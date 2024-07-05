package net.taskwolf.access.stripe;

import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.log.Log;
import net.taskwolf.core.stripe.StripeConfiguration;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Optional;

@RestController
public final class StripeController extends TaskwolfRestController {
  private final StripeConfiguration stripeConfiguration;
  private final StripeDatabaseTable stripeDatabaseTable;
  private final Log log;

  private StripeController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable, Log log
  ) {
    super(secretKey, userDatabaseTable);
    this.stripeConfiguration = stripeConfiguration;
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.log = log;
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
    switch (event.get().getType()) {
      case "customer.created":
        processCustomerCreation(event.get(), (Customer) stripeObject);
      case "customer.subscription.created":
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

  private void processCustomerCreation(Event event, Customer customer) {
    userDatabaseTable().findUser(customer.getEmail()).thenAccept(user ->
      stripeDatabaseTable.insertStripeAccount(user.id(), customer.getId()));
  }

  private void processSubscriptionCreation(Event event, Subscription subscription) {
    stripeDatabaseTable.stripeAccountExistsById(subscription.getCustomer())
      .thenAccept(exists -> saveSubscription(subscription, exists));
  }

  private void saveSubscription(Subscription subscription, boolean accountExists) {
    if (!accountExists) {
      log.severe("An error has occurred during the payment process: " +
        "The subscription " + subscription.getId() + " could not be assigned correctly. " +
        "This is because no account with the customer ID could be found in our database.");
      return;
    }
    stripeDatabaseTable.findStripeAccountById(subscription.getCustomer())
      .thenAccept(account -> stripeDatabaseTable.updateStripeAccountSubscription(
        account, subscription.getId()));
  }
}
