package net.taskwolf.access.organization;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.access.account.AccountController;
import net.taskwolf.access.workflow.WorkflowModificationController;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.distribution.Distribution;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class OrganizationModificationController extends TaskwolfRestController {
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final Distribution distribution;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final WorkflowModificationController workflowModificationController;
  private final AccountController accountController;

  private OrganizationModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable, Distribution distribution,
    WorkflowDatabaseTable workflowDatabaseTable,
    WorkflowModificationController workflowModificationController,
    AccountController accountController
  ) {
    super(secretKey, userDatabaseTable);
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.distribution = distribution;
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.workflowModificationController = workflowModificationController;
    this.accountController = accountController;
  }

  @RequestMapping(path = "/organization/create/", method = RequestMethod.POST)
  public void createOrganization(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var name = (String) input.get("name");
    var userId = findUserId(request);
    organizationDatabaseTable.generateAvailableOrganizationId().thenAccept(id ->
        createOrganization(id, name, userId));
  }

  private void createOrganization(UUID organizationId, String name, UUID userId) {
    organizationDatabaseTable.insertOrganization(organizationId, name, userId,
      Lists.newArrayList(), UUID.randomUUID().toString());
    userDatabaseTable().addUserOrganization(userId, organizationId);
    distribution.addNewUser(organizationId);
  }

  @RequestMapping(path = "/organization/link/regenerate/", method = RequestMethod.POST)
  public void regenerateLink(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var organizationId = UUID.fromString((String) input.get("organization"));
    findUser(request).thenAccept(user ->
      organizationDatabaseTable.organizationExists(organizationId).thenAccept(
        exists -> regenerateLink(user, organizationId, exists)));
  }

  private void regenerateLink(
    User user, UUID organizationId, boolean organizationExists
  ) {
    if (!organizationExists) {
      return;
    }
    if (!user.organizations().contains(organizationId)) {
      return;
    }
    organizationDatabaseTable.findOrganization(organizationId).thenAccept(
      organization -> regenerateLink(user, organization));
  }

  private void regenerateLink(User user, Organization organization) {
    if (!organization.owner().equals(user.id())) {
      return;
    }
    organizationDatabaseTable.changeOrganizationInvitationToken(organization.id(),
      UUID.randomUUID().toString());
  }

  @RequestMapping(path = "/organization/join/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> joinOrganization(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var organizationId = UUID.fromString((String) input.get("organization"));
    var invitationToken = (String) input.get("token");
    findUser(request).thenAccept(user ->
      organizationDatabaseTable.organizationExists(organizationId).thenAccept(
        exists -> joinOrganization(user, organizationId, exists, invitationToken)
          .thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> joinOrganization(
    User user, UUID organizationId, boolean organizationExists, String token
  ) {
    if (!organizationExists) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1000));
    }
    if (user.organizations().contains(organizationId)){
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1001));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    organizationDatabaseTable.findOrganization(organizationId).thenAccept(
      organization -> futureResponse.complete(joinOrganization(user,
        organization, token)));
    return futureResponse;
  }

  private Map<String, Object> joinOrganization(
    User user, Organization organization, String token
  ) {
    if (!organization.invitationToken().equals(token)) {
      return Map.of("success", false, "errorCode", 1002);
    }
    organizationDatabaseTable.addOrganizationMember(organization.id(), user.id());
    userDatabaseTable().addUserOrganization(user.id(), organization.id());
    return Map.of("success", true);
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

  @RequestMapping(path = "/organization/leave/", method = RequestMethod.POST)
  public void leaveOrganization(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var organizationId = UUID.fromString((String) input.get("organization"));
    findUser(request).thenAccept(user -> leaveOrganization(user, organizationId));
  }

  public void leaveOrganization(User user, UUID organizationId) {
    if (!user.organizations().contains(organizationId)) {
      return;
    }
    organizationDatabaseTable.removeOrganizationMember(organizationId, user.id());
    userDatabaseTable().removeUserOrganization(user.id(), organizationId);
  }

  @RequestMapping(path = "/organization/delete/", method = RequestMethod.POST)
  public void deleteOrganization(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var organizationId = UUID.fromString((String) input.get("organization"));
    findUser(request).thenAccept(user -> deleteOrganization(user, organizationId));
  }

  public void deleteOrganization(User user, UUID organizationId) {
    if (!user.organizations().contains(organizationId)) {
      return;
    }
    organizationDatabaseTable.findOrganization(organizationId)
      .thenAccept(this::deleteOrganization);
  }

  public void deleteOrganization(Organization organization) {
    organizationDatabaseTable.deleteOrganization(organization.id());
    userDatabaseTable().removeUserOrganization(organization.owner(),
      organization.id());
    for (var member : organization.members()) {
      userDatabaseTable().removeUserOrganization(member, organization.id());
    }
    workflowDatabaseTable.findWorkflowsOfOwner(organization.id()).thenAccept(
      workflows -> workflows.forEach(workflowModificationController::deleteWorkflow));
    accountController.deleteAllAccounts(organization.id());
  }
}
