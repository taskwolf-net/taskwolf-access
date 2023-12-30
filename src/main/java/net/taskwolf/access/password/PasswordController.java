package net.taskwolf.access.password;

import com.google.common.hash.Hashing;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
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
  private final String verificationMailHost;
  private final String verificationMail;
  private final String verificationMailPassword;
  private final UserPasswordResetDatabaseTable userPasswordResetDatabaseTable;

  private PasswordController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    @Qualifier("verificationMailHost") String verificationMailHost,
    @Qualifier("verificationMail") String verificationMail,
    @Qualifier("verificationMailPassword") String verificationMailPassword,
    UserPasswordResetDatabaseTable userPasswordResetDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.verificationMailHost = verificationMailHost;
    this.verificationMail = verificationMail;
    this.verificationMailPassword = verificationMailPassword;
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

  private Map<String, Object> requestPasswordReset(User user, boolean requestExists) {
    if (requestExists) {
      return Map.of("success", false, "errorCode", 1001);
    }
    var token = UUID.randomUUID().toString();
    userPasswordResetDatabaseTable.insertResetToken(user.id(), token);
    PasswordResetMail.create(verificationMailHost, verificationMail,
      verificationMailPassword, user.email(), user.name(), user.id(), token).send();
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

  @RequestMapping(path = "/password/change/", method = RequestMethod.POST)
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
    userDatabaseTable().changeUserPassword(user.id(), newPassword);
    return Map.of("success", true);
  }

  private String hashPassword(String password) {
    return Hashing.sha256().hashString(password, StandardCharsets.UTF_8)
      .toString();
  }
}

