package net.taskwolf.access.organization;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfHomeRestController;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.activity.ActivityType;
import net.taskwolf.core.user.activity.UserActivityDatabaseTable;
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
public final class OrganizationJoinController extends TaskwolfHomeRestController {
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final UserActivityDatabaseTable activityDatabaseTable;

  private OrganizationJoinController(
    @Qualifier("homeKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable,
    UserActivityDatabaseTable activityDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.activityDatabaseTable = activityDatabaseTable;
  }

  @RequestMapping(path = "/organization/join/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> joinOrganization(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var organizationId = body.getUUID("organization");
    findUser(request).thenAccept(user ->
      organizationDatabaseTable.organizationExists(organizationId).thenAccept(
        exists -> joinOrganization(user, organizationId, exists,
          body.getString("token")).thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> joinOrganization(
    User user, UUID organizationId, boolean organizationExists, String token
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
        limitReached -> futureResponse.complete(joinOrganization(user,
          organization, token, limitReached))));
    return futureResponse;
  }

  private CompletableFuture<Boolean> checkOrganizationSizeLimit(
    Organization organization
  ) {
    return bundleDatabaseTable.findBundle(organization.id()).thenApply(
      bundle -> bundle.organizationMemberLimit() > 0 &&
        organization.members().size() >= bundle.organizationMemberLimit());
  }

  private Map<String, Object> joinOrganization(
    User user, Organization organization, String token, boolean limitReached
  ) {
    if (!organization.invitationToken().equals(token)) {
      return Map.of("success", false, "errorCode", 1002);
    }
    if (limitReached) {
      return Map.of("success", false, "errorCode", 1003);
    }
    organizationDatabaseTable.addOrganizationMember(organization.id(), user.id());
    userDatabaseTable().addUserOrganization(user.id(), organization.id());
    activityDatabaseTable.insertActivity(user.id(), "activity.organization.join.title",
      "activity.organization.join.description", ActivityType.ORGANIZATION);
    return Map.of("success", true);
  }
}
