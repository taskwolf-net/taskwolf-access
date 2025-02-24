package com.dulno.access.password;

import com.dulno.core.hashing.Hashing;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserPasswordResetDatabaseTable;
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
public final class PasswordController extends DulnoRestController {
  private final Mail changeMail;
  private final UserPasswordResetDatabaseTable userPasswordResetDatabaseTable;
  private final Translation translation;
  private final Hashing hashing;

  private PasswordController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    @Qualifier("changeMail") Mail changeMail,
    UserPasswordResetDatabaseTable userPasswordResetDatabaseTable,
    Translation translation, Hashing hashing
  ) {
    super(secretKey, userDatabaseTable);
    this.changeMail = changeMail;
    this.userPasswordResetDatabaseTable = userPasswordResetDatabaseTable;
    this.translation = translation;
    this.hashing = hashing;
  }

  @RequestMapping(path = "/password/reset/request/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> requestPasswordReset(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var email = body.getString("email");
    userDatabaseTable().userExists(email).thenAccept(exists ->
      requestPasswordReset(email, exists).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> requestPasswordReset(
    String email, boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "errorCode", 1000));
    }
    return userDatabaseTable().findUser(email)
      .thenCompose(user -> requestPasswordReset(user,
        "password.reset.email.title", "password.reset.email.body"));
  }

  public CompletableFuture<Map<String, Object>> requestPasswordReset(
    User user, String emailTitle, String emailBody
  ) {
    return userPasswordResetDatabaseTable.resetTokenExists(user.id())
      .thenCompose(requestExists -> requestPasswordReset(user, emailTitle,
        emailBody, requestExists));
  }

  private CompletableFuture<Map<String, Object>> requestPasswordReset(
    User user, String emailTitle, String emailBody, boolean requestExists
  ) {
    if (requestExists) {
      return userPasswordResetDatabaseTable.findResetToken(user.id())
        .thenApply(token -> sendPasswordResetMail(user, token, emailTitle,
          emailBody));
    }
    var token = UUID.randomUUID().toString();
    userPasswordResetDatabaseTable.insertResetToken(user.id(), token);
    return CompletableFuture.completedFuture(sendPasswordResetMail(user, token,
      emailTitle, emailBody));
  }

  private static final String PASSWORD_RESET_URL = "https://dulno.com/password/reset/complete/%s/%s/";

  private Map<String, Object> sendPasswordResetMail(
    User user, String token, String emailTitle, String emailBody
  ) {
    var title = translation.translate(user, emailTitle);
    var body = String.format(translation.translate(user, emailBody), user.name(),
      String.format(PASSWORD_RESET_URL, user.id().toString(), token));
    changeMail.send(user.email(), title, body);
    return Map.of("success", true);
  }

  @RequestMapping(path = "/password/reset/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completePasswordReset(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var userId = body.getUUID("user");
    userPasswordResetDatabaseTable.resetTokenExists(userId).thenAccept(exists ->
      completePasswordReset(userId, body.getString("token"), exists,
        body.getString("newPassword")).thenAccept(futureResponse::complete));
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
    userDatabaseTable().changeUserPassword(userId, hashing.hash(newPassword));
    return CompletableFuture.completedFuture(Map.of("success", true));
  }
}

