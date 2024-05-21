package net.taskwolf.access.verification;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.grafana.GrafanaUserFactory;
import net.taskwolf.core.mail.TaskwolfMail;
import net.taskwolf.core.notification.NotificationDatabaseTable;
import net.taskwolf.core.tutorial.TutorialDatabaseTable;
import net.taskwolf.core.user.*;
import net.taskwolf.core.worker.WorkerDistribution;
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
  private final WorkerDistribution distribution;
  private final GrafanaUserFactory grafanaUserFactory;
  private final TutorialDatabaseTable tutorialDatabaseTable;

  private VerificationController(
    Key secretKey, @Qualifier("verificationMail") TaskwolfMail verificationMail,
    UserDatabaseTable userDatabaseTable,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    ProfilePictureDatabaseTable profilePictureDatabaseTable,
    @Qualifier("defaultProfilePicture") String defaultProfilePicture,
    NotificationDatabaseTable notificationDatabaseTable,
    WorkerDistribution distribution, GrafanaUserFactory grafanaUserFactory,
    TutorialDatabaseTable tutorialDatabaseTable
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
    this.grafanaUserFactory = grafanaUserFactory;
    this.tutorialDatabaseTable = tutorialDatabaseTable;
  }

  @RequestMapping(path = "/verification/register/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> register(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var email = body.getString("email");
    userDatabaseTable.userExists(email).thenAccept(exists ->
      completeRegistration(futureResponse, exists, email, body.getString("name"),
        body.getString("password")));
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
    distribution.addUser(userId);
    grafanaUserFactory.createUser(userId).create("");
    tutorialDatabaseTable.insertTutorial(userId, 0, 0);
  }

  @RequestMapping(path = "/verification/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> complete(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var userId = body.getUUID("user");
    userVerificationDatabaseTable.verificationExists(userId).thenApply(exists ->
      complete(userId, body.getString("token"), exists));
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
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var verification = Verification.create(userDatabaseTable, secretKey,
      body.getString("email").replace(" ", ""),
      hashPassword(body.getString("password")));
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    verification.isAuthenticated().thenAccept(isAuthenticated ->
      checkAuthorization(response, verification, futureResponse, isAuthenticated));
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
    var apiKey = verification.generateApiKey(user.id());
    grafanaUserFactory.createUser(user.id()).updateApiKey(apiKey);
    futureResponse.complete(Map.of("apiKey", apiKey));
  }

  @RequestMapping(path = "/verification/isValid/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> isValid(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    try {
      var userId = UUID.fromString(Jwts.parser()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws(body.getString("token"))
        .getPayload().get("id", String.class));
      return userDatabaseTable.userExists(userId).thenApply(exists ->
        Map.of("isValid", exists ? "true" : "false"));
    } catch (Exception exception) {
      return CompletableFuture.completedFuture(Map.of("isValid", "false"));
    }
  }

  private String hashPassword(String password) {
    return Hashing.sha256().hashString(password, StandardCharsets.UTF_8)
      .toString();
  }
}
