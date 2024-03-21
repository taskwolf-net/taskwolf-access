package net.taskwolf.access.setting;

import com.google.common.hash.Hashing;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.access.account.AccountController;
import net.taskwolf.access.organization.OrganizationModificationController;
import net.taskwolf.access.workflow.WorkflowModificationController;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.distribution.Distribution;
import net.taskwolf.core.mail.TaskwolfMail;
import net.taskwolf.core.notification.NotificationDatabaseTable;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.user.*;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
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
  private final ProfilePictureDatabaseTable profilePictureDatabaseTable;
  private final NotificationDatabaseTable notificationDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final OrganizationModificationController organizationModificationController;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final WorkflowModificationController workflowModificationController;
  private final AccountController accountController;
  private final Distribution distribution;

  private AccountSettingController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    @Qualifier("changeMail") TaskwolfMail changeMail,
    UserTargetDatabaseTable userTargetDatabaseTable,
    UserPasswordResetDatabaseTable userPasswordResetDatabaseTable,
    UserEmailChangeDatabaseTable userEmailChangeDatabaseTable,
    ProfilePictureDatabaseTable profilePictureDatabaseTable,
    NotificationDatabaseTable notificationDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    OrganizationModificationController organizationModificationController,
    WorkflowDatabaseTable workflowDatabaseTable,
    WorkflowModificationController workflowModificationController,
    AccountController accountController, Distribution distribution
  ) {
    super(secretKey, userDatabaseTable);
    this.changeMail = changeMail;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.userPasswordResetDatabaseTable = userPasswordResetDatabaseTable;
    this.userEmailChangeDatabaseTable = userEmailChangeDatabaseTable;
    this.profilePictureDatabaseTable = profilePictureDatabaseTable;
    this.notificationDatabaseTable = notificationDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.organizationModificationController = organizationModificationController;
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.workflowModificationController = workflowModificationController;
    this.accountController = accountController;
    this.distribution = distribution;
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
    return Map.of("success", true);
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
    userDatabaseTable().deleteUser(user.id());
    userTargetDatabaseTable.deleteTarget(user.id());
    userPasswordResetDatabaseTable.deleteResetToken(user.id());
    userEmailChangeDatabaseTable.deleteChange(user.id());
    profilePictureDatabaseTable.deleteProfilePicture(user.id());
    notificationDatabaseTable.deleteNotificationSettings(user.id());
    accountController.deleteAllAccounts(user.id());
    distribution.removeUser(user.id());
    workflowDatabaseTable.findWorkflowsOfOwner(user.id()).thenAccept(workflows ->
      workflows.forEach(workflowModificationController::deleteWorkflow));
    for (var organizationId : user.organizations()) {
      organizationDatabaseTable.findOrganization(organizationId).thenAccept(
        organization -> accountDeletionHandleOrganization(user, organization));
    }
  }

  private void accountDeletionHandleOrganization(User user, Organization organization) {
    if (organization.owner().equals(user.id())) {
      organizationModificationController.deleteOrganization(organization);
    } else {
      organizationModificationController.leaveOrganization(user, organization.id());
    }
  }

  private String hashPassword(String password) {
    return Hashing.sha256().hashString(password, StandardCharsets.UTF_8)
      .toString();
  }
}
