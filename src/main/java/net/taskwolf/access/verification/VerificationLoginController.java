package net.taskwolf.access.verification;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.maxmind.geoip2.DatabaseReader;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.bundle.Bundle;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.session.SessionDatabaseTable;
import net.taskwolf.core.session.SessionStatus;
import net.taskwolf.core.session.UserAgent;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserVerificationDatabaseTable;
import net.taskwolf.core.user.mfa.MultiFactorAuthFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class VerificationLoginController {
  private final Key homeKey;
  private final Key productKey;
  private final Key refreshKey;
  private final UserDatabaseTable userDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final MultiFactorAuthFactory multiFactorAuthFactory;
  private final SessionDatabaseTable sessionDatabaseTable;
  private final DatabaseReader geoDatabaseReader;

  private VerificationLoginController(
    @Qualifier("homeKey") Key homeKey, @Qualifier("productKey") Key productKey,
    @Qualifier("refreshKey") Key refreshKey, UserDatabaseTable userDatabaseTable,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable,
    MultiFactorAuthFactory multiFactorAuthFactory,
    SessionDatabaseTable sessionDatabaseTable, DatabaseReader geoDatabaseReader
  ) {
    this.homeKey = homeKey;
    this.productKey = productKey;
    this.refreshKey = refreshKey;
    this.userDatabaseTable = userDatabaseTable;
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.multiFactorAuthFactory = multiFactorAuthFactory;
    this.sessionDatabaseTable = sessionDatabaseTable;
    this.geoDatabaseReader = geoDatabaseReader;
  }

  @RequestMapping(path = "/verification/login/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> login(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) throws Exception {
    var body = TaskwolfRequestBody.of(payload, response);
    var loginFuture = login(request, body.getString("email"),
      body.getString("password"), body.getString("multiFactorCode"));
    loginFuture.thenAccept(result -> applyLoginResponseStatus(response, result));
    return loginFuture;
  }

  private void applyLoginResponseStatus(
    HttpServletResponse response, Map<String, Object> result
  ) {
    if ((boolean) result.get("success")) {
      response.setStatus(HttpServletResponse.SC_OK);
      return;
    }
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  private CompletableFuture<Map<String, Object>> login(
    HttpServletRequest request, String email, String password,
    String multiFactorCode
  ) {
    var verification = Verification.create(userDatabaseTable, homeKey, productKey,
      refreshKey, email.replace(" ", ""), hashPassword(password));
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    verification.isAuthenticated().thenAccept(isAuthenticated ->
      checkAuthorization(request, verification, multiFactorCode, futureResponse,
        isAuthenticated));
    return futureResponse;
  }

  private void checkAuthorization(
    HttpServletRequest request, Verification verification, String multiFactorCode,
    CompletableFuture<Map<String, Object>> futureResponse, boolean isAuthenticated
  ) {
    if (!isAuthenticated) {
      futureResponse.complete(Map.of("success", false, "error", 1000));
      return;
    }
    userDatabaseTable.findUser(verification.email()).thenAccept(user ->
      multiFactorAuthFactory.createAuth(user.id()).verifyCode(multiFactorCode)
        .thenAccept(verified -> checkMultiFactorAuth(request, user, verification,
          futureResponse, verified)));
  }

  private void checkMultiFactorAuth(
    HttpServletRequest request, User user, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse,
    boolean multiFactorVerified
  ) {
    if (!multiFactorVerified) {
      futureResponse.complete(Map.of("success", false, "error", 1002));
      return;
    }
    processAuthorizedLogin(request, user, verification, futureResponse);
  }

  public void processAuthorizedLogin(
    HttpServletRequest request, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse
  ) {
    userDatabaseTable.findUser(verification.email()).thenAccept(user ->
      processAuthorizedLogin(request, user, verification, futureResponse));
  }

  public void processAuthorizedLogin(
    HttpServletRequest request, User user, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse
  ) {
    userVerificationDatabaseTable.verificationExists(user.id())
      .thenAccept(completionPending -> checkBundle(request, verification,
        futureResponse, user, completionPending));
  }

  private void checkBundle(
    HttpServletRequest request, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse,
    User user, boolean completionPending
  ) {
    if (completionPending) {
      futureResponse.complete(Map.of("success", false, "error", 1001));
      return;
    }
    bundleDatabaseTable.bundleExists(user.id()).thenAccept(hasPersonalBundle ->
      findLatestBundleExpiration(user, hasPersonalBundle).thenAccept(
        latestExpiration -> completeLogin(request, verification, futureResponse,
          user, latestExpiration > System.currentTimeMillis(), latestExpiration)));
  }

  private CompletableFuture<Long> findLatestBundleExpiration(
    User user, boolean hasPersonalBundle
  ) {
    if (!hasPersonalBundle && user.organizations().isEmpty()) {
      return CompletableFuture.completedFuture(-10L);
    }
    var targets = Lists.newArrayList(user.organizations());
    if (hasPersonalBundle) {
      targets.add(user.id());
    }
    return AsyncIterator.execute(targets, bundleDatabaseTable::findBundle)
      .thenApply(bundles -> bundles.stream().map(Bundle::expiration)
        .sorted(Comparator.reverseOrder()).findFirst().get());
  }

  private void completeLogin(
    HttpServletRequest request, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse,
    User user, boolean bundleEnabled, long expiration
  ) {
    var homeApiKey = verification.generateHomeApiKey(user.id());
    if (expiration == -10) {
      futureResponse.complete(Map.of("success", false, "error", 1003,
        "homeApiKey", homeApiKey));
      return;
    }
    if (!bundleEnabled) {
      futureResponse.complete(Map.of("success", false, "error", 1004,
        "homeApiKey", homeApiKey));
      return;
    }
    var productApiKey = verification.generateProductApiKey(user.id(), expiration);
    var refreshToken = verification.generateRefreshToken(user.id(), expiration);
    storeSession(request, user, refreshToken);
    futureResponse.complete(Map.of("success", true, "productApiKey", productApiKey,
      "homeApiKey", homeApiKey));
  }

  private void storeSession(
    HttpServletRequest request, User user, String refreshToken
  ) {
    try {
      var ipAddress = request.getHeader("X-Real-IP");
      var location = geoDatabaseReader.city(InetAddress.getByName(ipAddress));
      var platform = UserAgent.create(request.getHeader("User-Agent"))
        .findPlatform();
      sessionDatabaseTable.generateAvailableSessionId().thenAccept(id ->
        sessionDatabaseTable.insertSession(id, user.id(), platform, ipAddress,
          location.getCountry().getName(), location.getCity().getName(),
          System.currentTimeMillis(), refreshToken, SessionStatus.ACTIVE));
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  @RequestMapping(path = "/verification/refresh/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> refreshVerification(
    @RequestBody String payload, HttpServletResponse response
  ) {
    return CompletableFuture.completedFuture(Maps.newHashMap());
  }

  @RequestMapping(path = "/verification/logout/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> logout(
    @RequestBody String payload, HttpServletResponse response
  ) {
    return CompletableFuture.completedFuture(Maps.newHashMap());
  }

  @RequestMapping(path = "/verification/isValid/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> isValid(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    try {
      var userId = UUID.fromString(Jwts.parser()
        .setSigningKey(productKey)
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

  @RequestMapping(path = "/email/exists/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> emailExists(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    return userDatabaseTable.userExists(body.getString("email"))
      .thenApply(exists -> Map.of("exists", exists));
  }
}
