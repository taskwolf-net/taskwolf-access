package com.dulno.access.offer;

import com.stripe.StripeClient;
import com.stripe.param.PriceUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.bundle.BundleType;
import com.dulno.core.offer.Offer;
import com.dulno.core.offer.OfferDatabaseTable;
import com.dulno.core.offer.OfferStatus;
import com.dulno.core.organization.OrganizationDatabaseTable;
import com.dulno.core.stripe.StripeAccount;
import com.dulno.core.stripe.StripeDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class OfferModificationController extends OfferController {
  private final StripeDatabaseTable stripeDatabaseTable;
  private final StripeClient stripeClient;

  private OfferModificationController(
    @Qualifier("homeKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    OfferDatabaseTable offerDatabaseTable, StripeDatabaseTable stripeDatabaseTable,
    StripeClient stripeClient
  ) {
    super(secretKey, userDatabaseTable, organizationDatabaseTable,
      offerDatabaseTable);
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.stripeClient = stripeClient;
  }

  @RequestMapping(path = "/offer/accept/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> acceptOffer(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user ->
      performOfferOperation(user.id(), body.getUUID("offer"),
        offer -> findExistingAccount(user, offer.bundleType()).thenAcceptAsync(
          account -> futureResponse.complete(acceptOffer(offer, user, account))),
        () -> futureResponse.complete(Map.of("success", false))));
    return futureResponse;
  }

  public Map<String, Object> acceptOffer(Offer offer, User user, String accountId) {
    try {
      var sessionBuilder = SessionCreateParams.builder()
        .addLineItem(SessionCreateParams.LineItem.builder()
          .setPrice(offer.priceId())
          .setQuantity(1L)
          .build())
        .setSuccessUrl("https://dulno.com/payment/complete/")
        .setCancelUrl("https://dulno.com/offer/" + offer.id() + "/")
        .setMode(SessionCreateParams.Mode.SUBSCRIPTION);
      if (!accountId.isEmpty()) {
        sessionBuilder.setCustomer(accountId);
      } else {
        sessionBuilder.setCustomerEmail(user.email());
      }
      var checkout = stripeClient.checkout().sessions()
        .create(sessionBuilder.build());
      return Map.of("success", true, "link", checkout.getUrl());
    } catch (Exception exception) {
      exception.printStackTrace();
      return Map.of("success", false);
    }
  }

  private CompletableFuture<String> findExistingAccount(
    User user, BundleType bundleType
  ) {
    if (bundleType.isIndividual()) {
      return findAccountIfExists(user.id());
    }
    return organizationDatabaseTable().organizationExistsByOwner(user.id())
      .thenCompose(exists -> exists ?
        organizationDatabaseTable().findOrganizationByOwner(user.id())
          .thenCompose(organization -> findAccountIfExists(organization.id())) :
        CompletableFuture.completedFuture(""));
  }

  private CompletableFuture<String> findAccountIfExists(UUID target) {
    return stripeDatabaseTable.stripeAccountExistsByTarget(target)
      .thenCompose(exists -> exists ?
        stripeDatabaseTable.findStripeAccountByTarget(target)
          .thenApply(StripeAccount::accountId) :
        CompletableFuture.completedFuture(""));
  }

  @RequestMapping(path = "/offer/decline/", method = RequestMethod.POST)
  public void declineOffer(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    performOfferOperation(findUserId(request), body.getUUID("offer"),
      this::declineOffer, () -> {});
  }

  private void declineOffer(Offer offer) {
    offerDatabaseTable().updateOfferStatus(offer, OfferStatus.DECLINED);
    try {
      stripeClient.prices().update(offer.priceId(),
        PriceUpdateParams.builder().setActive(false).build());
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }
}
