package com.dulno.access.setting;

import com.dulno.access.organization.OrganizationModificationController;
import com.dulno.access.stripe.StripeTerminationController;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.session.SessionDatabaseTable;
import com.dulno.core.user.mfa.MultiFactorAuthDatabaseTable;
import com.dulno.core.workflow.operation.OperationDatabaseTable;
import com.dulno.core.workflow.throttle.WorkflowThrottleDatabaseTable;
import com.dulno.device.access.DeviceModificationController;
import com.google.common.hash.Hashing;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.access.account.AccountController;
import com.dulno.access.ticket.TicketModificationController;
import com.dulno.access.workflow.WorkflowModificationController;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.notification.NotificationDatabaseTable;
import com.dulno.core.organization.Organization;
import com.dulno.core.organization.OrganizationDatabaseTable;
import com.dulno.core.stripe.StripeDatabaseTable;
import com.dulno.core.ticket.TicketDatabaseTable;
import com.dulno.core.tutorial.TutorialDatabaseTable;
import com.dulno.core.user.*;
import com.dulno.core.user.activity.ActivityType;
import com.dulno.core.user.activity.UserActivityDatabaseTable;
import com.dulno.core.workflow.WorkflowDatabaseTable;
import com.dulno.device.structure.DeviceDatabaseTable;
import com.dulno.process.access.ProcessModificationController;
import com.dulno.process.structure.ProcessDatabaseTable;
import com.dulno.table.access.TableModificationController;
import com.dulno.table.structure.TableDatabaseTable;
import com.dulno.webhook.structure.WebhookDatabaseTable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
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

  private AccountSettingController(
    @Qualifier("productKey") Key productKey, @Qualifier("homeKey") Key homeKey,
    UserDatabaseTable userDatabaseTable, @Qualifier("changeMail") Mail changeMail,
    Translation translation, UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
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
    SessionDatabaseTable sessionDatabaseTable
  ) {
    super(productKey, homeKey, userDatabaseTable);
    this.changeMail = changeMail;
    this.translation = translation;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.teamTargetDatabaseTable = teamTargetDatabaseTable;
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
    findUser(request).thenAccept(user -> userPasswordResetDatabaseTable
      .resetTokenExists(user.id()).thenAccept(requestExists ->
        unlockAccountSettings(user, requestExists)));
  }

  private static final String PASSWORD_RESET_URL = "https://dulno.com/password/reset/complete/%s/%s/";

  private void unlockAccountSettings(User user, boolean requestExists) {
    if (!user.passwordHash().equals("") || requestExists) {
      return;
    }
    var token = UUID.randomUUID().toString();
    userPasswordResetDatabaseTable.insertResetToken(user.id(), token);
    var title = translation.translate(user, "password.set.email.title");
    var body = String.format(
      translation.translate(user, "password.set.email.body"), user.name(),
      String.format(PASSWORD_RESET_URL, user.id().toString(), token));
    changeMail.send(user.email(), title, body);
  }

  @RequestMapping(path = "/settings/account/password/change/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> changePassword(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> futureResponse.complete(
      changePassword(user, body.getString("currentPassword"),
        body.getString("newPassword"))));
    return futureResponse;
  }

  private Map<String, Object> changePassword(
    User user, String currentPassword, String newPassword
  ) {
    if (!user.passwordHash().equals(hashPassword(currentPassword))) {
      return Map.of("success", false);
    }
    userDatabaseTable().changeUserPassword(user.id(), hashPassword(newPassword));
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
    var body = DulnoRequestBody.of(payload, response);
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
    if (!user.passwordHash().equals(hashPassword(password))) {
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

  private static final String EMAIL_CHANGE_URL = "https://dulno.com/email/change/complete/%s/%s/";

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
      String.format(EMAIL_CHANGE_URL, user.id().toString(), token));
    changeMail.send(newEmail, title, body);
    return Map.of("success", true);
  }

  @RequestMapping(path = "/settings/account/email/change/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completeEmailChange(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
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
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> futureResponse.complete(
      deleteAccount(user, body.getString("password"))));
    return futureResponse;
  }

  private Map<String, Object> deleteAccount(
    User user, String password
  ) {
    if (!user.passwordHash().equals(hashPassword(password))) {
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
    ticketDatabaseTable.findTicketsByCreator(user.id())
      .thenAccept(tickets -> tickets.forEach(ticket ->
        ticketModificationController.deleteTicket(ticket, false)));
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

  private String hashPassword(String password) {
    return Hashing.sha256().hashString(password, StandardCharsets.UTF_8)
      .toString();
  }
}
