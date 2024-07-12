package net.taskwolf.access.stripe;

import com.stripe.StripeClient;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.bundle.BundleClass;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.bundle.BundleRuntime;
import net.taskwolf.core.bundle.BundleType;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.stripe.StripeAccount;
import net.taskwolf.core.stripe.StripeConfiguration;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripeCheckoutRequestController extends TaskwolfRestController {
  private final StripeDatabaseTable stripeDatabaseTable;
  private final StripeConfiguration stripeConfiguration;
  private final StripeClient stripeClient;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;

  private StripeCheckoutRequestController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeDatabaseTable stripeDatabaseTable,
    StripeConfiguration stripeConfiguration, StripeClient stripeClient,
    BundleDatabaseTable bundleDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.stripeConfiguration = stripeConfiguration;
    this.stripeClient = stripeClient;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
  }

  @RequestMapping(path = "/checkout/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> checkout(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var email = body.getString("email");
    var bundleType = BundleType.valueOf(body.getString("bundleType"));
    var bundleClass = BundleClass.valueOf(body.getString("bundleClass"));
    var bundleRuntime = BundleRuntime.valueOf(body.getString("bundleRuntime"));
    return userDatabaseTable().userExists(email).thenCompose(exists ->
      checkout(email, bundleType, bundleClass, bundleRuntime, exists));
  }

  private CompletableFuture<Map<String, Object>> checkout(
    String email, BundleType bundleType, BundleClass bundleClass,
    BundleRuntime bundleRuntime, boolean userExists
  ) {
    if (!userExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    if (bundleType.isTrial() || bundleType.isEnterprise()) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    return userDatabaseTable().findUser(email)
      .thenCompose(user -> checkBundleUsability(user, bundleType, bundleClass)
        .thenCompose(bundleUsability -> checkout(email, bundleType,
          bundleClass, bundleRuntime, user, bundleUsability)));
  }

  private CompletableFuture<Map<String, Object>> checkout(
    String email, BundleType bundleType, BundleClass bundleClass,
    BundleRuntime bundleRuntime, User user, boolean bundleUsability
  ) {
    if (!bundleUsability) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    return findExistingAccount(user, bundleType).thenApplyAsync(accountId ->
      checkout(email, bundleType, bundleClass, bundleRuntime, accountId));
  }

  private CompletableFuture<String> findExistingAccount(
    User user, BundleType bundleType
  ) {
    if (bundleType.isProfessional()) {
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

  private Map<String, Object> checkout(
    String email, BundleType bundleType, BundleClass bundleClass,
    BundleRuntime bundleRuntime, String accountId
  ) {
    try {
      var sessionBuilder = SessionCreateParams.builder()
        .addLineItem(SessionCreateParams.LineItem.builder()
          .setPrice(stripeConfiguration.findPriceId(bundleType,
            bundleClass, bundleRuntime))
          .setQuantity(1L)
          .build())
        .setSuccessUrl("https://taskwolf.net/payment/complete/")
        .setCancelUrl("https://taskwolf.net/pricing/")
        .setMode(SessionCreateParams.Mode.SUBSCRIPTION);
      if (!accountId.isEmpty()) {
        sessionBuilder.setCustomer(accountId);
      } else {
        sessionBuilder.setCustomerEmail(email);
      }
      var checkout = stripeClient.checkout().sessions()
        .create(sessionBuilder.build());
      return Map.of("success", true, "link", checkout.getUrl());
    } catch (Exception exception) {
      exception.printStackTrace();
      return Map.of("success", false);
    }
  }

  private CompletableFuture<Boolean> checkBundleUsability(
    User user, BundleType bundleType, BundleClass bundleClass
  ) {
    if (bundleType.isProfessional()) {
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
    return bundleDatabaseTable.findBundle(user.id()).thenApply(bundle ->
      bundle.bundleClass().weight() < bundleClass.weight());
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
        .thenApply(bundle -> bundle.bundleClass().weight() < bundleClass.weight()));
  }
}
