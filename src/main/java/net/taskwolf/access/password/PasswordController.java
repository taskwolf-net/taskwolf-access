package net.taskwolf.access.password;

import com.google.common.hash.Hashing;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.mail.TaskwolfMail;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserPasswordResetDatabaseTable;
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
public final class PasswordController extends TaskwolfRestController {
  private final TaskwolfMail changeMail;
  private final UserPasswordResetDatabaseTable userPasswordResetDatabaseTable;

  private PasswordController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    @Qualifier("changeMail") TaskwolfMail changeMail,
    UserPasswordResetDatabaseTable userPasswordResetDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.changeMail = changeMail;
    this.userPasswordResetDatabaseTable = userPasswordResetDatabaseTable;
  }

  @RequestMapping(path = "/password/reset/request/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> requestPasswordReset(
    @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var email = (String) input.get("email");
    userDatabaseTable().userExists(email).thenAccept(exists ->
      requestPasswordReset(email, exists).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> requestPasswordReset(
    String email, boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1000));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userDatabaseTable().findUser(email).thenAccept(user ->
      userPasswordResetDatabaseTable.resetTokenExists(user.id()).thenAccept(requestExists ->
        futureResponse.complete(requestPasswordReset(user, requestExists))));
    return futureResponse;
  }

  private static final String PASSWORD_EMAIL_TITLE = "Password Reset";
  private static final String PASSWORD_RESET_URL = "https://taskwolf.net/password/reset/complete/%s/%s/";
  private static final String PASSWORD_EMAIL_BODY = "Hey %s,\n" +
    "\n" +
    "there was a request to change your password!\n" +
    "\n" +
    "If you did not make this request then please ignore this email.\n" +
    "\n" +
    "Otherwise, please click this link to change your password:\n" +
    "\n" +
    "%s";

  private Map<String, Object> requestPasswordReset(User user, boolean requestExists) {
    if (requestExists) {
      return Map.of("success", false, "errorCode", 1001);
    }
    var token = UUID.randomUUID().toString();
    userPasswordResetDatabaseTable.insertResetToken(user.id(), token);
    var body = String.format(PASSWORD_EMAIL_BODY, user.name(),
      String.format(PASSWORD_RESET_URL, user.id().toString(), token));
    changeMail.send(user.email(), PASSWORD_EMAIL_TITLE, body);
    return Map.of("success", true);
  }

  @RequestMapping(path = "/password/reset/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completePasswordReset(
    @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var userId = UUID.fromString((String) input.get("user"));
    var token = (String) input.get("token");
    var newPassword = (String) input.get("newPassword");
    userPasswordResetDatabaseTable.resetTokenExists(userId).thenAccept(exists ->
      completePasswordReset(userId, token, exists, newPassword)
        .thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> completePasswordReset(
    UUID userId, String token, boolean exists, String newPassword
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1000));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userPasswordResetDatabaseTable.findResetToken(userId).thenAccept(actualToken ->
      completePasswordReset(userId, token, actualToken, newPassword)
        .thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> completePasswordReset(
    UUID userId, String token, String actualToken, String newPassword
  ) {
    if (!token.equals(actualToken)) {
      return CompletableFuture.completedFuture(Map.of("success", false, "errorCode", 1001));
    }
    userPasswordResetDatabaseTable.deleteResetToken(userId);
    userDatabaseTable().changeUserPassword(userId, hashPassword(newPassword));
    return CompletableFuture.completedFuture(Map.of("success", true));
  }

  private String hashPassword(String password) {
    return Hashing.sha256().hashString(password, StandardCharsets.UTF_8)
      .toString();
  }
}

