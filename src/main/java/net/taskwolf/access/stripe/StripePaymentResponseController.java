package net.taskwolf.access.stripe;

import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import com.stripe.param.SubscriptionListParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.bundle.Bundle;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.bundle.BundleType;
import net.taskwolf.core.offer.OfferDatabaseTable;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.stripe.StripeConfiguration;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.stripe.TerminationDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripePaymentResponseController extends StripeController {
  private final StripeClient stripeClient;
  private final TerminationDatabaseTable terminationDatabaseTable;
  private final StripeTerminationController stripeTerminationController;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final OfferDatabaseTable offerDatabaseTable;

  private StripePaymentResponseController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable, StripeClient stripeClient,
    UserTargetDatabaseTable targetDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    TerminationDatabaseTable terminationDatabaseTable,
    StripeTerminationController stripeTerminationController,
    BundleDatabaseTable bundleDatabaseTable, OfferDatabaseTable offerDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, stripeConfiguration, stripeDatabaseTable,
      targetDatabaseTable, organizationDatabaseTable);
    this.stripeClient = stripeClient;
    this.terminationDatabaseTable = terminationDatabaseTable;
    this.stripeTerminationController = stripeTerminationController;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.offerDatabaseTable = offerDatabaseTable;
  }

  @RequestMapping(path = "/stripe/payment/", method = RequestMethod.POST)
  public void processStripeRequest(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var event = findStripeEvent(request, payload,
      stripeConfiguration().paymentWebhookSecret());
    if (event.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    var object = findStripeObject(event.get());
    if (object.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    if (event.get().getType().equals("payment_intent.created")) {
      new Thread(() -> processPaymentCreation((PaymentIntent) object.get())).start();
    }
  }

  private void processPaymentCreation(PaymentIntent paymentIntent) {
    try {
      var subscription = stripeClient.subscriptions()
        .list(SubscriptionListParams.builder()
          .setCustomer(paymentIntent.getCustomer()).build())
        .getData().get(0);
      var customer = stripeClient.customers().retrieve(paymentIntent.getCustomer());
      userDatabaseTable().findUser(customer.getEmail())
        .thenAccept(user -> findBundleTarget(user, subscription)
          .thenAccept(target -> processPaymentCreation(paymentIntent, target)));
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private void processPaymentCreation(
    PaymentIntent paymentIntent, Optional<UUID> targetId
  ) {
    if (targetId.isEmpty()) {
      return;
    }
    bundleDatabaseTable.bundleExists(targetId.get()).thenAccept(bundleExists ->
      processPaymentCreation(paymentIntent, targetId.get(), bundleExists));
  }

  private void processPaymentCreation(
    PaymentIntent paymentIntent, UUID targetId, boolean bundleExists
  ) {
    if (!bundleExists) {
      return;
    }
    bundleDatabaseTable.findBundle(targetId).thenAccept(bundle ->
      processPaymentCreation(paymentIntent, targetId, bundle));
  }

  private void processPaymentCreation(
    PaymentIntent paymentIntent, UUID targetId, Bundle bundle
  ) {
    checkPackageExtension(bundle);
    terminationDatabaseTable.terminationExists(targetId)
      .thenAccept(terminationExists -> checkPackageTermination(paymentIntent,
        targetId, bundle, terminationExists));
  }

  private void checkPackageExtension(Bundle bundle) {
    var timeDifference = Math.abs(System.currentTimeMillis() - bundle.expiration());
    if (timeDifference > 1000L * 60 * 60 * 24) {
      return;
    }
    bundle.extend();
    bundleDatabaseTable.updateBundle(bundle);
  }

  private void checkPackageTermination(
    PaymentIntent paymentIntent, UUID targetId, Bundle bundle,
    boolean terminationExists
  ) {
    if (!terminationExists) {
      return;
    }
    var timeDifference = Math.abs((System.currentTimeMillis() +
      1000L * 60 * 60 * 24 * 30) - bundle.expiration());
    if (timeDifference > 1000L * 60 * 60 * 24) {
      return;
    }
    terminationDatabaseTable.deleteTermination(targetId);
    stripeTerminationController.cancelSubscription(paymentIntent.getCustomer());
  }

  private CompletableFuture<Optional<UUID>> findBundleTarget(
    User user, Subscription subscription
  ) {
    var price = subscription.getItems().getData().get(0).getPrice().getId();
    if (!stripeConfiguration().priceIdExists(price)) {
      return offerDatabaseTable.findOffersByPriceId(price)
        .thenApply(offer -> Optional.of(offer.targetId()));
    }
    if (stripeConfiguration().findPriceIdsOfType(BundleType.PROFESSIONAL).contains(price)) {
      return CompletableFuture.completedFuture(Optional.of(user.id()));
    }
    return organizationDatabaseTable().organizationExistsByOwner(user.id())
      .thenCompose(exists -> findOrganization(user, exists));
  }

  private CompletableFuture<Optional<UUID>> findOrganization(
    User user, boolean organizationExists
  ) {
    if (organizationExists) {
      return organizationDatabaseTable().findOrganizationByOwner(user.id())
        .thenApply(organization -> Optional.of(organization.id()));
    }
    return CompletableFuture.completedFuture(Optional.empty());
  }
}
