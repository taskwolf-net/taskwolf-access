package net.taskwolf.access.setting;

import com.google.common.hash.Hashing;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.access.account.AccountController;
import net.taskwolf.access.organization.OrganizationModificationController;
import net.taskwolf.access.ticket.TicketModificationController;
import net.taskwolf.access.workflow.WorkflowModificationController;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.mail.TaskwolfMail;
import net.taskwolf.core.notification.NotificationDatabaseTable;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.ticket.TicketDatabaseTable;
import net.taskwolf.core.tutorial.TutorialDatabaseTable;
import net.taskwolf.core.user.*;
import net.taskwolf.core.user.activity.ActivityType;
import net.taskwolf.core.user.activity.UserActivityDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.device.structure.DeviceDatabaseTable;
import net.taskwolf.device.structure.UserDeviceDatabaseTable;
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

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class AccountSettingController extends TaskwolfRestController {
  private final TaskwolfMail changeMail;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final UserPasswordResetDatabaseTable userPasswordResetDatabaseTable;
  private final UserEmailChangeDatabaseTable userEmailChangeDatabaseTable;
  private final NotificationDatabaseTable notificationDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final OrganizationModificationController organizationModificationController;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final WorkflowModificationController workflowModificationController;
  private final ProcessDatabaseTable processDatabaseTable;
  private final ProcessModificationController processModificationController;
  private final TableDatabaseTable tableDatabaseTable;
  private final TableModificationController tableModificationController;
  private final WebhookDatabaseTable webhookDatabaseTable;
  private final TicketDatabaseTable ticketDatabaseTable;
  private final TicketModificationController ticketModificationController;
  private final DeviceDatabaseTable deviceDatabaseTable;
  private final UserDeviceDatabaseTable userDeviceDatabaseTable;
  private final AccountController accountController;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final StripeDatabaseTable stripeDatabaseTable;
  private final TutorialDatabaseTable tutorialDatabaseTable;
  private final UserActivityDatabaseTable activityDatabaseTable;

  private AccountSettingController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    @Qualifier("changeMail") TaskwolfMail changeMail,
    UserTargetDatabaseTable userTargetDatabaseTable,
    UserPasswordResetDatabaseTable userPasswordResetDatabaseTable,
    UserEmailChangeDatabaseTable userEmailChangeDatabaseTable,
    NotificationDatabaseTable notificationDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    OrganizationModificationController organizationModificationController,
    WorkflowDatabaseTable workflowDatabaseTable,
    WorkflowModificationController workflowModificationController,
    ProcessDatabaseTable processDatabaseTable,
    ProcessModificationController processModificationController,
    TableDatabaseTable tableDatabaseTable,
    TableModificationController tableModificationController,
    WebhookDatabaseTable webhookDatabaseTable,
    TicketDatabaseTable ticketDatabaseTable,
    TicketModificationController ticketModificationController,
    DeviceDatabaseTable deviceDatabaseTable,
    UserDeviceDatabaseTable userDeviceDatabaseTable,
    AccountController accountController,
    BundleDatabaseTable bundleDatabaseTable,
    StripeDatabaseTable stripeDatabaseTable,
    TutorialDatabaseTable tutorialDatabaseTable,
    UserActivityDatabaseTable activityDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.changeMail = changeMail;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.userPasswordResetDatabaseTable = userPasswordResetDatabaseTable;
    this.userEmailChangeDatabaseTable = userEmailChangeDatabaseTable;
    this.notificationDatabaseTable = notificationDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.organizationModificationController = organizationModificationController;
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.workflowModificationController = workflowModificationController;
    this.processDatabaseTable = processDatabaseTable;
    this.processModificationController = processModificationController;
    this.tableDatabaseTable = tableDatabaseTable;
    this.tableModificationController = tableModificationController;
    this.webhookDatabaseTable = webhookDatabaseTable;
    this.ticketDatabaseTable = ticketDatabaseTable;
    this.ticketModificationController = ticketModificationController;
    this.deviceDatabaseTable = deviceDatabaseTable;
    this.userDeviceDatabaseTable = userDeviceDatabaseTable;
    this.accountController = accountController;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.stripeDatabaseTable = stripeDatabaseTable;
    this.tutorialDatabaseTable = tutorialDatabaseTable;
    this.activityDatabaseTable = activityDatabaseTable;
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

  private static final String PASSWORD_EMAIL_TITLE = "Set Password";
  private static final String PASSWORD_RESET_URL = "https://taskwolf.net/password/reset/complete/%s/%s/";
  private static final String PASSWORD_EMAIL_BODY = "Hey %s,\n" +
    "\n" +
    "there was a request to set your password!\n" +
    "\n" +
    "If you did not make this request then please ignore this email.\n" +
    "\n" +
    "Otherwise, please click this link to set your password:\n" +
    "\n" +
    "%s";

  private void unlockAccountSettings(User user, boolean requestExists) {
    if (!user.passwordHash().equals("") || requestExists) {
      return;
    }
    var token = UUID.randomUUID().toString();
    userPasswordResetDatabaseTable.insertResetToken(user.id(), token);
    var body = String.format(PASSWORD_EMAIL_BODY, user.name(),
      String.format(PASSWORD_RESET_URL, user.id().toString(), token));
    changeMail.send(user.email(), PASSWORD_EMAIL_TITLE, body);
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

  private static final String EMAIL_TITLE = "Email Change";
  private static final String EMAIL_CHANGE_URL = "https://taskwolf.net/email/change/complete/%s/%s/";
  private static final String EMAIL_BODY = "Hey, \n" +
    "\n" +
    "we have received a request to replace the email of one of our accounts with this email.\n" +
    "\n" +
    "If you are not a Taskwolf customer or have not requested the replacement, please ignore this email.\n" +
    "\n" +
    "However, if this is a genuine request, please click on the link below to complete the change:\n" +
    "\n" +
    "%s";

  private Map<String, Object> requestEmailChange(
    User user, String newEmail, boolean requestExists
  ) {
    var token = UUID.randomUUID().toString();
    if (requestExists) {
      userEmailChangeDatabaseTable.updateChange(user.id(), newEmail, token);
    } else {
      userEmailChangeDatabaseTable.insertChange(user.id(), newEmail, token);
    }
    var body = String.format(EMAIL_BODY, String.format(EMAIL_CHANGE_URL,
      user.id().toString(), token));
    changeMail.send(newEmail, EMAIL_TITLE, body);
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
    userPasswordResetDatabaseTable.deleteResetToken(user.id());
    userEmailChangeDatabaseTable.deleteChange(user.id());
    notificationDatabaseTable.deleteNotificationSettings(user.id());
    var futureDevices = deviceDatabaseTable.findDevicesOfOwner(user.id());
    futureDevices.thenAccept(devices -> devices.forEach(device ->
      deviceDatabaseTable.deleteDevice(device.id())));
    futureDevices.thenAccept(devices -> devices.forEach(device ->
      userDeviceDatabaseTable.findUsersOfDevice(device.id()).thenAccept(users ->
        users.forEach(entry -> userDeviceDatabaseTable.removeDevice(entry,
          device.id())))));
    userDeviceDatabaseTable.deleteDevices(user.id());
    ticketDatabaseTable.findTicketsByCreator(user.id()).thenAccept(tables ->
      tables.forEach(ticketModificationController::deleteTicket));
    for (var organizationId : user.organizations()) {
      organizationDatabaseTable.findOrganization(organizationId).thenAccept(
        organization -> accountDeletionHandleOrganization(user, organization));
    }
    tutorialDatabaseTable.deleteTutorial(user.id());
    activityDatabaseTable.deleteActivity(user.id());
  }

  public void deleteAccountServices(UUID userId) {
    workflowDatabaseTable.findWorkflowsOfOwner(userId).thenAccept(workflows ->
      workflows.forEach(workflowModificationController::deleteWorkflow));
    processDatabaseTable.findProcessesOfOwner(userId).thenAccept(processes ->
      processes.forEach(processModificationController::deleteProcess));
    tableDatabaseTable.findTablesOfOwner(userId).thenAccept(tables ->
      tables.forEach(tableModificationController::deleteTable));
    webhookDatabaseTable.findWebhooksByOwner(userId).thenAccept(webhooks ->
      webhooks.forEach(webhook -> webhookDatabaseTable.deleteWebhook(webhook.id())));
    accountController.deleteAllAccounts(userId);
    bundleDatabaseTable.deleteBundle(userId);
    stripeDatabaseTable.deleteStripeAccountByTarget(userId);
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
