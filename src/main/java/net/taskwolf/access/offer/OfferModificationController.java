package net.taskwolf.access.offer;

import com.stripe.StripeClient;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.bundle.BundleType;
import net.taskwolf.core.offer.Offer;
import net.taskwolf.core.offer.OfferDatabaseTable;
import net.taskwolf.core.offer.OfferStatus;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.stripe.StripeAccount;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
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
    var body = TaskwolfRequestBody.of(payload, response);
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
        .setSuccessUrl("https://taskwolf.net/payment/complete/")
        .setCancelUrl("https://taskwolf.net/offer/" + offer.id() + "/")
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
    if (bundleType.isProfessional()) {
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
    var body = TaskwolfRequestBody.of(payload, response);
    performOfferOperation(findUserId(request), body.getUUID("offer"),
      offer -> offerDatabaseTable().updateOfferStatus(offer, OfferStatus.DECLINED),
      () -> {});
  }
}
