package net.taskwolf.access.setting;

import net.taskwolf.access.organization.OrganizationModificationController;
import net.taskwolf.access.password.PasswordController;
import net.taskwolf.access.stripe.StripeTerminationController;
import net.taskwolf.core.environment.TaskwolfEnvironment;
import net.taskwolf.core.hashing.Hashing;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.core.session.SessionDatabaseTable;
import net.taskwolf.core.user.mfa.MultiFactorAuthDatabaseTable;
import net.taskwolf.workflow.operation.OperationDatabaseTable;
import net.taskwolf.device.access.DeviceModificationController;
import net.taskwolf.workflow.throttle.WorkflowThrottleDatabaseTable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.access.account.AccountController;
import net.taskwolf.access.ticket.TicketModificationController;
import net.taskwolf.access.workflow.WorkflowModificationController;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.locale.Translation;
import net.taskwolf.core.mail.Mail;
import net.taskwolf.core.notification.NotificationDatabaseTable;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.ticket.TicketDatabaseTable;
import net.taskwolf.core.tutorial.TutorialDatabaseTable;
import net.taskwolf.core.user.*;
import net.taskwolf.core.user.activity.ActivityType;
import net.taskwolf.core.user.activity.UserActivityDatabaseTable;
import net.taskwolf.workflow.structure.WorkflowDatabaseTable;
import net.taskwolf.device.structure.DeviceDatabaseTable;
import net.taskwolf.process.access.ProcessModificationController;
import net.taskwolf.process.structure.ProcessDatabaseTable;
import net.taskwolf.table.access.TableModificationController;
import net.taskwolf.table.structure.TableDatabaseTable;
import net.taskwolf.webhook.structure.WebhookDatabaseTable;
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
public final class AccountSettingController extends SettingController {
  private final Mail changeMail;
  private final Translation translation;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final TeamTargetDatabaseTable teamTargetDatabaseTable;
  private final PasswordController passwordController;
  private final UserPasswordResetDatabaseTable userPasswordResetDatabaseTable;
  private final UserEmailChangeDatabaseTable userEmailChangeDatabaseTable;
  private final NotificationDatabaseTable notificationDatabaseTable;
  private final MultiFactorAuthDatabaseTable multiFactorAuthDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final OrganizationModificationController organizationModificationController;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final WorkflowThrottleDatabaseTable workflowThrottleDatabaseTable;
  private final OperationDatabaseTable operationDatabaseTable;
  private final WorkflowModificationController workflowModificationController;
  private final ProcessDatabaseTable processDatabaseTable;
  private final ProcessModificationController processModificationController;
  private final TableDatabaseTable tableDatabaseTable;
  private final TableModificationController tableModificationController;
  private final WebhookDatabaseTable webhookDatabaseTable;
  private final TicketDatabaseTable ticketDatabaseTable;
  private final TicketModificationController ticketModificationController;
  private final DeviceDatabaseTable deviceDatabaseTable;
  private final DeviceModificationController deviceModificationController;
  private final AccountController accountController;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final StripeDatabaseTable stripeDatabaseTable;
  private final StripeTerminationController terminationController;
  private final TutorialDatabaseTable tutorialDatabaseTable;
  private final UserActivityDatabaseTable activityDatabaseTable;
  private final SessionDatabaseTable sessionDatabaseTable;
  private final Hashing hashing;
  private final TaskwolfEnvironment environment;

