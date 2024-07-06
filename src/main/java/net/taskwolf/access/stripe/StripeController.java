package net.taskwolf.access.stripe;

import com.google.common.collect.Lists;
import com.stripe.StripeClient;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
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
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.worker.WorkerDistribution;
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
public final class StripeController extends TaskwolfRestController {
  private final StripeConfiguration stripeConfiguration;
  private final StripeDatabaseTable stripeDatabaseTable;
  private final StripeClient stripeClient;
  private final TaskwolfMail orderMail;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final WorkerDistribution distribution;
  private final CoreModule coreModule;

  private StripeController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable, StripeClient stripeClient,
    @Qualifier("orderMail") TaskwolfMail orderMail,
    OrganizationDatabaseTable organizationDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable,  WorkerDistribution distribution,
    CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.stripeConfiguration = stripeConfiguration;
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.stripeClient = stripeClient;
    this.orderMail = orderMail;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
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
      processSubscriptionCreation(event.get(), (Subscription) stripeObject);
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
    stripeDatabaseTable.stripeAccountExistsById(subscription.getCustomer())
      .thenAcceptAsync(exists -> findCustomer(subscription, exists)
        .thenAcceptAsync(customer -> applySubscription(customer, subscription)));
  }

  private CompletableFuture<Customer> findCustomer(Subscription subscription, boolean exists) {
    try {
      var customer = stripeClient.customers().retrieve(subscription.getCustomer());
      if (exists) {
        return CompletableFuture.completedFuture(customer);
      }
      return userDatabaseTable().findUser(customer.getEmail())
        .thenCompose(user -> stripeDatabaseTable.insertStripeAccount(
          user.id(), customer.getId()).thenApply(value -> customer));
    } catch (Exception exception) {
      exception.printStackTrace();
      return null;
    }
  }

  private void applySubscription(
    Customer customer, Subscription subscription
  ) {
    var accountFuture = stripeDatabaseTable.findStripeAccountById(subscription.getCustomer());
    accountFuture.thenAccept(account -> stripeDatabaseTable.updateStripeAccountSubscription(
      account, subscription.getId()));
    accountFuture.thenAccept(account -> applyBundle(account, subscription));
    try {
      sendPaymentEmail(customer, subscription);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private void applyBundle(StripeAccount account, Subscription subscription) {
    try {
      var bundlePreset = findBundlePreset(subscription);
      if (bundlePreset == null) {
        return;
      }
      var bundleRuntime = findBundleRuntime(subscription);
      findBundleTarget(account, bundlePreset).thenAccept(target ->
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
    if (bundleExists) {
      bundleDatabaseTable.updateBundle(Bundle.of(target, bundlePreset, bundleRuntime));
      return;
    }
    bundleDatabaseTable.insertBundle(Bundle.of(target, bundlePreset, bundleRuntime));
  }

  private CompletableFuture<UUID> findBundleTarget(
    StripeAccount account, BundlePreset preset
  ) {
    if (preset.bundleType() == BundleType.PROFESSIONAL) {
      return CompletableFuture.completedFuture(account.userId());
    }
    return userDatabaseTable().findUser(account.userId()).thenCompose(user ->
      organizationDatabaseTable.organizationExistsByOwner(user.id()).thenCompose(
        exists -> findOrganization(user, exists)));
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
    Customer customer, Subscription subscription
  ) throws Exception {
    var invoice = stripeClient.invoices().retrieve(subscription.getLatestInvoice());
    var invoiceFile = new File(System.getProperty("user.dir") + "/invoices/" +
      invoice.getId() + ".pdf");
    invoiceFile.getParentFile().mkdirs();
    invoiceFile.createNewFile();
    downloadInvoice(invoice.getInvoicePdf(), invoiceFile.getAbsoluteFile());
    orderMail.send(customer.getEmail(), PAYMENT_EMAIL_TITLE,
        String.format(PAYMENT_EMAIL_BODY, customer.getName()),
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
}
