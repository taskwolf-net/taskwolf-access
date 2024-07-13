package net.taskwolf.access.organization;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.access.account.AccountController;
import net.taskwolf.access.workflow.WorkflowModificationController;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.organization.OrganizationTeamDatabaseTable;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.user.activity.ActivityType;
import net.taskwolf.core.user.activity.UserActivityDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class OrganizationModificationController extends OrganizationController {
  private final OrganizationTeamDatabaseTable teamDatabaseTable;
  private final WorkflowDatabaseTable workflowDatabaseTable;
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
  private final UserActivityDatabaseTable activityDatabaseTable;

  private OrganizationModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    OrganizationTeamDatabaseTable teamDatabaseTable,
    UserTargetDatabaseTable targetDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable,
    WorkflowModificationController workflowModificationController,
    ProcessDatabaseTable processDatabaseTable,
    ProcessModificationController processModificationController,
    TableDatabaseTable tableDatabaseTable,
    TableModificationController tableModificationController,
    WebhookDatabaseTable webhookDatabaseTable,
    UserDeviceDatabaseTable userDeviceDatabaseTable,
    AccountController accountController, BundleDatabaseTable bundleDatabaseTable,
    StripeDatabaseTable stripeDatabaseTable,
    UserActivityDatabaseTable activityDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, organizationDatabaseTable,
      targetDatabaseTable);
    this.teamDatabaseTable = teamDatabaseTable;
    this.workflowDatabaseTable = workflowDatabaseTable;
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
    var name = body.getString("name");
    performOrganizationOwnerOperation(findUserId(request), organization ->
        organizationDatabaseTable().renameOrganization(organization, name),
      () -> {});
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
      organizationDatabaseTable().organizationExists(organizationId).thenAccept(
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
    organizationDatabaseTable().findOrganization(organizationId).thenAccept(
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
    organizationDatabaseTable().addOrganizationMember(organization.id(), user.id());
    userDatabaseTable().addUserOrganization(user.id(), organization.id());
    activityDatabaseTable.insertActivity(user.id(), "activity.organization.join.title",
      "activity.organization.join.description", ActivityType.ORGANIZATION);
    return Map.of("success", true);
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
    userDatabaseTable().removeUserOrganization(targetId, organization.id());
    organizationDatabaseTable().removeOrganizationMember(organization.id(), targetId);
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
    activityDatabaseTable.insertActivity(userId, "activity.organization.leave.title",
      "activity.organization.leave.description", ActivityType.ORGANIZATION);
  }

  public void deleteOrganization(Organization organization) {
    organizationDatabaseTable().deleteOrganization(organization.id());
    userDatabaseTable().removeUserOrganization(organization.owner(),
      organization.id());
    for (var member : organization.members()) {
      userDatabaseTable().removeUserOrganization(member, organization.id());
    }
    teamDatabaseTable.findTeamsByOrganization(organization.id()).thenAccept(
      teams -> teams.forEach(team -> teamDatabaseTable.deleteTeam(team.id())));
    workflowDatabaseTable.findWorkflowsOfOwner(organization.id()).thenAccept(
      workflows -> workflows.forEach(workflowModificationController::deleteWorkflow));
    processDatabaseTable.findProcessesOfOwner(organization.id()).thenAccept(
      processes -> processes.forEach(processModificationController::deleteProcess));
    tableDatabaseTable.findTablesOfOwner(organization.id()).thenAccept(tables ->
      tables.forEach(tableModificationController::deleteTable));
    webhookDatabaseTable.findWebhooksByOwner(organization.id()).thenAccept(webhooks ->
      webhooks.forEach(webhook -> webhookDatabaseTable.deleteWebhook(webhook.id())));
    userDeviceDatabaseTable.deleteDevices(organization.id());
    accountController.deleteAllAccounts(organization.id());
    bundleDatabaseTable.deleteBundle(organization.id());
    stripeDatabaseTable.deleteStripeAccountByTarget(organization.id());
  }
}
