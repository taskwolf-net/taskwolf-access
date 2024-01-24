package net.taskwolf.access.verification;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.distribution.Distribution;
import net.taskwolf.core.mail.TaskwolfMail;
import net.taskwolf.core.notification.NotificationDatabaseTable;
import net.taskwolf.core.user.*;
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
public final class VerificationController {
  private final Key secretKey;
  private final TaskwolfMail verificationMail;
  private final UserDatabaseTable userDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final ProfilePictureDatabaseTable profilePictureDatabaseTable;
  private final String defaultProfilePicture;
  private final NotificationDatabaseTable notificationDatabaseTable;
  private final Distribution distribution;

  private VerificationController(
    Key secretKey, @Qualifier("verificationMail") TaskwolfMail verificationMail,
    UserDatabaseTable userDatabaseTable,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    ProfilePictureDatabaseTable profilePictureDatabaseTable,
    @Qualifier("defaultProfilePicture") String defaultProfilePicture,
    NotificationDatabaseTable notificationDatabaseTable, Distribution distribution
  ) {
    this.secretKey = secretKey;
    this.verificationMail = verificationMail;
    this.userDatabaseTable = userDatabaseTable;
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.profilePictureDatabaseTable = profilePictureDatabaseTable;
    this.defaultProfilePicture = defaultProfilePicture;
    this.notificationDatabaseTable = notificationDatabaseTable;
    this.distribution = distribution;
  }

  @RequestMapping(path = "/verification/register/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> register(
    @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var email = (String) input.get("email");
    var name = (String) input.get("name");
    var password = (String) input.get("password");
    userDatabaseTable.userExists(email).thenAccept(exists ->
      completeRegistration(futureResponse, exists, email, name, password));
    return futureResponse;
  }

  private void completeRegistration(
    CompletableFuture<Map<String, Object>> futureResponse, boolean alreadyExists,
    String email, String name, String password
  ) {
    var response = Maps.<String, Object>newHashMap();
    if (alreadyExists) {
      response.put("success", false);
      futureResponse.complete(response);
      return;
    }
    userDatabaseTable.generateAvailableUserId().thenAccept(id ->
      insertNewUser(id, name, email, hashPassword(password)));
    response.put("success", true);
    futureResponse.complete(response);
  }

  private static final String VERIFICATIION_EMAIL_TITLE = "Verification";
  private static final String VERIFICATION_URL = "https://taskwolf.net/register/confirm/%s/%s/";
  private static final String VERIFICATION_EMAIL_BODY = "Hey %s,\n" +
    "\n" +
    "We’re excited to welcome you to Taskwolf! Before you begin your " +
    "journey, we need to verify your account. Follow these steps to complete " +
    "the verification process:\n" +
    "\n" +
    "Click the link below to verify your account:\n" +
    "%s\n" +
    "\n" +
    "After verification, you’ll have access to all the amazing features on Taskwolf.\n" +
    "\n" +
    "If you encounter any issues or have questions, our support team is here to help. Simply reply to this email or reach out to us at support@taskwolf.net\n" +
    "\n" +
    "Welcome aboard!";

  private void insertNewUser(
    UUID userId, String name, String email, String passwordHash
  ) {
    userDatabaseTable.insertUser(userId, name, email, passwordHash, "en",
      Lists.newArrayList());
    userTargetDatabaseTable.insertTarget(userId, userId);
    profilePictureDatabaseTable.insertProfilePicture(userId, defaultProfilePicture);
    var token = UUID.randomUUID().toString();
    notificationDatabaseTable.insertNotificationSettings(userId, true, true);
    userVerificationDatabaseTable.insertVerification(userId, token);
    var body = String.format(VERIFICATION_EMAIL_BODY, name,
      String.format(VERIFICATION_URL, userId.toString(), token));
    verificationMail.send(email, VERIFICATIION_EMAIL_TITLE, body);
    distribution.addNewUser(userId);
  }

  @RequestMapping(path = "/verification/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> complete(
    @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var userId = UUID.fromString((String) input.get("user"));
    var token = (String) input.get("token");
    userVerificationDatabaseTable.verificationExists(userId).thenApply(exists ->
      complete(userId, token, exists));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> complete(
    UUID userId, String submittedToken, boolean tokenExists
  ) {
    if (!tokenExists) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userVerificationDatabaseTable.findVerification(userId)
      .thenApply(originalToken -> complete(userId, submittedToken, originalToken));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> complete(
    UUID userId, String submittedToken, String originalToken
  ) {
    if (!submittedToken.equals(originalToken)) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    userVerificationDatabaseTable.deleteVerification(userId);
    return CompletableFuture.completedFuture(Map.of("success", true));
  }

  @RequestMapping(path = "/verification/login/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> login(
    @RequestBody Map<String, Object> input, HttpServletResponse servletResponse
  ) {
    var verification = Verification.create(userDatabaseTable, secretKey,
      (String) input.get("email"), hashPassword((String) input.get("password")));
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    verification.isAuthenticated().thenAccept(isAuthenticated ->
      checkAuthorization(servletResponse, verification, futureResponse, isAuthenticated));
    return futureResponse;
  }

  private void checkAuthorization(
    HttpServletResponse servletResponse, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse, boolean isAuthenticated
  ) {
    if (!isAuthenticated) {
      servletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      futureResponse.complete(Maps.newHashMap());
      return;
    }
    userDatabaseTable.findUser(verification.email()).thenAccept(user ->
      userVerificationDatabaseTable.verificationExists(user.id())
        .thenAccept(completionPending -> completeLogin(servletResponse, verification,
          futureResponse, user, completionPending)));
  }

  private void completeLogin(
    HttpServletResponse servletResponse, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse, User user,
    boolean completionPending
  ) {
    if (completionPending) {
      servletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      futureResponse.complete(Maps.newHashMap());
      return;
    }
    futureResponse.complete(Map.of("apiKey", verification.generateApiKey(user.id())));
  }

  @RequestMapping(path = "/verification/isValid/", method = RequestMethod.POST)
  public Map<String, Object> isValid(@RequestBody Map<String, Object> input) {
    var response = Maps.<String, Object>newHashMap();
    try {
      Jwts.parser()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws((String) input.get("token"));
      response.put("isValid", "true");
    } catch (Exception exception) {
      response.put("isValid", "false");
    }
    return response;
  }

  private String hashPassword(String password) {
    return Hashing.sha256().hashString(password, StandardCharsets.UTF_8)
      .toString();
  }
}
