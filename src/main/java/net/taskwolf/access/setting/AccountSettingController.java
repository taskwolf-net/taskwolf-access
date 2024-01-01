package net.taskwolf.access.setting;

import com.google.common.hash.Hashing;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.access.account.AccountController;
import net.taskwolf.access.organization.OrganizationModificationController;
import net.taskwolf.access.workflow.WorkflowModificationController;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.user.ProfilePictureDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserPasswordResetDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class AccountSettingController extends TaskwolfRestController {
  private final UserPasswordResetDatabaseTable userPasswordResetDatabaseTable;
  private final ProfilePictureDatabaseTable profilePictureDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final OrganizationModificationController organizationModificationController;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final WorkflowModificationController workflowModificationController;
  private final AccountController accountController;

  private AccountSettingController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    UserPasswordResetDatabaseTable userPasswordResetDatabaseTable,
    ProfilePictureDatabaseTable profilePictureDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    OrganizationModificationController organizationModificationController,
    WorkflowDatabaseTable workflowDatabaseTable,
    WorkflowModificationController workflowModificationController,
    AccountController accountController
  ) {
    super(secretKey, userDatabaseTable);
    this.userPasswordResetDatabaseTable = userPasswordResetDatabaseTable;
    this.profilePictureDatabaseTable = profilePictureDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.organizationModificationController = organizationModificationController;
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.workflowModificationController = workflowModificationController;
    this.accountController = accountController;
  }

  @RequestMapping(path = "/settings/account/password/change/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> changePassword(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var currentPassword = (String) input.get("currentPassword");
    var newPassword = (String) input.get("newPassword");
    findUser(request).thenAccept(user -> futureResponse.complete(
      changePassword(user, currentPassword, newPassword)));
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

  @RequestMapping(path = "/settings/account/email/change/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> changeEmail(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var password = (String) input.get("password");
    var newEmail = (String) input.get("newEmail");
    findUser(request).thenAccept(user -> futureResponse.complete(
      changePassword(user, password, newEmail)));
    return futureResponse;
  }

  private Map<String, Object> changeEmail(
    User user, String password, String newEmail
  ) {
    if (!user.passwordHash().equals(hashPassword(password))) {
      return Map.of("success", false);
    }
    userDatabaseTable().changeUserEmail(user.id(), newEmail);
    return Map.of("success", true);
  }

  @RequestMapping(path = "/settings/account/delete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> deleteAccount(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var password = (String) input.get("password");
    findUser(request).thenAccept(user -> futureResponse.complete(
      deleteAccount(user, password)));
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
    userPasswordResetDatabaseTable.deleteResetToken(user.id());
    profilePictureDatabaseTable.deleteProfilePicture(user.id());
    accountController.deleteAllAccounts(user.id());
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
