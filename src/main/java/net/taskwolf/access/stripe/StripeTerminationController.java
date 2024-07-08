package net.taskwolf.access.stripe;

import com.google.common.collect.Maps;
import com.stripe.StripeClient;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.bundle.Bundle;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.stripe.TerminationDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripeTerminationController extends TaskwolfRestController {
  private final StripeDatabaseTable stripeDatabaseTable;
  private final StripeClient stripeClient;
  private final TerminationDatabaseTable terminationDatabaseTable;
  private final UserTargetDatabaseTable targetDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;

  private StripeTerminationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeDatabaseTable stripeDatabaseTable, StripeClient stripeClient,
    TerminationDatabaseTable terminationDatabaseTable,
    UserTargetDatabaseTable targetDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.stripeClient = stripeClient;
    this.terminationDatabaseTable = terminationDatabaseTable;
    this.targetDatabaseTable = targetDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
  }

  @RequestMapping(path = "/termination/status/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findTerminationStatus(
    HttpServletRequest request
  ) {
    return targetDatabaseTable.findTarget(findUserId(request))
      .thenCompose(target -> bundleDatabaseTable.findBundle(target)
        .thenCompose(bundle -> findTerminationStatus(target, bundle)));
  }

  private CompletableFuture<Map<String, Object>> findTerminationStatus(
    UUID target, Bundle bundle
  ) {
    if (bundle.bundleType().isTrial()) {
      return CompletableFuture.completedFuture(Map.of("terminable", false,
        "terminated", false));
    }
    return stripeDatabaseTable.findStripeAccount(target).thenComposeAsync(
      account -> findTerminationStatus(target, bundle, account.accountId()));
  }

  private CompletableFuture<Map<String, Object>> findTerminationStatus(
    UUID target, Bundle bundle, String stripeAccountId
  ) {
    try {
      var subscriptions = stripeClient.customers().retrieve(stripeAccountId)
        .getSubscriptions().getData();
      if (subscriptions.isEmpty()) {
        return CompletableFuture.completedFuture(Map.of("terminable", true,
          "terminated", true));
      }
      return terminationDatabaseTable.terminationExists(target)
        .thenApply(exists -> findTerminationStatus(bundle, exists));
    } catch (Exception exception) {
      exception.printStackTrace();
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
  }

  private Map<String, Object> findTerminationStatus(
    Bundle bundle, boolean terminationExists
  ) {
    if (!terminationExists) {
      return Map.of("terminable", true, "terminated", false);
    }
    return Map.of("terminable", true, "terminated", false, "directlyEffective",
      bundle.expiration() - System.currentTimeMillis() < 1000L * 60 * 60 * 24 * 30);
  }

  @RequestMapping(path = "/terminate/", method = RequestMethod.GET)
  public void terminate(
    HttpServletRequest request
  ) {
    targetDatabaseTable.findTarget(findUserId(request))
      .thenAccept(target -> bundleDatabaseTable.findBundle(target)
        .thenAccept(bundle -> terminate(target, bundle)));
  }

  private void terminate(
    UUID target, Bundle bundle
  ) {
    if (bundle.bundleRuntime().isWeekly()) {
      return;
    }
    stripeDatabaseTable.findStripeAccount(target).thenAcceptAsync(account ->
      terminate(target, bundle, account.accountId()));
  }

  private void terminate(
    UUID target, Bundle bundle, String stripeAccountId
  ) {
    if (bundle.bundleRuntime().isMonthly()) {
      cancelSubscription(stripeAccountId);
    } else if (bundle.bundleRuntime().isYearly()) {
      terminateYearly(target, bundle, stripeAccountId);
    }
  }

  private void terminateYearly(UUID target, Bundle bundle, String stripeAccountId) {
    if (bundle.expiration() - System.currentTimeMillis() < 1000L * 60 * 60 * 24 * 30) {
      cancelSubscription(stripeAccountId);
      return;
    }
    terminationDatabaseTable.insertTermination(target);
  }

  private void cancelSubscription(String stripeAccountId) {
    try {
      var subscriptions = stripeClient.customers().retrieve(stripeAccountId)
        .getSubscriptions().getData();
      if (subscriptions.isEmpty()) {
        return;
      }
      subscriptions.get(0).cancel();
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }
}
