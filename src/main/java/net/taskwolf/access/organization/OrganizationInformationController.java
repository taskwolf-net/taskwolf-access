package net.taskwolf.access.organization;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.organization.InvitationDatabaseTable;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
public final class OrganizationInformationController extends TaskwolfRestController {
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final InvitationDatabaseTable invitationDatabaseTable;

  private OrganizationInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    InvitationDatabaseTable invitationDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.invitationDatabaseTable = invitationDatabaseTable;
  }

  @RequestMapping(path = "/organization/invitations/personal/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findPersonalInvitations(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    invitationDatabaseTable.findInvitations(findUserId(request)).thenAccept(
      invitations -> collectPersonalInvitations(invitations)
        .thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> collectPersonalInvitations(
    List<UUID> invitations
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(invitations, organizationId ->
        organizationDatabaseTable.findOrganization(organizationId)
          .thenApply(organization -> new AbstractMap.SimpleEntry(organizationId,
            organization.name())), invitations.size(), organizations ->
      futureResponse.complete(Map.of("invitations", organizations)));
    return futureResponse;
  }

  @RequestMapping(path = "/organization/selected/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findSelectedOrganization(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var id = UUID.fromString((String) input.get("id"));
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> gatherOrganizationInformation(
      id, user.id()).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  @RequestMapping(path = "/organization/invitations/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findOrganizationInvitations(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var id = UUID.fromString((String) input.get("id"));
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> findOrganizationInvitations(user, id)
      .thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findOrganizationInvitations(
    User user, UUID organizationId
  ) {
    if (!user.organizations().contains(organizationId)) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    organizationDatabaseTable.findOrganization(organizationId).thenAccept(organization ->
      collectOrganizationInvitations(organization).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> collectOrganizationInvitations(
    Organization organization
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(organization.invitations(), invited ->
      userDatabaseTable().findUser(invited), organization.invitations().size(),
      users -> futureResponse.complete(Map.of("invitations",
        users.stream().map(User::name).collect(Collectors.toList()))));
    return futureResponse;
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
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(organizations, organization ->
        gatherOrganizationInformation(organization, applicantId),
      organizations.size(), information -> futureResponse.complete(
        Map.of("organizations", information)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherOrganizationInformation(
    UUID organizationId, UUID applicantId
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    organizationDatabaseTable.findOrganization(organizationId).thenAccept(
      organization -> userDatabaseTable().findUser(organization.owner()).thenAccept(
        owner -> findOrganizationMembers(organization.members()).thenAccept(
          members -> futureResponse.complete(
            assemblyOrganizationInformation(organization, owner, members, applicantId)))));
    return futureResponse;
  }

  private CompletableFuture<List<User>> findOrganizationMembers(
    List<UUID> memberIds
  ) {
    var futureResponse = new CompletableFuture<List<User>>();
    AsyncIterator.execute(memberIds, member -> userDatabaseTable().findUser(member),
      memberIds.size(), futureResponse::complete);
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
    information.put("members", members.stream().map(User::name)
      .collect(Collectors.toList()));
    return information;
  }
}
