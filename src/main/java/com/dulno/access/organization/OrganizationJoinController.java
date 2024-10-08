package com.dulno.access.organization;

import com.dulno.access.verification.Verification;
import com.dulno.access.verification.VerificationLoginController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoHomeRestController;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.organization.Organization;
import com.dulno.core.organization.OrganizationDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.activity.ActivityType;
import com.dulno.core.user.activity.UserActivityDatabaseTable;
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
public final class OrganizationJoinController extends DulnoHomeRestController {
  private final Key homeKey;
  private final Key refreshKey;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final UserActivityDatabaseTable activityDatabaseTable;
  private final VerificationLoginController verificationLoginController;

  private OrganizationJoinController(
    @Qualifier("homeKey") Key homeKey, @Qualifier("productKey") Key productKey,
    @Qualifier("refreshKey") Key refreshKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable,
    UserActivityDatabaseTable activityDatabaseTable,
    VerificationLoginController verificationLoginController
  ) {
    super(homeKey, userDatabaseTable);
    this.homeKey = homeKey;
    this.refreshKey = refreshKey;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.activityDatabaseTable = activityDatabaseTable;
    this.verificationLoginController = verificationLoginController;
  }

  @RequestMapping(path = "/organization/join/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> joinOrganization(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var organizationId = body.getUUID("organization");
    findUser(request).thenAccept(user ->
      organizationDatabaseTable.organizationExists(organizationId).thenAccept(
        exists -> joinOrganization(request, user, organizationId, exists,
          body.getString("token")).thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> joinOrganization(
    HttpServletRequest request, User user, UUID organizationId,
    boolean organizationExists, String token
  ) {
    if (!organizationExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "errorCode", 1000));
    }
    if (user.organizations().contains(organizationId)){
      return CompletableFuture.completedFuture(Map.of("success", false,
        "errorCode", 1001));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    organizationDatabaseTable.findOrganization(organizationId).thenAccept(
      organization -> checkOrganizationSizeLimit(organization).thenAccept(
        limitReached -> joinOrganization(request, user, organization, token,
          limitReached).thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Boolean> checkOrganizationSizeLimit(
    Organization organization
  ) {
    return bundleDatabaseTable.findBundle(organization.id()).thenApply(
      bundle -> bundle.organizationMemberLimit() > 0 &&
        organization.members().size() >= bundle.organizationMemberLimit());
  }

  private CompletableFuture<Map<String, Object>> joinOrganization(
    HttpServletRequest request, User user, Organization organization,
    String token, boolean limitReached
  ) {
    if (!organization.invitationToken().equals(token)) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "errorCode", 1002));
    }
    if (limitReached) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "errorCode", 1003));
    }
    organizationDatabaseTable.addOrganizationMember(organization.id(), user.id());
    userDatabaseTable().addUserOrganization(user.id(), organization.id());
    activityDatabaseTable.insertActivity(user.id(), "activity.organization.join.title",
      "activity.organization.join.description", ActivityType.ORGANIZATION);
    var futureResponse = loginUser(request, user);
    futureResponse.thenAccept(response -> response.put("success", true));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> loginUser(
    HttpServletRequest request, User user
  ) {
    var verification = Verification.create(userDatabaseTable(), homeKey,
      secretKey(), refreshKey, user.email(), "");
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    verificationLoginController.processAuthorizedLogin(request, verification,
      futureResponse);
    return futureResponse;
  }
}
