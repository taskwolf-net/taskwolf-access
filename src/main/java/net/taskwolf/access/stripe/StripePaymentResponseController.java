package net.taskwolf.access.stripe;

import net.taskwolf.core.error.ErrorRepository;
import net.taskwolf.workflow.operation.OperationDatabaseTable;
import net.taskwolf.workflow.throttle.WorkflowThrottleDatabaseTable;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
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
  private final OperationDatabaseTable operationDatabaseTable;
  private final WorkflowThrottleDatabaseTable workflowThrottleDatabaseTable;
  private final OfferDatabaseTable offerDatabaseTable;
  private final ErrorRepository errorRepository;

  private StripePaymentResponseController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable, StripeClient stripeClient,
    UserTargetDatabaseTable targetDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    TerminationDatabaseTable terminationDatabaseTable,
    StripeTerminationController stripeTerminationController,
    BundleDatabaseTable bundleDatabaseTable,
    OperationDatabaseTable operationDatabaseTable,
    WorkflowThrottleDatabaseTable workflowThrottleDatabaseTable,
    OfferDatabaseTable offerDatabaseTable,
    ErrorRepository errorRepository
  ) {
    super(secretKey, userDatabaseTable, stripeConfiguration, stripeDatabaseTable,
      targetDatabaseTable, organizationDatabaseTable);
    this.stripeClient = stripeClient;
    this.terminationDatabaseTable = terminationDatabaseTable;
    this.stripeTerminationController = stripeTerminationController;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.operationDatabaseTable = operationDatabaseTable;
    this.workflowThrottleDatabaseTable = workflowThrottleDatabaseTable;
    this.offerDatabaseTable = offerDatabaseTable;
    this.errorRepository = errorRepository;
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
    if (event.get().getType().equals("payment_intent.succeeded")) {
      new Thread(() -> processPaymentSuccess((PaymentIntent) object.get())).start();
    }
  }

  private void processPaymentCreation(PaymentIntent paymentIntent) {
    try {
      var invoice = stripeClient.invoices().retrieve(paymentIntent.getInvoice());
      var subscription = stripeClient.subscriptions().retrieve(
        invoice.getSubscription());
      findBundle(subscription).thenAccept(this::checkPackageExtension);
    } catch (Exception exception) {
      errorRepository.processError(exception);
    }
  }

  private void checkPackageExtension(Bundle bundle) {
    if (bundle == null) {
      return;
    }
    operationDatabaseTable.resetExpiration(bundle.ownerId());
    workflowThrottleDatabaseTable.setThrottle(bundle.ownerId(), 0, 0);
    var timeDifference = Math.abs(System.currentTimeMillis() - bundle.expiration());
    if (timeDifference > 1000L * 60 * 60 * 24) {
      return;
    }
    bundle.extend();
    bundleDatabaseTable.updateBundle(bundle);
  }

  private void processPaymentSuccess(PaymentIntent paymentIntent) {
    try {
      var invoice = stripeClient.invoices().retrieve(paymentIntent.getInvoice());
      var subscription = stripeClient.subscriptions().retrieve(
        invoice.getSubscription());
      findBundle(subscription).thenAccept(bundle ->
        processPaymentSuccess(subscription, bundle));
    } catch (Exception exception) {
      errorRepository.processError(exception);
    }
  }

  private void processPaymentSuccess(Subscription subscription, Bundle bundle) {
    if (bundle == null) {
      return;
    }
    terminationDatabaseTable.terminationExists(bundle.ownerId())
      .thenAccept(terminationExists -> checkPackageTermination(subscription,
        bundle, terminationExists));
  }

  private void checkPackageTermination(
    Subscription subscription, Bundle bundle, boolean terminationExists
  ) {
    if (!terminationExists) {
      return;
    }
    var periodEnd = subscription.getCurrentPeriodEnd();
    if (periodEnd == null) {
      return;
    }
    var timeDifference = Math.abs(periodEnd - bundle.expiration());
    if (timeDifference > 1000L * 60 * 60 * 24) {
      return;
    }
    terminationDatabaseTable.deleteTermination(bundle.ownerId());
    stripeTerminationController.cancelSubscription(subscription);
  }

  private CompletableFuture<Bundle> findBundle(Subscription subscription) {
    try {
      var customer = stripeClient.customers().retrieve(subscription.getCustomer());
      return userDatabaseTable().findUser(customer.getEmail())
        .thenCompose(user -> findBundleTarget(user, subscription)
          .thenCompose(this::findBundle));
    } catch (Exception exception) {
      errorRepository.processError(exception);
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Bundle> findBundle(Optional<UUID> targetId) {
    if (targetId.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    return bundleDatabaseTable.bundleExists(targetId.get())
      .thenCompose(bundleExists -> findBundle(targetId.get(),
        bundleExists));
  }

  private CompletableFuture<Bundle> findBundle(
    UUID targetId, boolean bundleExists
  ) {
    if (!bundleExists) {
      return CompletableFuture.completedFuture(null);
    }
    return bundleDatabaseTable.findBundle(targetId);
  }

  private CompletableFuture<Optional<UUID>> findBundleTarget(
    User user, Subscription subscription
  ) {
    var price = subscription.getItems().getData().get(0).getPrice().getId();
    if (!stripeConfiguration().priceIdExists(price)) {
      return offerDatabaseTable.findOffersByPriceId(price)
        .thenApply(offer -> Optional.of(offer.targetId()));
    }
    if (stripeConfiguration().findPriceIdsOfType(BundleType.INDIVIDUAL).contains(price)) {
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
