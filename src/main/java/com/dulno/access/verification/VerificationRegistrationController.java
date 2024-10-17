package com.dulno.access.verification;

import com.dulno.core.session.SessionDatabaseTable;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.maxmind.geoip2.DatabaseReader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.notification.NotificationDatabaseTable;
import com.dulno.core.recaptcha.RecaptchaConfiguration;
import com.dulno.core.tutorial.TutorialDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
import com.dulno.core.user.UserVerificationDatabaseTable;
import com.dulno.core.user.activity.ActivityType;
import com.dulno.core.user.activity.UserActivityDatabaseTable;
import com.dulno.core.worker.WorkerDistribution;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
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
  private final Translation translation;
  private final UserDatabaseTable userDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final RecaptchaConfiguration recaptchaConfiguration;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final NotificationDatabaseTable notificationDatabaseTable;
  private final WorkerDistribution distribution;
  private final TutorialDatabaseTable tutorialDatabaseTable;
  private final UserActivityDatabaseTable activityDatabaseTable;
  private final DatabaseReader geoDatabaseReader;
  private final SessionDatabaseTable sessionDatabaseTable;
  private final VerificationLoginController loginController;

  private VerificationRegistrationController(
    @Qualifier("homeKey") Key homeKey, @Qualifier("productKey") Key productKey,
    @Qualifier("productKey") Key refreshKey,
    @Qualifier("verificationMail") Mail verificationMail, Translation translation,
    UserDatabaseTable userDatabaseTable,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    RecaptchaConfiguration recaptchaConfiguration,
    UserTargetDatabaseTable userTargetDatabaseTable,
    NotificationDatabaseTable notificationDatabaseTable,
    WorkerDistribution distribution, TutorialDatabaseTable tutorialDatabaseTable,
    UserActivityDatabaseTable activityDatabaseTable,
    DatabaseReader geoDatabaseReader, SessionDatabaseTable sessionDatabaseTable,
    VerificationLoginController loginController
  ) {
    this.homeKey = homeKey;
    this.productKey = productKey;
    this.refreshKey = refreshKey;
    this.verificationMail = verificationMail;
    this.translation = translation;
    this.userDatabaseTable = userDatabaseTable;
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
    this.recaptchaConfiguration = recaptchaConfiguration;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.notificationDatabaseTable = notificationDatabaseTable;
    this.distribution = distribution;
    this.tutorialDatabaseTable = tutorialDatabaseTable;
    this.activityDatabaseTable = activityDatabaseTable;
    this.geoDatabaseReader = geoDatabaseReader;
    this.sessionDatabaseTable = sessionDatabaseTable;
    this.loginController = loginController;
  }

  @RequestMapping(path = "/verification/register/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> register(
    HttpServletRequest request,  @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var legalAccepted = body.getBoolean("legalAccepted");
    if (!legalAccepted) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    var ipAddress = request.getHeader("X-Real-IP");
    var email = body.getString("email");
    userDatabaseTable.userExists(email)
      .thenAccept(exists -> checkRecaptcha(body.getString("recaptchaToken"))
        .thenAccept(recaptchaVerified -> completeRegistration(futureResponse,
          exists, recaptchaVerified, email, body.getString("name"),
          body.getString("password"), body.getString("redirect"), ipAddress,
          legalAccepted, body.getBoolean("newsletter"))));
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
    boolean recaptchaVerified, String email, String name, String password,
    String redirect, String ipAddress, boolean legalAccepted, boolean newsletter
  ) {
    if (alreadyExists) {
      futureResponse.complete(Map.of("success", false, "error", 1000));
      return;
    }
    if (!recaptchaVerified) {
      futureResponse.complete(Map.of("success", false, "error", 1001));
      return;
    }
    createUser(name, email, hashPassword(password), redirect, ipAddress,
      legalAccepted, newsletter, true);
    futureResponse.complete(Map.of("success", true));
  }

  public CompletableFuture<User> createUser(
    String name, String email, String passwordHash, String redirect,
    String ipAddress, boolean legalAccepted, boolean newsletter,
    boolean verificationRequired
  ) {
    return userDatabaseTable.generateAvailableUserId().thenCompose(id ->
      insertNewUser(id, name, email, passwordHash, redirect, ipAddress,
        legalAccepted, newsletter, verificationRequired));
  }

  private static final String VERIFICATION_URL = "https://dulno.com/register/confirm/%s/%s/";

  private CompletableFuture<User> insertNewUser(
    UUID userId, String name, String email, String passwordHash, String redirect,
    String ipAddress, boolean legalAccepted, boolean newsletter,
    boolean verificationRequired
  ) {
    var language = findUserLanguage(ipAddress);
    var token = UUID.randomUUID().toString();
    notificationDatabaseTable.insertNotificationSettings(userId, true, true);
    activityDatabaseTable.insertActivity(userId, "activity.setting.registration.title",
      "activity.setting.registration.description", ActivityType.SETTING);
    if (verificationRequired) {
      userVerificationDatabaseTable.insertVerification(userId, token);
    }
    var title = translation.translate(language, "registration.email.title");
    var verificationUrl = String.format(VERIFICATION_URL, userId.toString(), token);
    if (!redirect.isEmpty()) {
      verificationUrl += "?redirect=" + redirect;
    }
    var body = String.format(
      translation.translate(language, "registration.email.body"), name,
      verificationUrl);
    verificationMail.send(email, title, body);
    distribution.addUser(userId);
    tutorialDatabaseTable.insertTutorial(userId, 0, 0);
    var user = User.create(userId, name, email, passwordHash, language,
      Lists.newArrayList(), legalAccepted, newsletter);
    return  userTargetDatabaseTable.insertTarget(userId, userId)
      .thenCompose(targetValue -> userDatabaseTable.insertUser(user)
        .thenApply(userValue -> user));
  }

  private String findUserLanguage(String ipAddress) {
    try {
      var location = geoDatabaseReader.city(InetAddress.getByName(ipAddress));
      if (location.getCountry().getIsoCode().equalsIgnoreCase("de")) {
        return "de";
      }
      return "en";
    } catch (Exception exception) {
      exception.printStackTrace();
      return "en";
    }
  }

  @RequestMapping(path = "/verification/email/resend/", method = RequestMethod.POST)
  public void resendEmail(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
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
    var title = translation.translate(user, "registration.email.title");
    userVerificationDatabaseTable.findVerification(user.id())
      .thenApply(token -> String.format(
        translation.translate(user, "registration.email.body"), user.name(),
        String.format(VERIFICATION_URL, user.id().toString(), token)))
      .thenAccept(body -> verificationMail.send(user.email(), title, body));
  }

  @RequestMapping(path = "/verification/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> complete(
    HttpServletRequest request, @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var userId = body.getUUID("user");
    return userVerificationDatabaseTable.verificationExists(userId)
      .thenCompose(exists -> complete(request, userId, body.getString("token"),
        exists));
  }

  private CompletableFuture<Map<String, Object>> complete(
    HttpServletRequest request, UUID userId, String submittedToken,
    boolean tokenExists
  ) {
    if (!tokenExists) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return userVerificationDatabaseTable.findVerification(userId)
      .thenCompose(originalToken -> complete(request, userId, submittedToken,
        originalToken));
  }

  private CompletableFuture<Map<String, Object>> complete(
    HttpServletRequest request, UUID userId, String submittedToken,
    String originalToken
  ) {
    if (!submittedToken.equals(originalToken)) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return sessionDatabaseTable.generateAvailableSessionId()
      .thenCompose(sessionId -> complete(request, userId, sessionId));
  }

  private CompletableFuture<Map<String, Object>> complete(
    HttpServletRequest request, UUID userId, UUID sessionId
  ) {
    userVerificationDatabaseTable.deleteVerification(userId);
    var apiKey = Verification.create(userDatabaseTable, homeKey, productKey,
      refreshKey, "", "").generateHomeApiKey(userId, sessionId);
    loginController.storeSession(request, userId, sessionId, "");
    return userDatabaseTable.findUser(userId).thenApply(user ->
      Map.of("success", true, "homeApiKey", apiKey, "userName", user.name()));
  }

  private String hashPassword(String password) {
    return Hashing.sha256().hashString(password, StandardCharsets.UTF_8)
      .toString();
  }
}

