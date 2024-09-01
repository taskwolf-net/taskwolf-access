package net.taskwolf.access.verification;

import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.mail.Mail;
import net.taskwolf.core.notification.NotificationDatabaseTable;
import net.taskwolf.core.recaptcha.RecaptchaConfiguration;
import net.taskwolf.core.tutorial.TutorialDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.user.UserVerificationDatabaseTable;
import net.taskwolf.core.user.activity.ActivityType;
import net.taskwolf.core.user.activity.UserActivityDatabaseTable;
import net.taskwolf.core.worker.WorkerDistribution;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class VerificationRegistrationController {
  private final Key homeKey;
  private final Key productKey;
  private final Key refreshKey;
  private final Mail verificationMail;
  private final UserDatabaseTable userDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final RecaptchaConfiguration recaptchaConfiguration;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final NotificationDatabaseTable notificationDatabaseTable;
  private final WorkerDistribution distribution;
  private final TutorialDatabaseTable tutorialDatabaseTable;
  private final UserActivityDatabaseTable activityDatabaseTable;

  private VerificationRegistrationController(
    @Qualifier("homeKey") Key homeKey, @Qualifier("productKey") Key productKey,
    @Qualifier("productKey") Key refreshKey,
    @Qualifier("verificationMail") Mail verificationMail,
    UserDatabaseTable userDatabaseTable,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    RecaptchaConfiguration recaptchaConfiguration,
    UserTargetDatabaseTable userTargetDatabaseTable,
    NotificationDatabaseTable notificationDatabaseTable,
    WorkerDistribution distribution, TutorialDatabaseTable tutorialDatabaseTable,
    UserActivityDatabaseTable activityDatabaseTable
  ) {
    this.homeKey = homeKey;
    this.productKey = productKey;
    this.refreshKey = refreshKey;
    this.verificationMail = verificationMail;
    this.userDatabaseTable = userDatabaseTable;
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
    this.recaptchaConfiguration = recaptchaConfiguration;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.notificationDatabaseTable = notificationDatabaseTable;
    this.distribution = distribution;
    this.tutorialDatabaseTable = tutorialDatabaseTable;
    this.activityDatabaseTable = activityDatabaseTable;
  }

  @RequestMapping(path = "/verification/register/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> register(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var email = body.getString("email");
    userDatabaseTable.userExists(email)
      .thenAccept(exists -> checkRecaptcha(body.getString("recaptchaToken"))
        .thenAccept(recaptchaVerified -> completeRegistration(futureResponse,
          exists, recaptchaVerified, email, body.getString("name"),
          body.getString("password"))));
    return futureResponse;
  }

  private static final String RECAPTCHA_URL = "https://www.google.com/recaptcha/api/siteverify?secret=%s&response=%s";

  private CompletableFuture<Boolean> checkRecaptcha(String token) {
    var url = URI.create(String.format(RECAPTCHA_URL,
      recaptchaConfiguration.secretKey(), token));
    var requestBuilder = HttpRequest.newBuilder().uri(url)
      .POST(HttpRequest.BodyPublishers.noBody())
      .build();
    return HttpClient.newHttpClient()
      .sendAsync(requestBuilder, HttpResponse.BodyHandlers.ofString())
      .thenApply(response -> new JSONObject(response.body()).getBoolean("success"));
  }

  private void completeRegistration(
    CompletableFuture<Map<String, Object>> futureResponse, boolean alreadyExists,
    boolean recaptchaVerified, String email, String name, String password
  ) {
    if (alreadyExists) {
      futureResponse.complete(Map.of("success", false, "error", 1000));
      return;
    }
    if (!recaptchaVerified) {
      futureResponse.complete(Map.of("success", false, "error", 1001));
      return;
    }
    userDatabaseTable.generateAvailableUserId().thenAccept(id ->
      insertNewUser(id, name, email, hashPassword(password)));
    futureResponse.complete(Map.of("success", true));
  }

  private static final String VERIFICATIION_EMAIL_TITLE = "Verification";
  private static final String VERIFICATION_URL = "https://taskwolf.net/register/confirm/%s/%s/";
  private static final String VERIFICATION_EMAIL_BODY = "Hey %s,\n" +
    "\n" +
    "we’re excited to welcome you to Taskwolf! Before you begin your " +
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
    var token = UUID.randomUUID().toString();
    notificationDatabaseTable.insertNotificationSettings(userId, true, true);
    userVerificationDatabaseTable.insertVerification(userId, token);
    var body = String.format(VERIFICATION_EMAIL_BODY, name,
      String.format(VERIFICATION_URL, userId.toString(), token));
    verificationMail.send(email, VERIFICATIION_EMAIL_TITLE, body);
    distribution.addUser(userId);
    tutorialDatabaseTable.insertTutorial(userId, 0, 0);
  }

  @RequestMapping(path = "/verification/email/resend/", method = RequestMethod.POST)
  public void resendEmail(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var email = body.getString("email").replace(" ", "");
    userDatabaseTable.userExists(email).thenAccept(userExists ->
      resendEmailCheckUser(email, userExists));
  }

  private void resendEmailCheckUser(String email, boolean userExists) {
    if (!userExists) {
      return;
    }
    userDatabaseTable.findUser(email).thenAccept(user ->
      userVerificationDatabaseTable.verificationExists(user.id())
        .thenAccept(verificationExists ->
          resendEmailCheckRegistration(user, verificationExists)));
  }

  private void resendEmailCheckRegistration(User user, boolean verificationExists) {
    if (!verificationExists) {
      return;
    }
    userVerificationDatabaseTable.findVerification(user.id()).thenAccept(token ->
      verificationMail.send(user.email(), VERIFICATIION_EMAIL_TITLE,
        String.format(VERIFICATION_EMAIL_BODY, user.name(),
          String.format(VERIFICATION_URL, user.id().toString(), token))));
  }

  @RequestMapping(path = "/verification/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> complete(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var userId = body.getUUID("user");
    return userVerificationDatabaseTable.verificationExists(userId)
      .thenCompose(exists -> complete(userId, body.getString("token"), exists));
  }

  private CompletableFuture<Map<String, Object>> complete(
    UUID userId, String submittedToken, boolean tokenExists
  ) {
    if (!tokenExists) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return userVerificationDatabaseTable.findVerification(userId)
      .thenCompose(originalToken -> complete(userId, submittedToken, originalToken));
  }

  private CompletableFuture<Map<String, Object>> complete(
    UUID userId, String submittedToken, String originalToken
  ) {
    if (!submittedToken.equals(originalToken)) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    userVerificationDatabaseTable.deleteVerification(userId);
    activityDatabaseTable.insertActivity(userId, "activity.setting.registration.title",
      "activity.setting.registration.description", ActivityType.SETTING);
    var apiKey = Verification.create(userDatabaseTable, homeKey, productKey,
      refreshKey, "", "").generateHomeApiKey(userId);
    return userDatabaseTable.findUser(userId).thenApply(user ->
      Map.of("success", true, "homeApiKey", apiKey));
  }

  private String hashPassword(String password) {
    return Hashing.sha256().hashString(password, StandardCharsets.UTF_8)
      .toString();
  }
}

