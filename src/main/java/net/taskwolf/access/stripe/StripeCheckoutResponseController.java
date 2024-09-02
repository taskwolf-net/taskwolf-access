package net.taskwolf.access.stripe;

import com.google.common.collect.Lists;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.PaymentIntentListParams;
import com.stripe.param.RefundCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.bundle.*;
import net.taskwolf.core.locale.Translation;
import net.taskwolf.core.mail.Mail;
import net.taskwolf.core.mail.MailAttachment;
import net.taskwolf.core.offer.Offer;
import net.taskwolf.core.offer.OfferDatabaseTable;
import net.taskwolf.core.offer.OfferStatus;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.stripe.StripeAccount;
import net.taskwolf.core.stripe.StripeConfiguration;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.stripe.TerminationDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.worker.WorkerDistribution;
import net.taskwolf.core.workflow.operation.OperationDatabaseTable;
import net.taskwolf.core.workflow.throttle.WorkflowThrottleDatabaseTable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.security.Key;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripeCheckoutResponseController extends StripeController {
  private final StripeClient stripeClient;
  private final TerminationDatabaseTable terminationDatabaseTable;
  private final Mail orderMail;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final OperationDatabaseTable operationDatabaseTable;
  private final WorkflowThrottleDatabaseTable workflowThrottleDatabaseTable;
  private final OfferDatabaseTable offerDatabaseTable;
  private final WorkerDistribution distribution;
  private final Translation translation;

  private StripeCheckoutResponseController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable, StripeClient stripeClient,
    UserTargetDatabaseTable targetDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    TerminationDatabaseTable terminationDatabaseTable,
    @Qualifier("orderMail") Mail orderMail,
    BundleDatabaseTable bundleDatabaseTable,
    OperationDatabaseTable operationDatabaseTable,
    WorkflowThrottleDatabaseTable workflowThrottleDatabaseTable,
    OfferDatabaseTable offerDatabaseTable, WorkerDistribution distribution,
    Translation translation
  ) {
    super(secretKey, userDatabaseTable, stripeConfiguration, stripeDatabaseTable,
      targetDatabaseTable, organizationDatabaseTable);
    this.stripeClient = stripeClient;
    this.terminationDatabaseTable = terminationDatabaseTable;
    this.orderMail = orderMail;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.operationDatabaseTable = operationDatabaseTable;
    this.workflowThrottleDatabaseTable = workflowThrottleDatabaseTable;
    this.offerDatabaseTable = offerDatabaseTable;
    this.distribution = distribution;
    this.translation = translation;
  }

  @RequestMapping(path = "/stripe/checkout/", method = RequestMethod.POST)
  public void processStripeRequest(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var event = findStripeEvent(request, payload,
      stripeConfiguration().checkoutWebhookSecret());
    if (event.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    var object = findStripeObject(event.get());
    if (object.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    if (event.get().getType().equals("checkout.session.completed")) {
      new Thread(() -> processCheckoutSessionCompletion(
        (Session) object.get())).start();
    }
  }

  private void processCheckoutSessionCompletion(Session session) {
    try {
      var customerEmail = stripeClient.customers()
        .retrieve(session.getCustomer()).getEmail();
      var subscription = stripeClient.subscriptions()
        .retrieve(session.getSubscription());
      userDatabaseTable().findUser(customerEmail).thenCompose(user ->
        findBundle(user, subscription).thenCompose(bundle ->
          processPreviousSubscriptions(session, user).thenAcceptAsync(value ->
            findStripeAccount(session, subscription, user, bundle.ownerId())
              .thenAccept(account -> applySubscription(subscription, user,
                bundle.ownerId(), bundle)))));
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private CompletableFuture<Bundle> findBundle(User user, Subscription subscription) {
    try {
      var price = subscription.getItems().getData().get(0).getPrice().getId();
      var bundlePreset = stripeConfiguration().findBundlePreset(price);
      if (bundlePreset.isEmpty()) {
        var futureOffer = offerDatabaseTable.findOffersByPriceId(price);
        futureOffer.thenAccept(offer ->
          offerDatabaseTable.updateOfferStatus(offer, OfferStatus.ACCEPTED));
        return futureOffer.thenApply(Offer::toBundle);
      }
      var bundleRuntime = findBundleRuntime(subscription);
      return findBundleTarget(user, bundlePreset.get().bundleType())
        .thenApply(target -> Bundle.of(target, bundlePreset.get(), bundleRuntime));
    } catch (Exception exception) {
      exception.printStackTrace();
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Void> processPreviousSubscriptions(
    Session session, User user
  ) {
    return stripeDatabaseTable().stripeAccountExists(session.getCustomer())
      .thenCompose(exists -> processPreviousSubscriptions(user,
        session.getCustomer(), exists));
  }

  private CompletableFuture<Void> processPreviousSubscriptions(
    User user, String accountId, boolean previouslyExisted
  ) {
    if (!previouslyExisted) {
      return CompletableFuture.completedFuture(null);
    }
    return stripeDatabaseTable().findStripeAccount(accountId)
      .thenAcceptAsync(account -> terminatePreviousSubscriptions(user,
        accountId, account.subscriptionId()));
  }

  private void terminatePreviousSubscriptions(
    User user, String accountId, String subscriptionId
  ) {
    try {
      var customer = stripeClient.customers().retrieve(accountId);
      if (subscriptionId.isEmpty()) {
        return;
      }
      var subscription = stripeClient.subscriptions().retrieve(subscriptionId);
      refundLastPayment(user, customer.getId());
      subscription.cancel();
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private void refundLastPayment(User user, String accountId) throws Exception  {
    var payments = stripeClient.paymentIntents()
      .list(PaymentIntentListParams.builder().setCustomer(accountId).build())
      .getData();
    payments.sort(Comparator.comparing(PaymentIntent::getCreated));
    if (payments.size() <= 1) {
      return;
    }
    var payment = payments.get(payments.size() - 2);
    var amount = calculatePaymentRefundAmount(payment);
    stripeClient.refunds().create(RefundCreateParams.builder()
      .setPaymentIntent(payment.getId())
      .setAmount(amount)
      .build());
    var title = translation.translate(user, "upgrade.email.title");
    var body = String.format(translation.translate(user, "upgrade.email.body"),
      user.name(), amount / 100D);
    orderMail.send(user.email(), title, body);
  }

  private long calculatePaymentRefundAmount(PaymentIntent payment) {
    var monthMillis = 1000L * 60 * 60 * 24 * 30;
    var percentageLeft = (monthMillis - (System.currentTimeMillis() -
      (payment.getCreated() * 1000))) / (double) monthMillis;
    return Math.round(percentageLeft * payment.getAmount());
  }

  private CompletableFuture<StripeAccount> findStripeAccount(
    Session session, Subscription subscription, User user, UUID targetId
  ) {
    var accountId = session.getCustomer();
    return stripeDatabaseTable().stripeAccountExists(accountId).thenCompose(
      exists -> storeCustomer(accountId, subscription, user, targetId, exists)
        .thenCompose(customer -> stripeDatabaseTable().findStripeAccount(accountId)));
  }

  private CompletableFuture<Void> storeCustomer(
    String accountId, Subscription subscription, User user, UUID targetId,
    boolean exists
  ) {
    if (exists) {
      return stripeDatabaseTable().updateStripeAccount(accountId, targetId,
        user.id(), subscription.getId());
    }
    return stripeDatabaseTable().insertStripeAccount(accountId, targetId,
      user.id(), subscription.getId());
  }

  private void applySubscription(
    Subscription subscription, User user, UUID targetId, Bundle bundle
  ) {
    bundleDatabaseTable.bundleExists(targetId).thenAccept(exists ->
      applyBundle(targetId, bundle, exists));
    terminationDatabaseTable.deleteTermination(targetId);
    targetDatabaseTable().changeTarget(user.id(), targetId);
    try {
      sendPaymentEmail(user, subscription);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private void applyBundle(UUID target, Bundle bundle, boolean bundleExists) {
    operationDatabaseTable.operationsExists(target)
      .thenCompose(exists -> !exists ?
        operationDatabaseTable.insertOperations(target) :
        operationDatabaseTable.resetExpiration(target));
    workflowThrottleDatabaseTable.throttleExists(target)
      .thenCompose(exists -> !exists ?
        workflowThrottleDatabaseTable.insertThrottle(target) :
        workflowThrottleDatabaseTable.setThrottle(target, 0, 0));
    if (bundleExists) {
      bundleDatabaseTable.updateBundle(bundle);
      return;
    }
    bundleDatabaseTable.insertBundle(bundle);
  }

  private BundleRuntime findBundleRuntime(Subscription subscription) {
    var price = subscription.getItems().getData().get(0).getPrice().getId();
    if (stripeConfiguration().findPriceIdsOfRuntime(BundleRuntime.MONTHLY).contains(price)) {
      return BundleRuntime.MONTHLY;
    }
    return BundleRuntime.YEARLY;
  }

  private void sendPaymentEmail(
    User user, Subscription subscription
  ) throws Exception {
    var invoice = stripeClient.invoices().retrieve(subscription.getLatestInvoice());
    var invoiceFile = new File(System.getProperty("user.dir") + "/invoices/" +
      invoice.getId() + ".pdf");
    invoiceFile.getParentFile().mkdirs();
    invoiceFile.createNewFile();
    downloadInvoice(invoice.getInvoicePdf(), invoiceFile.getAbsoluteFile());
    var title = translation.translate(user, "payment.email.title");
    var body = String.format(translation.translate(user, "upgrade.email.body"),
      user.name());
    orderMail.send(user.email(), title, body,
        Lists.newArrayList(MailAttachment.create("Invoice.pdf", invoiceFile)))
      .thenAccept(value -> invoiceFile.delete());
  }

  private void downloadInvoice(String url, File file) throws Exception {
    var inputStream = new BufferedInputStream(new URL(url).openStream());
    var fileOutputStream = new FileOutputStream(file);
    byte[] dataBuffer = new byte[1024];
    int bytesRead;
    while ((bytesRead = inputStream.read(dataBuffer, 0, 1024)) != -1) {
      fileOutputStream.write(dataBuffer, 0, bytesRead);
    }
  }

  private CompletableFuture<UUID> findBundleTarget(
    User user, BundleType bundleType
  ) {
    if (bundleType == BundleType.INDIVIDUAL) {
      return CompletableFuture.completedFuture(user.id());
    }
    return organizationDatabaseTable().organizationExistsByOwner(user.id())
      .thenCompose(exists -> findOrganization(user, exists));
  }

  private CompletableFuture<UUID> findOrganization(
    User user, boolean organizationExists
  ) {
    if (organizationExists) {
      return organizationDatabaseTable().findOrganizationByOwner(user.id())
        .thenApply(Organization::id);
    }
    var organizationIdFuture = organizationDatabaseTable()
      .generateAvailableOrganizationId();
    var organizationName = String.format(translation.translate(user,
      "organization.default.name"), user.name());
    organizationIdFuture.thenAccept(organizationId ->
      createOrganization(organizationId, organizationName, user.id()));
    return organizationIdFuture;
  }

  private void createOrganization(UUID organizationId, String name, UUID userId) {
    organizationDatabaseTable().insertOrganization(organizationId, name, userId,
      Lists.newArrayList(), UUID.randomUUID().toString());
    userDatabaseTable().addUserOrganization(userId, organizationId);
    distribution.addUser(organizationId);
  }
}

