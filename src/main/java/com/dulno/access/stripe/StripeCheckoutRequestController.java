package com.dulno.access.stripe;

import com.dulno.core.bundle.*;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.stripe.StripeCompletionDatabaseTable;
import com.stripe.StripeClient;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoHomeRestController;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.organization.OrganizationDatabaseTable;
import com.dulno.core.stripe.StripeAccount;
import com.dulno.core.stripe.StripeConfiguration;
import com.dulno.core.stripe.StripeDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripeCheckoutRequestController extends DulnoHomeRestController {
  private final StripeDatabaseTable stripeDatabaseTable;
  private final StripeCompletionDatabaseTable stripeCompletionDatabaseTable;
  private final StripeConfiguration stripeConfiguration;
  private final StripeClient stripeClient;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final ErrorRepository errorRepository;

  private StripeCheckoutRequestController(
    @Qualifier("homeKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeDatabaseTable stripeDatabaseTable,
    StripeCompletionDatabaseTable stripeCompletionDatabaseTable,
    StripeConfiguration stripeConfiguration, StripeClient stripeClient,
    BundleDatabaseTable bundleDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    ErrorRepository errorRepository
  ) {
    super(secretKey, userDatabaseTable);
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.stripeCompletionDatabaseTable = stripeCompletionDatabaseTable;
    this.stripeConfiguration = stripeConfiguration;
    this.stripeClient = stripeClient;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.errorRepository = errorRepository;
  }

  @RequestMapping(path = "/checkout/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> checkout(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var bundleType = BundleType.valueOf(body.getString("bundleType"));
    var bundleClass = BundleClass.valueOf(body.getString("bundleClass"));
    var bundleRuntime = BundleRuntime.valueOf(body.getString("bundleRuntime"));
    return findUser(request).thenCompose(user -> checkout(user, bundleType,
      bundleClass, bundleRuntime));
  }

  private CompletableFuture<Map<String, Object>> checkout(
    User user, BundleType bundleType, BundleClass bundleClass,
    BundleRuntime bundleRuntime
  ) {
    if (bundleType.isTrial() || bundleType.isEnterprise()) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return checkBundleUsability(user, bundleType, bundleClass)
      .thenCompose(bundleUsability -> checkout(user, bundleType,
        bundleClass, bundleRuntime, bundleUsability));
  }

  private CompletableFuture<Map<String, Object>> checkout(
    User user, BundleType bundleType, BundleClass bundleClass,
    BundleRuntime bundleRuntime, boolean bundleUsability
  ) {
    if (!bundleUsability) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return findExistingAccount(user, bundleType).thenApplyAsync(accountId ->
      checkout(user, bundleType, bundleClass, bundleRuntime, accountId));
  }

  private CompletableFuture<String> findExistingAccount(
    User user, BundleType bundleType
  ) {
    if (bundleType.isIndividual()) {
      return findAccountIfExists(user.id());
    }
    return organizationDatabaseTable.organizationExistsByOwner(user.id())
      .thenCompose(exists -> exists ?
        organizationDatabaseTable.findOrganizationByOwner(user.id())
          .thenCompose(organization -> findAccountIfExists(organization.id())) :
        CompletableFuture.completedFuture(""));
  }

  private CompletableFuture<String> findAccountIfExists(UUID target) {
    return stripeDatabaseTable.stripeAccountExistsByTarget(target)
      .thenCompose(exists -> exists ?
        stripeDatabaseTable.findStripeAccountByTarget(target)
          .thenApply(StripeAccount::accountId) :
        CompletableFuture.completedFuture(""));
  }

  private static final String SUCCESS_URL_FORMAT =
    "https://dulno.com/payment/complete/%s/";

  private Map<String, Object> checkout(
    User user, BundleType bundleType, BundleClass bundleClass,
    BundleRuntime bundleRuntime, String accountId
  ) {
    try {
      var token = UUID.randomUUID().toString();
      stripeCompletionDatabaseTable.insertStripeCompletion(user.id(), token);
      var sessionBuilder = SessionCreateParams.builder()
        .addLineItem(SessionCreateParams.LineItem.builder()
          .setPrice(stripeConfiguration.findPriceId(bundleType,
            bundleClass, bundleRuntime))
          .setQuantity(1L)
          .build())
        .setSuccessUrl(String.format(SUCCESS_URL_FORMAT, token))
        .setCancelUrl("https://dulno.com/pricing/")
        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
        .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED);
      if (!accountId.isEmpty()) {
        sessionBuilder.setCustomer(accountId);
      } else {
        sessionBuilder.setCustomerEmail(user.email());
      }
      var checkout = stripeClient.checkout().sessions()
        .create(sessionBuilder.build());
      return Map.of("success", true, "link", checkout.getUrl());
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return Map.of("success", false);
    }
  }

  private CompletableFuture<Boolean> checkBundleUsability(
    User user, BundleType bundleType, BundleClass bundleClass
  ) {
    if (bundleType.isIndividual()) {
      return checkPersonalBundleUsability(user, bundleClass);
    }
    if (bundleType.isTeam()) {
      return checkTeamBundleUsability(user, bundleClass);
    }
    return CompletableFuture.completedFuture(false);
  }

  private CompletableFuture<Boolean> checkPersonalBundleUsability(
    User user, BundleClass bundleClass
  ) {
    return bundleDatabaseTable.bundleExists(user.id()).thenCompose(exists ->
      checkPersonalBundleUsability(user, bundleClass, exists));
  }

  private CompletableFuture<Boolean> checkPersonalBundleUsability(
    User user, BundleClass bundleClass,
    boolean bundleExists
  ) {
    if (!bundleExists) {
      return CompletableFuture.completedFuture(true);
    }
    return bundleDatabaseTable.findBundle(user.id())
      .thenApply(bundle -> checkBundleUsability(bundle, bundleClass));
  }

  private CompletableFuture<Boolean> checkTeamBundleUsability(
    User user, BundleClass bundleClass
  ) {
    return organizationDatabaseTable.organizationExistsByOwner(user.id())
      .thenCompose(exists -> checkTeamBundleUsability(user, bundleClass, exists));
  }

  private CompletableFuture<Boolean> checkTeamBundleUsability(
    User user, BundleClass bundleClass,
    boolean hasOrganization
  ) {
    if (!hasOrganization) {
      return CompletableFuture.completedFuture(true);
    }
    return organizationDatabaseTable.findOrganizationByOwner(user.id())
      .thenCompose(organization -> bundleDatabaseTable.findBundle(organization.id())
        .thenApply(bundle -> checkBundleUsability(bundle, bundleClass)));
  }

  private boolean checkBundleUsability(Bundle bundle, BundleClass bundleClass) {
    return bundle.bundleClass().weight() < bundleClass.weight() ||
      (bundle.bundleClass().weight() == bundleClass.weight() &&
        System.currentTimeMillis() > bundle.expiration());
  }
}