  private AccountSettingController(
    @Qualifier("productKey") Key productKey, @Qualifier("homeKey") Key homeKey,
    UserDatabaseTable userDatabaseTable, @Qualifier("changeMail") Mail changeMail,
    Translation translation, UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    PasswordController passwordController,
    UserPasswordResetDatabaseTable userPasswordResetDatabaseTable,
    UserEmailChangeDatabaseTable userEmailChangeDatabaseTable,
    NotificationDatabaseTable notificationDatabaseTable,
    MultiFactorAuthDatabaseTable multiFactorAuthDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    OrganizationModificationController organizationModificationController,
    WorkflowDatabaseTable workflowDatabaseTable,
    WorkflowThrottleDatabaseTable workflowThrottleDatabaseTable,
    OperationDatabaseTable operationDatabaseTable,
    WorkflowModificationController workflowModificationController,
    ProcessDatabaseTable processDatabaseTable,
    ProcessModificationController processModificationController,
    TableDatabaseTable tableDatabaseTable,
    TableModificationController tableModificationController,
    WebhookDatabaseTable webhookDatabaseTable,
    TicketDatabaseTable ticketDatabaseTable,
    TicketModificationController ticketModificationController,
    DeviceDatabaseTable deviceDatabaseTable,
    DeviceModificationController deviceModificationController,
    AccountController accountController,
    BundleDatabaseTable bundleDatabaseTable,
    StripeDatabaseTable stripeDatabaseTable,
    StripeTerminationController terminationController,
    TutorialDatabaseTable tutorialDatabaseTable,
    UserActivityDatabaseTable activityDatabaseTable,
    SessionDatabaseTable sessionDatabaseTable, Hashing hashing,
    TaskwolfEnvironment environment
  ) {
    super(productKey, homeKey, userDatabaseTable);
    this.changeMail = changeMail;
    this.translation = translation;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.teamTargetDatabaseTable = teamTargetDatabaseTable;
    this.passwordController = passwordController;
    this.userPasswordResetDatabaseTable = userPasswordResetDatabaseTable;
    this.userEmailChangeDatabaseTable = userEmailChangeDatabaseTable;
    this.notificationDatabaseTable = notificationDatabaseTable;
    this.multiFactorAuthDatabaseTable = multiFactorAuthDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.organizationModificationController = organizationModificationController;
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.workflowThrottleDatabaseTable = workflowThrottleDatabaseTable;
    this.operationDatabaseTable = operationDatabaseTable;
    this.workflowModificationController = workflowModificationController;
    this.processDatabaseTable = processDatabaseTable;
    this.processModificationController = processModificationController;
    this.tableDatabaseTable = tableDatabaseTable;
    this.tableModificationController = tableModificationController;
    this.webhookDatabaseTable = webhookDatabaseTable;
    this.ticketDatabaseTable = ticketDatabaseTable;
    this.ticketModificationController = ticketModificationController;
    this.deviceDatabaseTable = deviceDatabaseTable;
    this.deviceModificationController = deviceModificationController;
    this.accountController = accountController;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.terminationController = terminationController;
    this.tutorialDatabaseTable = tutorialDatabaseTable;
    this.activityDatabaseTable = activityDatabaseTable;
    this.sessionDatabaseTable = sessionDatabaseTable;
    this.hashing = hashing;
    this.environment = environment;
  }

