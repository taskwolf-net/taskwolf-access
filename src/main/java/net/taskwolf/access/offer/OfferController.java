package net.taskwolf.access.offer;

import lombok.Getter;
import lombok.experimental.Accessors;
import net.taskwolf.core.access.TaskwolfHomeRestController;
import net.taskwolf.core.offer.Offer;
import net.taskwolf.core.offer.OfferDatabaseTable;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;

import java.security.Key;
import java.util.UUID;
import java.util.function.Consumer;

@Getter
@Accessors(fluent = true)
public class OfferController  extends TaskwolfHomeRestController {
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final OfferDatabaseTable offerDatabaseTable;

  protected OfferController(
    Key homeKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    OfferDatabaseTable offerDatabaseTable
  ) {
    super(homeKey, userDatabaseTable);
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.offerDatabaseTable = offerDatabaseTable;
  }

  protected void performOfferOperation(
    UUID userId, UUID offerId, Consumer<Offer> operation,
    Runnable failResponse
  ) {
    offerDatabaseTable.offerExists(offerId).thenAccept(exists ->
      performOfferOperation(userId, offerId, exists, operation, failResponse));
  }

  protected void performOfferOperation(
    UUID userId, UUID offerId, boolean offerExists, Consumer<Offer> operation,
    Runnable failResponse
  ) {
    if (!offerExists) {
      failResponse.run();
      return;
    }
    offerDatabaseTable.findOffer(offerId).thenAccept(offer ->
      userDatabaseTable().findUser(userId).thenAccept(user ->
        userDatabaseTable().userExists(offer.targetId())
          .thenAccept(targetIsUser -> performOfferOperation(user, offer,
            targetIsUser, operation, failResponse))));
  }

  protected void performOfferOperation(
    User user, Offer offer, boolean offerTargetIsUser, Consumer<Offer> operation,
    Runnable failResponse
  ) {
    if (!offer.offerStatus().isPending()) {
      failResponse.run();
      return;
    }
    if (!offerTargetIsUser) {
      organizationDatabaseTable.findOrganization(offer.targetId())
        .thenAccept(organization -> performOfferOperation(user, offer,
          organization, operation, failResponse));
      return;
    }
    if (!offer.targetId().equals(user.id())) {
      failResponse.run();
      return;
    }
    operation.accept(offer);
  }

  protected void performOfferOperation(
    User user, Offer offer, Organization organization, Consumer<Offer> operation,
    Runnable failResponse
  ) {
    if (!organization.owner().equals(user.id())) {
      failResponse.run();
      return;
    }
    operation.accept(offer);
  }
}
