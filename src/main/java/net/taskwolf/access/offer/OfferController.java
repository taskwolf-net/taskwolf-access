package net.taskwolf.access.offer;

import lombok.Getter;
import lombok.experimental.Accessors;
import net.taskwolf.core.access.TaskwolfHomeRestController;
import net.taskwolf.core.offer.Offer;
import net.taskwolf.core.offer.OfferDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;

import java.security.Key;
import java.util.UUID;
import java.util.function.Consumer;

@Getter
@Accessors(fluent = true)
public class OfferController  extends TaskwolfHomeRestController {
  private final OfferDatabaseTable offerDatabaseTable;

  protected OfferController(
    Key homeKey, UserDatabaseTable userDatabaseTable,
    OfferDatabaseTable offerDatabaseTable
  ) {
    super(homeKey, userDatabaseTable);
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
      performOfferOperation(userId, offer, operation, failResponse));
  }

  protected void performOfferOperation(
    UUID userId, Offer offer, Consumer<Offer> operation, Runnable failResponse
  ) {
    if (!offer.offerStatus().isPending() || !offer.targetId().equals(userId)) {
      failResponse.run();
      return;
    }
    operation.accept(offer);
  }
}
