package net.taskwolf.access.stripe;

import net.taskwolf.core.error.ErrorRepository;
import com.google.common.collect.Maps;
import com.stripe.StripeClient;
import com.stripe.model.Subscription;
import com.stripe.param.SubscriptionListParams;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.bundle.Bundle;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.stripe.StripeAccount;
import net.taskwolf.core.stripe.StripeConfiguration;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.stripe.TerminationDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripeTerminationController extends StripeController {
  private final StripeClient stripeClient;
  private final TerminationDatabaseTable terminationDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final ErrorRepository errorRepository;

  private StripeTerminationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable, StripeClient stripeClient,
    UserTargetDatabaseTable targetDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    TerminationDatabaseTable terminationDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable, ErrorRepository errorRepository
  ) {
    super(secretKey, userDatabaseTable, stripeConfiguration, stripeDatabaseTable,
      targetDatabaseTable, organizationDatabaseTable);
    this.stripeClient = stripeClient;
    this.terminationDatabaseTable = terminationDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.errorRepository = errorRepository;
  }

  @RequestMapping(path = "/termination/status/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findTerminationStatus(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performStripeOperation(findUserId(request), account ->
        bundleDatabaseTable.findBundle(account.targetId())
          .thenComposeAsync(bundle -> findTerminationStatus(account.targetId(),
            bundle, account.accountId()).thenAccept(futureResponse::complete)),
      () -> futureResponse.complete(Map.of("terminable", false,
        "terminated", false)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findTerminationStatus(
    UUID targetId, Bundle bundle, String stripeAccountId
  ) {
    if (bundle.bundleType().isTrial() || bundle.bundleRuntime().isUnbound()) {
      return CompletableFuture.completedFuture(Map.of("terminable", false,
        "terminated", false));
    }
    try {
      var subscriptions = stripeClient.subscriptions()
        .list(SubscriptionListParams.builder().setCustomer(stripeAccountId).build())
        .getData();
      if (subscriptions.isEmpty()) {
        return CompletableFuture.completedFuture(Map.of("terminable", true,
          "terminated", true));
      }
      return terminationDatabaseTable.terminationExists(targetId)
        .thenApply(exists -> findTerminationStatus(bundle, exists));
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
  }

  private Map<String, Object> findTerminationStatus(
    Bundle bundle, boolean terminationExists
  ) {
    if (!terminationExists) {
      return Map.of("terminable", true, "terminated", false, "directlyEffective",
        isDirectlyTerminable(bundle));
    }
    return Map.of("terminable", true, "terminated", true);
  }

  @RequestMapping(path = "/terminate/", method = RequestMethod.GET)
  public void terminate(
    HttpServletRequest request
  ) {
    performStripeOperation(findUserId(request), account ->
      bundleDatabaseTable.findBundle(account.targetId())
        .thenAcceptAsync(bundle -> terminate(account.targetId(), bundle, account)),
      () -> {});
  }

  public CompletableFuture<Void> terminate(UUID targetId) {
    return stripeDatabaseTable().stripeAccountExistsByTarget(targetId)
      .thenCompose(exists -> exists ?
        stripeDatabaseTable().findStripeAccountByTarget(targetId)
          .thenCompose(account -> bundleDatabaseTable.findBundle(account.targetId())
            .thenComposeAsync(bundle -> terminate(account.targetId(), bundle, account))) :
        CompletableFuture.completedFuture(null));
  }

  private CompletableFuture<Void> terminate(
    UUID targetId, Bundle bundle, StripeAccount account
  ) {
    if (bundle.bundleRuntime().isMonthly()) {
      return cancelSubscription(account);
    } else if (bundle.bundleRuntime().isYearly()) {
      return terminateYearly(targetId, bundle, account);
    }
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> terminateYearly(
    UUID targetId, Bundle bundle, StripeAccount account
  ) {
    if (isDirectlyTerminable(bundle)) {
      return cancelSubscription(account);
    }
    return terminationDatabaseTable.insertTermination(targetId);
  }

  public CompletableFuture<Void> cancelSubscription(Subscription subscription) {
    return stripeDatabaseTable().findStripeAccount(subscription.getCustomer())
      .thenComposeAsync(account -> cancelSubscription(subscription, account));
  }

  private CompletableFuture<Void> cancelSubscription(StripeAccount account) {
    try {
      var subscriptions = stripeClient.subscriptions()
        .list(SubscriptionListParams.builder().setCustomer(account.accountId()).build())
        .getData();
      if (subscriptions.isEmpty()) {
        return CompletableFuture.completedFuture(null);
      }
      return cancelSubscription(subscriptions.get(0), account);
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Void> cancelSubscription(
    Subscription subscription, StripeAccount account
  ) {
    try {
      subscription.cancel();
      return stripeDatabaseTable().updateStripeAccount(account.accountId(),
        account.targetId(), account.userId(), "");
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return CompletableFuture.completedFuture(null);
    }
  }

  private boolean isDirectlyTerminable(Bundle bundle) {
    var current = ZonedDateTime.ofInstant(Instant.ofEpochMilli(bundle.expiration()),
      ZoneId.systemDefault());
    var next = current.minusMonths(1);
    if (next.getDayOfMonth() != current.getDayOfMonth()) {
      next = next.withDayOfMonth(next.getMonth().length(
        next.toLocalDate().isLeapYear()));
    }
    return System.currentTimeMillis() > next.toInstant().toEpochMilli();
  }
}
