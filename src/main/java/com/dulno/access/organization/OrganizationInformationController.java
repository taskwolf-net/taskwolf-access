package com.dulno.access.organization;

import com.dulno.core.environment.DulnoEnvironment;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.organization.Organization;
import com.dulno.core.organization.OrganizationDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
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
  private final DulnoEnvironment environment;

  private OrganizationInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    UserTargetDatabaseTable targetDatabaseTable, DulnoEnvironment environment
  ) {
    super(secretKey, userDatabaseTable, organizationDatabaseTable,
      targetDatabaseTable);
    this.environment = environment;
  }

  @RequestMapping(path = "/organizations/all/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findAllOrganizations(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> collectOrganizationsInformation(
      user.organizations(), user.id()).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> collectOrganizationsInformation(
    List<UUID> organizations, UUID applicantId
  ) {
    return AsyncIterator.execute(organizations, organizationId ->
        organizationDatabaseTable().findOrganization(organizationId).thenCompose(
          organization -> gatherOrganizationInformation(organization, applicantId)))
      .thenApply(information -> Map.of("organizations", information));
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
    information.put("ownerId", owner.id());
    information.put("ownerName", owner.name());
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

  private static final String LINK_FORMAT = "https://%s/organization/join/%s/%s/";

  @RequestMapping(path = "/organization/link/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findLink(HttpServletRequest request) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performOrganizationMemberOperation(findUserId(request), organization ->
        futureResponse.complete(Map.of("link", String.format(LINK_FORMAT,
          environment.domain(), organization.id(), organization.invitationToken()))),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }
}
