package net.taskwolf.access.stripe;

import com.google.common.collect.Lists;
import com.stripe.StripeClient;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import com.stripe.param.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripeSubscriptionController extends TaskwolfRestController {
  private final StripeConfiguration stripeConfiguration;
  private final StripeDatabaseTable stripeDatabaseTable;
  private final StripeClient stripeClient;
  private final TerminationDatabaseTable terminationDatabaseTable;
  private final StripeTerminationController stripeTerminationController;
  private final TaskwolfMail orderMail;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final OperationDatabaseTable operationDatabaseTable;
  private final WorkerDistribution distribution;
  private final CoreModule coreModule;

  private StripeSubscriptionController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable, StripeClient stripeClient,
    TerminationDatabaseTable terminationDatabaseTable,
    StripeTerminationController stripeTerminationController,
    @Qualifier("orderMail") TaskwolfMail orderMail,
    OrganizationDatabaseTable organizationDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable,
    OperationDatabaseTable operationDatabaseTable, WorkerDistribution distribution,
    CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.stripeConfiguration = stripeConfiguration;
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.stripeClient = stripeClient;
    this.terminationDatabaseTable = terminationDatabaseTable;
    this.stripeTerminationController = stripeTerminationController;
    this.orderMail = orderMail;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.operationDatabaseTable = operationDatabaseTable;
    this.distribution = distribution;
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/stripe/", method = RequestMethod.POST)
  public void processStripeRequest(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var event = findStripeEvent(request, payload);
    if (event.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    var dataObjectDeserializer = event.get().getDataObjectDeserializer();
    if (dataObjectDeserializer.getObject().isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    var stripeObject = dataObjectDeserializer.getObject().get();
    if (event.get().getType().equals("customer.subscription.created")) {
      new Thread(() -> processSubscriptionCreation(event.get(),
        (Subscription) stripeObject)).start();
    } else if (event.get().getType().equals("payment_intent.created")) {
      new Thread(() -> processPaymentCreation(event.get(),
        (PaymentIntent) stripeObject)).start();
    }
  }

  private Optional<Event> findStripeEvent(
    HttpServletRequest request, String payload
  ) {
    var signature = request.getHeader("Stripe-Signature");
    try {
      return Optional.of(Webhook.constructEvent(payload, signature,
        stripeConfiguration.webhookSecret()));
    } catch (Exception exception) {
      return Optional.empty();
    }
  }

  private void processSubscriptionCreation(Event event, Subscription subscription) {
    try {
      processPreviousSubscriptions(subscription).thenAccept(value ->
        findStripeAccount(subscription).thenAccept(account ->
          userDatabaseTable().findUser(account.userId()).thenAccept(user ->
            applySubscription(user, subscription))));
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private CompletableFuture<Void> processPreviousSubscriptions(
    Subscription subscription
  ) throws Exception {
    var customerEmail = stripeClient.customers()
      .retrieve(subscription.getCustomer()).getEmail();
    return userDatabaseTable().findUser(customerEmail).thenCompose(user ->
      stripeDatabaseTable.stripeAccountExistsByUser(user.id()).thenCompose(exists ->
        processPreviousSubscriptions(user, exists)));
  }

  private CompletableFuture<Void> processPreviousSubscriptions(
    User user, boolean previouslyExisted
  ) {
    if (!previouslyExisted) {
      return CompletableFuture.completedFuture(null);
    }
    return stripeDatabaseTable.findStripeAccountByUser(user.id())
      .thenAcceptAsync(account -> terminatePreviousSubscriptions(user,
        account.accountId(), account.subscriptionId()));
  }

  private void terminatePreviousSubscriptions(
    User user, String accountId, String subscriptionId
  ) {
    try {
      var customer = stripeClient.customers().retrieve(accountId);
      if (!subscriptionId.isEmpty()) {
        var subscription = stripeClient.subscriptions().retrieve(subscriptionId);
        refundLastPayment(user, customer.getId());
        subscription.cancel();
      }
      customer.delete();
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
    if (payments.isEmpty()) {
      return;
    }
    var payment = payments.get(0);
    stripeClient.refunds().create(RefundCreateParams.builder()
      .setPaymentIntent(payment.getId())
      .build());
    orderMail.send(user.email(), UPGRADE_EMAIL_TITLE,
      String.format(UPGRADE_EMAIL_BODY, user.name(), payment.getAmount() / 100D));
  }

  private CompletableFuture<StripeAccount> findStripeAccount(
    Subscription subscription
  ) {
    var accountId = subscription.getCustomer();
    return stripeDatabaseTable.stripeAccountExists(accountId).thenComposeAsync(
      exists -> findAndStoreCustomer(accountId, subscription, exists).thenCompose(
        customer -> stripeDatabaseTable.findStripeAccount(accountId)));
  }

  private CompletableFuture<Customer> findAndStoreCustomer(
    String accountId, Subscription subscription, boolean exists
  ) {
    try {
      var customer = stripeClient.customers().retrieve(accountId);
      return userDatabaseTable().findUser(customer.getEmail())
        .thenCompose(user -> exists ?
          stripeDatabaseTable.updateStripeAccount(accountId, user.id(),
            subscription.getId()) :
          stripeDatabaseTable.insertStripeAccount(accountId, user.id(),
            subscription.getId()))
        .thenApply(value -> customer);
    } catch (Exception exception) {
      exception.printStackTrace();
      return CompletableFuture.completedFuture(null);
    }
  }

  private void applySubscription(User user, Subscription subscription) {
    applyBundle(user, subscription);
    try {
      sendPaymentEmail(user, subscription);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private void applyBundle(User user, Subscription subscription) {
    try {
      var bundlePreset = findBundlePreset(subscription);
      if (bundlePreset == null) {
        return;
      }
      var bundleRuntime = findBundleRuntime(subscription);
      findBundleTarget(user, bundlePreset).thenAccept(target ->
        bundleDatabaseTable.bundleExists(target).thenAccept(exists ->
          applyBundle(target, bundlePreset, bundleRuntime, exists)));
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

  private BundlePreset findBundlePreset(Subscription subscription) throws Exception {
    var product = subscription.getItems().getData().get(0).getPrice().getProduct();
    if (product.equals(stripeConfiguration.professionalBeginnerMonthlyProductId()) ||
      product.equals(stripeConfiguration.professionalBeginnerYearlyProductId())
    ) {
      return BundlePreset.createAndLoad(BundleType.PROFESSIONAL, BundleClass.BEGINNER);
    } else if (product.equals(stripeConfiguration.professionalAdvancedMonthlyProductId()) ||
      product.equals(stripeConfiguration.professionalAdvancedYearlyProductId())
    ) {
      return BundlePreset.createAndLoad(BundleType.PROFESSIONAL, BundleClass.ADVANCED);
    } else if (product.equals(stripeConfiguration.professionalExpertMonthlyProductId()) ||
      product.equals(stripeConfiguration.professionalExpertYearlyProductId())
    ) {
      return BundlePreset.createAndLoad(BundleType.PROFESSIONAL, BundleClass.EXPERT);
    } else if (product.equals(stripeConfiguration.teamBeginnerMonthlyProductId()) ||
      product.equals(stripeConfiguration.teamBeginnerYearlyProductId())
    ) {
      return BundlePreset.createAndLoad(BundleType.TEAM, BundleClass.BEGINNER);
    } else if (product.equals(stripeConfiguration.teamAdvancedMonthlyProductId()) ||
      product.equals(stripeConfiguration.teamAdvancedYearlyProductId())
    ) {
      return BundlePreset.createAndLoad(BundleType.TEAM, BundleClass.ADVANCED);
    } else if (product.equals(stripeConfiguration.teamExpertMonthlyProductId()) ||
      product.equals(stripeConfiguration.teamExpertYearlyProductId())
    ) {
      return BundlePreset.createAndLoad(BundleType.TEAM, BundleClass.EXPERT);
    }
    return null;
  }

  private BundleRuntime findBundleRuntime(Subscription subscription) {
    var product = subscription.getItems().getData().get(0).getPrice().getProduct();
    if (product.equals(stripeConfiguration.professionalBeginnerMonthlyProductId()) ||
      product.equals(stripeConfiguration.professionalAdvancedMonthlyProductId()) ||
      product.equals(stripeConfiguration.professionalExpertMonthlyProductId()) ||
      product.equals(stripeConfiguration.teamBeginnerMonthlyProductId()) ||
      product.equals(stripeConfiguration.teamAdvancedMonthlyProductId()) ||
      product.equals(stripeConfiguration.teamExpertMonthlyProductId())
    ) {
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

  private void processPaymentCreation(Event event, PaymentIntent paymentIntent) {
    try {
      var subscription = stripeClient.subscriptions()
        .list(SubscriptionListParams.builder()
          .setCustomer(paymentIntent.getCustomer()).build())
        .getData().get(0);
      var customer = stripeClient.customers().retrieve(paymentIntent.getCustomer());
      userDatabaseTable().findUser(customer.getEmail())
        .thenAccept(user -> findBundleTarget(user, subscription)
          .thenAccept(target -> bundleDatabaseTable.findBundle(target)
            .thenAccept(bundle -> terminationDatabaseTable.terminationExists(target)
              .thenAccept(exists -> processPaymentCreation(paymentIntent, target,
                bundle, exists)))));
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private void processPaymentCreation(
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

  private CompletableFuture<UUID> findBundleTarget(
    User user, BundlePreset preset
  ) {
    if (preset.bundleType() == BundleType.PROFESSIONAL) {
      return CompletableFuture.completedFuture(user.id());
    }
    return organizationDatabaseTable.organizationExistsByOwner(user.id())
      .thenCompose(exists -> findOrganization(user, exists));
  }

  private CompletableFuture<UUID> findBundleTarget(
    User user, Subscription subscription
  ) {
    var product = subscription.getItems().getData().get(0).getPrice().getProduct();
    if (product.equals(stripeConfiguration.professionalBeginnerMonthlyProductId()) ||
      product.equals(stripeConfiguration.professionalAdvancedMonthlyProductId()) ||
      product.equals(stripeConfiguration.professionalExpertMonthlyProductId()) ||
      product.equals(stripeConfiguration.professionalBeginnerYearlyProductId()) ||
      product.equals(stripeConfiguration.professionalAdvancedYearlyProductId()) ||
      product.equals(stripeConfiguration.professionalExpertYearlyProductId())
    ) {
      return CompletableFuture.completedFuture(user.id());
    }
    return organizationDatabaseTable.organizationExistsByOwner(user.id())
      .thenCompose(exists -> findOrganization(user, exists));
  }

  private CompletableFuture<UUID> findOrganization(
    User user, boolean organizationExists
  ) {
    if (organizationExists) {
      return organizationDatabaseTable.findOrganizationByOwner(user.id())
        .thenApply(Organization::id);
    }
    var organizationIdFuture = organizationDatabaseTable
      .generateAvailableOrganizationId();
    var organizationName = String.format(coreModule.translate(user,
      "organization.default.name"), user.name());
    organizationIdFuture.thenAccept(organizationId ->
      createOrganization(organizationId, organizationName, user.id()));
    return organizationIdFuture;
  }

  private void createOrganization(UUID organizationId, String name, UUID userId) {
    organizationDatabaseTable.insertOrganization(organizationId, name, userId,
      Lists.newArrayList(), UUID.randomUUID().toString());
    userDatabaseTable().addUserOrganization(userId, organizationId);
    distribution.addUser(organizationId);
  }
}
