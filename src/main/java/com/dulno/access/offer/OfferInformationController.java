package com.dulno.access.offer;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.offer.Offer;
import com.dulno.core.offer.OfferDatabaseTable;
import com.dulno.core.organization.OrganizationDatabaseTable;
import com.dulno.core.user.UserDatabaseTable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class OfferInformationController extends OfferController {
  private OfferInformationController(
    @Qualifier("homeKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    OfferDatabaseTable offerDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, organizationDatabaseTable,
      offerDatabaseTable);
  }

  @RequestMapping(path = "/offer/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findOffer(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performOfferOperation(findUserId(request), body.getUUID("offer"),
      offer -> futureResponse.complete(findOffer(offer)),
      () -> futureResponse.complete(Map.of("found", false)));
    return futureResponse;
  }

  private Map<String, Object> findOffer(Offer offer) {
    var information = Maps.<String, Object>newHashMap();
    information.put("found", true);
    information.put("id", offer.id().toString());
    information.put("type", offer.bundleType().toString());
    information.put("class", offer.bundleClass().toString());
    information.put("runtime", offer.bundleRuntime().toString());
    information.put("price", offer.price());
    information.put("workflowAccess", offer.workflowAccess());
    information.put("workflowNumberLimit", offer.workflowNumberLimit());
    information.put("workflowOperationLimit", offer.workflowOperationLimit());
    information.put("workflowTemplateAccess", offer.workflowTemplateAccess());
    information.put("processAccess", offer.processAccess());
    information.put("processNumberLimit", offer.processNumberLimit());
    information.put("databaseAccess", offer.databaseAccess());
    information.put("databaseNumberLimit", offer.databaseNumberLimit());
    information.put("databaseDataLimit", new DecimalFormat("#.#").format(
      offer.databaseDataLimit()));
    information.put("webhookAccess", offer.webhookAccess());
    information.put("webhookNumberLimit", offer.webhookNumberLimit());
    information.put("organizationAccess", offer.organizationAccess());
    information.put("organizationMemberLimit", offer.organizationMemberLimit());
    information.put("organizationTeamLimit", offer.organizationTeamLimit());
    information.put("deviceAccess", offer.deviceAccess());
    information.put("accountsAccess", offer.accountsAccess());
    information.put("accountsNumberLimit", offer.accountsNumberLimit());
    return information;
  }
}
