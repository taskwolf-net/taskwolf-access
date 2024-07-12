package net.taskwolf.access.stripe;

import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.stripe.StripeAccount;
import net.taskwolf.core.stripe.StripeConfiguration;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;

import java.security.Key;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Getter(AccessLevel.PROTECTED)
@Accessors(fluent = true)
public class StripeController extends TaskwolfRestController {
  private final StripeConfiguration stripeConfiguration;
  private final StripeDatabaseTable stripeDatabaseTable;
  private final UserTargetDatabaseTable targetDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;

  protected StripeController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable,
    UserTargetDatabaseTable targetDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.stripeConfiguration = stripeConfiguration;
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.targetDatabaseTable = targetDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
  }

  protected void performStripeOperation(
    UUID userId, Consumer<StripeAccount> operation,
    Runnable failResponse
  ) {
    targetDatabaseTable.findTargetSecured(userId).thenAccept(target ->
      checkUserPermission(userId, target).thenAccept(hasPermission ->
        performStripeOperation(userId, target, hasPermission, operation,
          failResponse)));
  }

  private CompletableFuture<Boolean> checkUserPermission(
    UUID userId, UUID targetId
  ) {
    if (userId.equals(targetId)) {
      return CompletableFuture.completedFuture(true);
    }
    return organizationDatabaseTable.findOrganization(targetId)
      .thenApply(organization -> organization.owner().equals(userId));
  }

  private void performStripeOperation(
    UUID userId, UUID targetId, boolean hasPermission,
    Consumer<StripeAccount> operation, Runnable failResponse
  ) {
    if (!hasPermission) {
      failResponse.run();
      return;
    }
    stripeDatabaseTable.stripeAccountExistsByTarget(targetId)
      .thenAccept(exists -> performStripeOperation(targetId, exists,
        operation, failResponse));
  }

  private void performStripeOperation(
    UUID targetId, boolean accountExists,
    Consumer<StripeAccount> operation, Runnable failResponse
  ) {
    if (!accountExists) {
      failResponse.run();
      return;
    }
    stripeDatabaseTable.findStripeAccountByTarget(targetId)
      .thenAccept(operation::accept);
  }

  protected Optional<StripeObject> findStripeObject(Event event) {
    var dataObjectDeserializer = event.getDataObjectDeserializer();
    if (dataObjectDeserializer.getObject().isEmpty()) {
      return Optional.empty();
    }
    return dataObjectDeserializer.getObject();
  }

  protected Optional<Event> findStripeEvent(
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
}

