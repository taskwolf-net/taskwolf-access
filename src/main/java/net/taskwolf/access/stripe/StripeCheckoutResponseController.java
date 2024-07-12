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
import net.taskwolf.core.mail.TaskwolfMail;
import net.taskwolf.core.mail.TaskwolfMailAttachment;
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
  private final TaskwolfMail orderMail;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final OperationDatabaseTable operationDatabaseTable;
  private final WorkerDistribution distribution;
  private final CoreModule coreModule;

  private StripeCheckoutResponseController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable, StripeClient stripeClient,
    UserTargetDatabaseTable targetDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    TerminationDatabaseTable terminationDatabaseTable,
    @Qualifier("orderMail") TaskwolfMail orderMail,
    BundleDatabaseTable bundleDatabaseTable,
    OperationDatabaseTable operationDatabaseTable,
    WorkerDistribution distribution, CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable, stripeConfiguration, stripeDatabaseTable,
      targetDatabaseTable, organizationDatabaseTable);
    this.stripeClient = stripeClient;
    this.terminationDatabaseTable = terminationDatabaseTable;
    this.orderMail = orderMail;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.operationDatabaseTable = operationDatabaseTable;
    this.distribution = distribution;
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/stripe/checkout/", method = RequestMethod.POST)
  public void processStripeRequest(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var event = findStripeEvent(request, payload);
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
      var price = subscription.getItems().getData().get(0).getPrice().getId();
      var bundlePreset = stripeConfiguration().findBundlePreset(price);
      var bundleRuntime = findBundleRuntime(subscription);
      userDatabaseTable().findUser(customerEmail).thenCompose(user ->
        findBundleTarget(user, bundlePreset).thenAccept(target ->
          processPreviousSubscriptions(session, user).thenAcceptAsync(value ->
            findStripeAccount(session, subscription, user, target)
              .thenAccept(account -> applySubscription(subscription, user,
                target, bundlePreset, bundleRuntime)))));
    } catch (Exception exception) {
      exception.printStackTrace();
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

  private static final String UPGRADE_EMAIL_TITLE = "Upgrade";
  private static final String UPGRADE_EMAIL_BODY = "Hey %s,\n" +
    "\n" +
    "We have noticed that you have upgraded your Taskwolf package.\n" +
    "\n" +
    "As a result of the upgrade, we have canceled your old subscription and " +
    "activated the subscription for the new package.\n" +
    "\n" +
    "In the course of canceling your old package (by upgrading the package) " +
    "we have refunded you the last payment for this month in the amount of %s €.\n" +
    "\n" +
    "It may take a few days for the refund to reach you. If you have any " +
    "problems with this, you can contact support at support@taskwolf.net " +
    "at any time.\n" +
    "\n" +
    "We hope you enjoy your new package and thank you for your purchase.";

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
    orderMail.send(user.email(), UPGRADE_EMAIL_TITLE,
      String.format(UPGRADE_EMAIL_BODY, user.name(), amount / 100D));
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
    Subscription subscription, User user, UUID targetId,
    BundlePreset bundlePreset, BundleRuntime bundleRuntime
  ) {
    bundleDatabaseTable.bundleExists(targetId).thenAccept(exists ->
      applyBundle(targetId, bundlePreset, bundleRuntime, exists));
    terminationDatabaseTable.deleteTermination(targetId);
    targetDatabaseTable().changeTarget(user.id(), targetId);
    try {
      sendPaymentEmail(user, subscription);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private void applyBundle(
    UUID target, BundlePreset bundlePreset, BundleRuntime bundleRuntime,
    boolean bundleExists
  ) {
    operationDatabaseTable.insertOperations(target);
    if (bundleExists) {
      bundleDatabaseTable.updateBundle(Bundle.of(target, bundlePreset, bundleRuntime));
      return;
    }
    bundleDatabaseTable.insertBundle(Bundle.of(target, bundlePreset, bundleRuntime));
  }

  private BundleRuntime findBundleRuntime(Subscription subscription) {
    var price = subscription.getItems().getData().get(0).getPrice().getId();
    if (stripeConfiguration().findPriceIdsOfRuntime(BundleRuntime.MONTHLY).contains(price)) {
      return BundleRuntime.MONTHLY;
    }
    return BundleRuntime.YEARLY;
  }

  private static final String PAYMENT_EMAIL_TITLE = "Payment";
  private static final String PAYMENT_EMAIL_BODY = "Hey %s,\n" +
    "\n" +
    "Thank you for your order from Taskwolf. " +
    "We are delighted that you have chosen a product from Taskwolf.\n" +
    "\n" +
    "Your Taskwolf product is available to you immediately. " +
    "Log in now with the login details you entered when you registered.\n" +
    "\n" +
    "You can register under the following link:\n" +
    "https://taskwolf.net/login/\n" +
    "\n" +
    "We look forward to working with you!";

  private void sendPaymentEmail(
    User user, Subscription subscription
  ) throws Exception {
    var invoice = stripeClient.invoices().retrieve(subscription.getLatestInvoice());
    var invoiceFile = new File(System.getProperty("user.dir") + "/invoices/" +
      invoice.getId() + ".pdf");
    invoiceFile.getParentFile().mkdirs();
    invoiceFile.createNewFile();
    downloadInvoice(invoice.getInvoicePdf(), invoiceFile.getAbsoluteFile());
    orderMail.send(user.email(), PAYMENT_EMAIL_TITLE,
        String.format(PAYMENT_EMAIL_BODY, user.name()),
        Lists.newArrayList(TaskwolfMailAttachment.create("Invoice.pdf", invoiceFile)))
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
    User user, BundlePreset preset
  ) {
    if (preset.bundleType() == BundleType.PROFESSIONAL) {
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
    var organizationName = String.format(coreModule.translate(user,
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

