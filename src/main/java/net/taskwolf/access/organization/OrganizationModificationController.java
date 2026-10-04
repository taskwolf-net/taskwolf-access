package net.taskwolf.access.organization;

import net.taskwolf.access.stripe.StripeTerminationController;
import net.taskwolf.core.organization.team.Team;
import net.taskwolf.workflow.operation.OperationDatabaseTable;
import net.taskwolf.workflow.throttle.WorkflowThrottleDatabaseTable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.access.account.AccountController;
import net.taskwolf.access.workflow.WorkflowModificationController;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.organization.team.TeamDatabaseTable;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.user.activity.ActivityType;
import net.taskwolf.core.user.activity.UserActivityDatabaseTable;
import net.taskwolf.workflow.structure.WorkflowDatabaseTable;
import net.taskwolf.device.structure.UserDeviceDatabaseTable;
import net.taskwolf.process.access.ProcessModificationController;
import net.taskwolf.process.structure.ProcessDatabaseTable;
import net.taskwolf.table.access.TableModificationController;
import net.taskwolf.table.structure.TableDatabaseTable;
import net.taskwolf.webhook.structure.WebhookDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.UUID;

@RestController
public final class OrganizationModificationController extends OrganizationController {
  private final TeamDatabaseTable teamDatabaseTable;
  private final OrganizationTeamModificationController teamModificationController;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final WorkflowThrottleDatabaseTable workflowThrottleDatabaseTable;
  private final OperationDatabaseTable operationDatabaseTable;
  private final WorkflowModificationController workflowModificationController;
  private final ProcessDatabaseTable processDatabaseTable;
  private final ProcessModificationController processModificationController;
  private final TableDatabaseTable tableDatabaseTable;
  private final TableModificationController tableModificationController;
  private final WebhookDatabaseTable webhookDatabaseTable;
  private final UserDeviceDatabaseTable userDeviceDatabaseTable;
  private final AccountController accountController;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final StripeDatabaseTable stripeDatabaseTable;
  private final StripeTerminationController terminationController;
  private final UserActivityDatabaseTable activityDatabaseTable;

