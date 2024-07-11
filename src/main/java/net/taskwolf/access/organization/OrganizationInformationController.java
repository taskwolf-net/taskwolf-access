package net.taskwolf.access.organization;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class OrganizationInformationController extends OrganizationController {
  private OrganizationInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    UserTargetDatabaseTable targetDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, organizationDatabaseTable,
      targetDatabaseTable);
  }

  @RequestMapping(path = "/organization/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findSelectedOrganization(
    HttpServletRequest request
  ) {
    var userId = findUserId(request);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performOrganizationMemberOperation(userId, organization ->
        gatherOrganizationInformation(organization, userId)
          .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherOrganizationInformation(
    Organization organization, UUID applicantId
  ) {
    return userDatabaseTable().findUser(organization.owner())
      .thenCompose(owner -> findOrganizationMembers(organization.members())
        .thenApply(members -> assemblyOrganizationInformation(organization,
          owner, members, applicantId)));
  }

  private CompletableFuture<List<User>> findOrganizationMembers(
    List<UUID> memberIds
  ) {
    var futureResponse = new CompletableFuture<List<User>>();
    AsyncIterator.execute(memberIds, member -> userDatabaseTable().findUser(member))
      .thenAccept(futureResponse::complete);
    return futureResponse;
  }

  private Map<String, Object> assemblyOrganizationInformation(
    Organization organization, User owner, List<User> members, UUID applicantId
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", organization.id());
    information.put("name", organization.name());
    information.put("owner", owner.name());
    information.put("isOwner", applicantId.equals(owner.id()));
    var membersInformation = Lists.<Map<String, Object>>newArrayList();
    for (var member : members) {
      var memberInformation = Maps.<String, Object>newHashMap();
      memberInformation.put("id", member.id());
      memberInformation.put("name", member.name());
      membersInformation.add(memberInformation);
    }
    information.put("members", membersInformation);
    return information;
  }

  private static final String LINK_FORMAT = "https://taskwolf.net/organization/join/%s/%s/";

  @RequestMapping(path = "/organization/link/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findLink(HttpServletRequest request) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performOrganizationMemberOperation(findUserId(request), organization ->
        futureResponse.complete(Map.of("link", String.format(LINK_FORMAT,
          organization.id(), organization.invitationToken()))),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }
}
