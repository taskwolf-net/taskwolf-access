package net.taskwolf.access.organization;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.distribution.Distribution;
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

@CrossOrigin
@RestController
public final class OrganizationModificationController extends TaskwolfRestController {
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final InvitationDatabaseTable invitationDatabaseTable;
  private final Distribution distribution;

  private OrganizationModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    InvitationDatabaseTable invitationDatabaseTable, Distribution distribution
  ) {
    super(secretKey, userDatabaseTable);
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.invitationDatabaseTable = invitationDatabaseTable;
    this.distribution = distribution;
  }

  @RequestMapping(path = "/organization/create/", method = RequestMethod.POST)
  public void createOrganization(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var name = (String) input.get("name");
    organizationDatabaseTable.generateAvailableOrganizationId().thenAccept(id ->
        createOrganization(id, name, findUserId(request)));
  }

  private void createOrganization(UUID organizationId, String name, UUID userId) {
    organizationDatabaseTable.insertOrganization(organizationId, name, userId,
      Lists.newArrayList(), Lists.newArrayList());
    userDatabaseTable().addUserOrganization(userId, organizationId);
    distribution.addNewUser(organizationId);
  }

  @RequestMapping(path = "/organization/invite/", method = RequestMethod.POST)
  public void inviteToOrganization(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var organizationId = UUID.fromString((String) input.get("organization"));
    var targetId = UUID.fromString((String) input.get("target"));
    findUser(request).thenAccept(user ->
      inviteToOrganization(user, organizationId, targetId));
  }

  private void inviteToOrganization(User user, UUID organizationId, UUID targetId) {
    if (!user.organizations().contains(organizationId)) {
      return;
    }
    organizationDatabaseTable.findOrganization(organizationId)
      .thenAccept(organization -> inviteToOrganization(user, organization, targetId));
  }

  private void inviteToOrganization(
    User user, Organization organization, UUID targetId
  ) {
    if (!organization.owner().equals(user.id()) ||
      organization.members().contains(targetId) || targetId.equals(user.id())
    ) {
      return;
    }
    invitationDatabaseTable.addInvitation(targetId, organization.id());
    organizationDatabaseTable.addOrganizationInvitation(organization.id(), targetId);
  }

  @RequestMapping(path = "/organization/invitation/accept/", method = RequestMethod.POST)
  public void acceptInvitation(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var organizationId = UUID.fromString((String) input.get("organization"));
    var userId = findUserId(request);
    invitationDatabaseTable.findInvitations(userId).thenAccept(invitations ->
        acceptInvitation(userId, invitations, organizationId));
  }

  private void acceptInvitation(
    UUID userId, List<UUID> invitations, UUID organizationId
  ) {
    if (!invitations.contains(organizationId)) {
      return;
    }
    invitationDatabaseTable.removeInvitation(userId, organizationId);
    userDatabaseTable().addUserOrganization(userId, organizationId);
    organizationDatabaseTable.addOrganizationMember(organizationId, userId);
    organizationDatabaseTable.removeOrganizationInvitation(organizationId, userId);
  }

  @RequestMapping(path = "/organization/kick/", method = RequestMethod.POST)
  public void kickFromOrganization(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var organizationId = UUID.fromString((String) input.get("organization"));
    var targetId = UUID.fromString((String) input.get("target"));
    findUser(request).thenAccept(user ->
      kickFromOrganization(user, organizationId, targetId));
  }

  private void kickFromOrganization(User user, UUID organizationId, UUID targetId) {
    if (!user.organizations().contains(organizationId)) {
      return;
    }
    organizationDatabaseTable.findOrganization(organizationId)
      .thenAccept(organization -> kickFromOrganization(user, organization, targetId));
  }

  private void kickFromOrganization(
    User user, Organization organization, UUID targetId
  ) {
    if (!organization.owner().equals(user.id()) ||
      !organization.members().contains(targetId) || targetId.equals(user.id())
    ) {
      return;
    }
    userDatabaseTable().removeUserOrganization(targetId, organization.id());
    organizationDatabaseTable.removeOrganizationMember(organization.id(), targetId);
  }

  @RequestMapping(path = "/organization/delete/", method = RequestMethod.POST)
  public void deleteOrganization(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var organizationId = UUID.fromString((String) input.get("organization"));
    findUser(request).thenAccept(user -> deleteOrganization(user, organizationId));
  }

  private void deleteOrganization(User user, UUID organizationId) {
    if (!user.organizations().contains(organizationId)) {
      return;
    }
    organizationDatabaseTable.findOrganization(organizationId)
      .thenAccept(this::deleteOrganization);
  }

  private void deleteOrganization(Organization organization) {
    organizationDatabaseTable.deleteOrganization(organization.id());
    userDatabaseTable().removeUserOrganization(organization.owner(),
      organization.id());
    for (var member : organization.members()) {
      userDatabaseTable().removeUserOrganization(member, organization.id());
    }
  }
}