  private OrganizationModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    TeamDatabaseTable teamDatabaseTable,
    OrganizationTeamModificationController teamModificationController,
    UserTargetDatabaseTable targetDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable,
    WorkflowThrottleDatabaseTable workflowThrottleDatabaseTable,
    OperationDatabaseTable operationDatabaseTable,
    WorkflowModificationController workflowModificationController,
    ProcessDatabaseTable processDatabaseTable,
    ProcessModificationController processModificationController,
    TableDatabaseTable tableDatabaseTable,
    TableModificationController tableModificationController,
    WebhookDatabaseTable webhookDatabaseTable,
    UserDeviceDatabaseTable userDeviceDatabaseTable,
    AccountController accountController, BundleDatabaseTable bundleDatabaseTable,
    StripeDatabaseTable stripeDatabaseTable,
    StripeTerminationController terminationController,
    UserActivityDatabaseTable activityDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, organizationDatabaseTable,
      targetDatabaseTable);
    this.teamDatabaseTable = teamDatabaseTable;
    this.teamModificationController = teamModificationController;
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.workflowThrottleDatabaseTable = workflowThrottleDatabaseTable;
    this.operationDatabaseTable = operationDatabaseTable;
    this.workflowModificationController = workflowModificationController;
    this.processDatabaseTable = processDatabaseTable;
    this.processModificationController = processModificationController;
    this.tableDatabaseTable = tableDatabaseTable;
    this.tableModificationController = tableModificationController;
    this.webhookDatabaseTable = webhookDatabaseTable;
    this.userDeviceDatabaseTable = userDeviceDatabaseTable;
    this.accountController = accountController;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.terminationController = terminationController;
    this.activityDatabaseTable = activityDatabaseTable;
  }

  @RequestMapping(path = "/organization/link/regenerate/", method = RequestMethod.GET)
  public void regenerateLink(HttpServletRequest request) {
    performOrganizationOwnerOperation(findUserId(request), organization ->
      organizationDatabaseTable().changeOrganizationInvitationToken(organization,
        UUID.randomUUID().toString()),
      () -> {});
  }

  @RequestMapping(path = "/organization/rename/", method = RequestMethod.POST)
  public void renameOrganization(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var name = body.getSanitizedString("name");
    performOrganizationOwnerOperation(findUserId(request), organization ->
        organizationDatabaseTable().renameOrganization(organization, name),
      () -> {});
  }

  @RequestMapping(path = "/organization/kick/", method = RequestMethod.POST)
  public void kickFromOrganization(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var userId = findUserId(request);
    var targetId = body.getUUID("target");
    performOrganizationOwnerOperation(userId, organization ->
        kickFromOrganization(userId, targetId, organization),
      () -> {});
  }

  private void kickFromOrganization(
    UUID userId, UUID targetId, Organization organization
  ) {
    if (!organization.members().contains(targetId) || targetId.equals(userId)) {
      return;
    }
    organizationDatabaseTable().removeOrganizationMember(organization.id(), targetId);
    userDatabaseTable().removeUserOrganization(targetId, organization.id());
    removeFromOrganizationTeams(targetId, organization.id());
    activityDatabaseTable.insertActivity(targetId, "activity.organization.kick.title",
      "activity.organization.kick.description", ActivityType.ORGANIZATION);
  }

  @RequestMapping(path = "/organization/leave/", method = RequestMethod.GET)
  public void leaveOrganization(HttpServletRequest request) {
    var userId = findUserId(request);
    performOrganizationMemberOperation(userId, organization ->
      leaveOrganization(userId, organization), () -> {});
  }

  public void leaveOrganization(UUID userId, Organization organization) {
    if (organization.owner().equals(userId)) {
      return;
    }
    organizationDatabaseTable().removeOrganizationMember(organization.id(), userId);
    userDatabaseTable().removeUserOrganization(userId, organization.id());
    removeFromOrganizationTeams(userId, organization.id());
    activityDatabaseTable.insertActivity(userId, "activity.organization.leave.title",
      "activity.organization.leave.description", ActivityType.ORGANIZATION);
  }

  private void removeFromOrganizationTeams(UUID userId, UUID organizationId) {
    teamDatabaseTable.findTeamsByOrganization(organizationId)
      .thenAccept(teams -> removeFromOrganizationTeams(userId, teams));
  }

  private void removeFromOrganizationTeams(UUID userId, List<Team> teams) {
    for (var team : teams) {
      if (!team.members().contains(userId)) {
        continue;
      }
      teamDatabaseTable.removeTeamMember(team, userId);
    }
  }

  public void deleteOrganization(Organization organization) {
    organizationDatabaseTable().deleteOrganization(organization.id());
    removeUserOrganization(organization.owner(), organization.id());
    for (var member : organization.members()) {
      removeUserOrganization(member, organization.id());
    }
    teamDatabaseTable.findTeamsByOrganization(organization.id())
      .thenAccept(teams -> teams.forEach(team ->
        teamModificationController.removeTeam(team, false)));
    workflowDatabaseTable.findAllWorkflowsOfOwner(organization.id()).thenAccept(
      workflows -> workflows.forEach(workflowModificationController::deleteWorkflow));
    workflowThrottleDatabaseTable.deleteThrottle(organization.id());
    operationDatabaseTable.deleteOperations(organization.id());
    processDatabaseTable.findAllProcessesOfOwner(organization.id()).thenAccept(
      processes -> processes.forEach(processModificationController::deleteProcess));
    tableDatabaseTable.findAllTablesOfOwner(organization.id()).thenAccept(tables ->
      tables.forEach(tableModificationController::deleteTable));
    webhookDatabaseTable.findAllWebhooksOfOwner(organization.id()).thenAccept(webhooks ->
      webhooks.forEach(webhook -> webhookDatabaseTable.deleteWebhook(webhook.id())));
    userDeviceDatabaseTable.findAllUserDevices(organization.id()).thenAccept(devices ->
      devices.forEach(entry -> userDeviceDatabaseTable.deleteUserDevice(organization.id(),
        entry.deviceId())));
    accountController.deleteAllAccounts(organization.id());
    terminationController.terminate(organization.id()).thenAccept(terminationValue ->
      bundleDatabaseTable.deleteBundle(organization.id()).thenAccept(deletionValue ->
        stripeDatabaseTable.findStripeAccountByTarget(organization.id()).thenAccept(
          account -> stripeDatabaseTable.deleteStripeAccount(account.accountId()))));
  }

  private void removeUserOrganization(UUID userId, UUID organizationId) {
    userDatabaseTable().userExists(userId).thenAccept(exists ->
      removeUserOrganization(userId, organizationId, exists));
  }

  private void removeUserOrganization(
    UUID userId, UUID organizationId, boolean userExists
  ) {
    if (!userExists) {
      return;
    }
    userDatabaseTable().removeUserOrganization(userId, organizationId);
  }
}
