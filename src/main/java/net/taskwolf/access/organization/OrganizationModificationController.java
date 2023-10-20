package net.taskwolf.access.organization;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
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

  private OrganizationModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    InvitationDatabaseTable invitationDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.invitationDatabaseTable = invitationDatabaseTable;
  }

  @RequestMapping(path = "/organization/create/", method = RequestMethod.POST)
  public void createOrganization(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var name = (String) input.get("name");
    findUser(request).thenAccept(user -> organizationDatabaseTable
      .generateAvailableOrganizationId().thenAccept(id ->
        createOrganization(id, name, user.id())));
  }

  private void createOrganization(UUID organizationId, String name, UUID userId) {
    organizationDatabaseTable.insertOrganization(organizationId, name, userId,
      Lists.newArrayList());
    userDatabaseTable().addUserOrganization(userId, organizationId);
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
  }

  @RequestMapping(path = "/organization/invitation/accept/", method = RequestMethod.POST)
  public void acceptInvitation(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var organizationId = UUID.fromString((String) input.get("organization"));
    findUser(request).thenAccept(user ->
      invitationDatabaseTable.findInvitations(user.id()).thenAccept(invitations ->
        acceptInvitation(user, invitations, organizationId)));
  }

  private void acceptInvitation(
    User user, List<UUID> invitations, UUID organizationId
  ) {
    if (!invitations.contains(organizationId)) {
      return;
    }
    invitationDatabaseTable.removeInvitation(user.id(), organizationId);
    userDatabaseTable().addUserOrganization(user.id(), organizationId);
    organizationDatabaseTable.addOrganizationMember(organizationId, user.id());
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
