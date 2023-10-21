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

  @RequestMapping(path = "/organization/invitations/all/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findAllInvitations(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    invitationDatabaseTable.findInvitations(findUserId(request)).thenAccept(
      invitations -> futureResponse.complete(Map.of("invitations", invitations)));
    return futureResponse;
  }

  @RequestMapping(path = "/organizations/all/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findAllOrganizations(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> collectOrganizationsInformation(
      user.organizations()).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> collectOrganizationsInformation(
    List<UUID> organizations
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(organizations, this::gatherOrganizationInformation,
      organizations.size(), information -> futureResponse.complete(
        Map.of("organizations", information)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherOrganizationInformation(
    UUID organizationId
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    organizationDatabaseTable.findOrganization(organizationId).thenAccept(
      organization -> userDatabaseTable().findUser(organization.owner()).thenAccept(
        owner -> findOrganizationMembers(organization.members()).thenAccept(
          members -> futureResponse.complete(
            assemblyOrganizationInformation(organization, owner, members)))));
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
    Organization organization, User owner, List<User> members
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("name", organization.name());
    information.put("owner", owner.name());
    information.put("members", members.stream().map(User::name)
      .collect(Collectors.toList()));
    return information;
  }
}
