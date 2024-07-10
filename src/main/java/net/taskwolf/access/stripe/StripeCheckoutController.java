package net.taskwolf.access.stripe;

import com.stripe.StripeClient;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.bundle.*;
import net.taskwolf.core.stripe.StripeAccount;
import net.taskwolf.core.stripe.StripeConfiguration;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripeCheckoutController extends TaskwolfRestController {
  private final StripeDatabaseTable stripeDatabaseTable;
  private final StripeConfiguration stripeConfiguration;
  private final StripeClient stripeClient;

  private StripeCheckoutController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeDatabaseTable stripeDatabaseTable,
    StripeConfiguration stripeConfiguration, StripeClient stripeClient
  ) {
    super(secretKey, userDatabaseTable);
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.stripeConfiguration = stripeConfiguration;
    this.stripeClient = stripeClient;
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
    if (!userExists || bundleType.isTrial() || bundleType.isEnterprise()) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return userDatabaseTable().findUser(email)
      .thenCompose(user -> stripeDatabaseTable.stripeAccountExistsByUser(user.id())
        .thenCompose(exists -> exists ?
          stripeDatabaseTable.findStripeAccountByUser(user.id())
            .thenApply(StripeAccount::accountId) :
          CompletableFuture.completedFuture(""))
        .thenApplyAsync(accountId -> checkout(email, bundleType, bundleClass,
          bundleRuntime, accountId)));
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
}