  @RequestMapping(path = "/settings/account/unlocked/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> accountSettingsUnlocked(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> futureResponse.complete(
      Map.of("unlocked", !user.passwordHash().equals(""))));
    return futureResponse;
  }

  @RequestMapping(path = "/settings/account/unlock/", method = RequestMethod.GET)
  public void unlockAccountSettings(
    HttpServletRequest request
  ) {
    findUser(request).thenAccept(user ->
      passwordController.requestPasswordReset(user, "password.set.email.title",
        "password.set.email.body"));
  }

  @RequestMapping(path = "/settings/account/password/change/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> changePassword(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> futureResponse.complete(
      changePassword(user, body.getString("currentPassword"),
        body.getString("newPassword"))));
    return futureResponse;
  }

  private Map<String, Object> changePassword(
    User user, String currentPassword, String newPassword
  ) {
    if (!hashing.matches(currentPassword, user.passwordHash())) {
      return Map.of("success", false);
    }
    userDatabaseTable().changeUserPassword(user.id(), hashing.hash(newPassword));
    activityDatabaseTable.insertActivity(user.id(), "activity.setting.password.title",
      "activity.setting.password.description", ActivityType.SETTING);
    return Map.of("success", true);
  }

  @RequestMapping(path = "/settings/account/email/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findEmail(
    HttpServletRequest request
  ) {
    return findUser(request).thenApply(user -> Map.of("email", user.email()));
  }

  @RequestMapping(path = "/settings/account/email/change/request/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> requestEmailChange(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var newEmail = body.getString("newEmail");
    findUser(request).thenAccept(user -> userDatabaseTable().userExists(newEmail)
      .thenAccept(exists -> requestEmailChange(user, body.getString("password"),
        newEmail, exists).thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> requestEmailChange(
    User user, String password, String newEmail, boolean accountExists
  ) {
    if (!hashing.matches(password, user.passwordHash())) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1000));
    }
    if (accountExists) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1001));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userEmailChangeDatabaseTable.changeExists(user.id()).thenAccept(requestExists ->
      futureResponse.complete(requestEmailChange(user, newEmail, requestExists)));
    return futureResponse;
  }

  private static final String EMAIL_CHANGE_URL = "https://%s/email/change/complete/%s/%s/";

  private Map<String, Object> requestEmailChange(
    User user, String newEmail, boolean requestExists
  ) {
    var token = UUID.randomUUID().toString();
    if (requestExists) {
      userEmailChangeDatabaseTable.updateChange(user.id(), newEmail, token);
    } else {
      userEmailChangeDatabaseTable.insertChange(user.id(), newEmail, token);
    }
    var title = translation.translate(user, "email.change.email.title");
    var body = String.format(
      translation.translate(user, "email.change.email.body"),
      String.format(EMAIL_CHANGE_URL, environment.domain(), user.id().toString(),
        token));
    changeMail.send(newEmail, user.language(), title, body);
    return Map.of("success", true);
  }

  @RequestMapping(path = "/settings/account/email/change/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completeEmailChange(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var userId = body.getUUID("user");
    userEmailChangeDatabaseTable.changeExists(userId).thenAccept(exists ->
      completeEmailChange(userId, body.getString("token"), exists)
        .thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> completeEmailChange(
    UUID userId, String token, boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1000));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userEmailChangeDatabaseTable.findChange(userId).thenAccept(changeParameters ->
      completeEmailChange(userId, token, changeParameters).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> completeEmailChange(
    UUID userId, String token, Map.Entry<String, String> changeParameters
  ) {
    if (!token.equals(changeParameters.getValue())) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1001));
    }
    userEmailChangeDatabaseTable.deleteChange(userId);
    userDatabaseTable().changeUserEmail(userId, changeParameters.getKey());
    activityDatabaseTable.insertActivity(userId, "activity.setting.email.title",
      "activity.setting.email.description", ActivityType.SETTING);
    return CompletableFuture.completedFuture(Map.of("success", true));
  }

  @RequestMapping(path = "/settings/account/delete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> deleteAccount(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> futureResponse.complete(
      deleteAccount(user, body.getString("password"))));
    return futureResponse;
  }

  private Map<String, Object> deleteAccount(
    User user, String password
  ) {
    if (!hashing.matches(password, user.passwordHash())) {
      return Map.of("success", false);
    }
    deleteAccount(user);
    return Map.of("success", true);
  }

  public void deleteAccount(User user) {
    deleteAccountServices(user.id());
    userDatabaseTable().deleteUser(user.id());
    userTargetDatabaseTable.deleteTarget(user.id());
    teamTargetDatabaseTable.deleteTarget(user.id());
    userPasswordResetDatabaseTable.deleteResetToken(user.id());
    userEmailChangeDatabaseTable.deleteChange(user.id());
    notificationDatabaseTable.deleteNotificationSettings(user.id());
    multiFactorAuthDatabaseTable.deleteAuth(user.id());
    deviceDatabaseTable.findDevicesOfOwner(user.id()).thenAccept(devices ->
      devices.forEach(deviceModificationController::deleteDevice));
    ticketDatabaseTable.findAllTicketsOfCreator(user.id()).thenAccept(tickets ->
      tickets.forEach(ticketModificationController::deleteTicket));
    for (var organizationId : user.organizations()) {
      organizationDatabaseTable.findOrganization(organizationId).thenAccept(
        organization -> accountDeletionHandleOrganization(user, organization));
    }
    tutorialDatabaseTable.deleteTutorial(user.id());
    activityDatabaseTable.findActivitiesOfUser(user.id())
      .thenAccept(activities -> activities.forEach(activity ->
        activityDatabaseTable.deleteActivity(activity.id())));
    sessionDatabaseTable.findSessionsOfUser(user.id()).thenAccept(sessions ->
      sessions.forEach(session -> sessionDatabaseTable.deleteSession(session.id())));
  }

  public void deleteAccountServices(UUID userId) {
    workflowDatabaseTable.findAllWorkflowsOfOwner(userId).thenAccept(workflows ->
      workflows.forEach(workflowModificationController::deleteWorkflow));
    workflowThrottleDatabaseTable.deleteThrottle(userId);
    operationDatabaseTable.deleteOperations(userId);
    processDatabaseTable.findAllProcessesOfOwner(userId).thenAccept(processes ->
      processes.forEach(processModificationController::deleteProcess));
    tableDatabaseTable.findAllTablesOfOwner(userId).thenAccept(tables ->
      tables.forEach(tableModificationController::deleteTable));
    webhookDatabaseTable.findAllWebhooksOfOwner(userId).thenAccept(webhooks ->
      webhooks.forEach(webhook -> webhookDatabaseTable.deleteWebhook(webhook.id())));
    accountController.deleteAllAccounts(userId);
    terminationController.terminate(userId).thenAccept(terminationValue ->
      bundleDatabaseTable.deleteBundle(userId).thenAccept(deletionValue ->
        stripeDatabaseTable.findStripeAccountByTarget(userId).thenAccept(account ->
          stripeDatabaseTable.deleteStripeAccount(account.accountId()))));
  }

  private void accountDeletionHandleOrganization(User user, Organization organization) {
    if (organization.owner().equals(user.id())) {
      organizationModificationController.deleteOrganization(organization);
    } else {
      organizationModificationController.leaveOrganization(user.id(),
        organization);
    }
  }
}
