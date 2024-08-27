package net.taskwolf.access.verification;

import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.maxmind.geoip2.DatabaseReader;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.bundle.Bundle;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.session.Session;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
public final class VerificationLoginController extends TaskwolfRestController {
  private final Key homeKey;
  private final Key productKey;
  private final Key refreshKey;
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
    super(productKey, userDatabaseTable);
    this.homeKey = homeKey;
    this.productKey = productKey;
    this.refreshKey = refreshKey;
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
    var verification = Verification.create(userDatabaseTable(), homeKey, productKey,
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
    userDatabaseTable().findUser(verification.email()).thenAccept(user ->
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
    userDatabaseTable().findUser(verification.email()).thenAccept(user ->
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
        latestExpiration -> checkBundle(request, verification, futureResponse,
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

  private void checkBundle(
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
    sessionDatabaseTable.generateAvailableSessionId().thenAccept(id ->
      completeLogin(request, verification, futureResponse, user, expiration,
        homeApiKey, id));
  }

  private void completeLogin(
    HttpServletRequest request, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse,
    User user, long expiration, String homeApiKey, UUID sessionId
  ) {
    var productApiKey = verification.generateProductApiKey(user.id(),
      sessionId, expiration);
    var refreshToken = verification.generateRefreshToken(user.id(),
      sessionId, expiration);
    storeSession(request, user, sessionId, refreshToken);
    futureResponse.complete(Map.of("success", true, "productApiKey", productApiKey,
      "homeApiKey", homeApiKey, "refreshToken", refreshToken));
  }

  private void storeSession(
    HttpServletRequest request, User user, UUID sessionId, String refreshToken
  ) {
    try {
      var ipAddress = request.getHeader("X-Real-IP");
      var location = geoDatabaseReader.city(InetAddress.getByName(ipAddress));
      var platform = UserAgent.create(request.getHeader("User-Agent"))
        .findPlatform();
      sessionDatabaseTable.insertSession(sessionId, user.id(), platform,
        ipAddress, location.getCountry().getName(), location.getCity().getName(),
        System.currentTimeMillis(), refreshToken, SessionStatus.ACTIVE);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  @RequestMapping(path = "/verification/refresh/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> refreshVerification(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var refreshToken = body.getString("refreshToken");
    var result = verifyToken(productKey, refreshToken);
    if (result.getKey() != HttpServletResponse.SC_ACCEPTED) {
      return CompletableFuture.completedFuture(Map.of("success", "false"));
    }
    var userId = UUID.fromString(result.getValue().get("id", String.class));
    var sessionId = UUID.fromString(result.getValue().get("session", String.class));
    return userDatabaseTable().userExists(userId)
      .thenCompose(exists -> refreshVerification(refreshToken, userId,
        sessionId, exists));
  }

  private CompletableFuture<Map<String, Object>> refreshVerification(
    String refreshToken, UUID userId, UUID sessionId, boolean userExists
  ) {
    if (!userExists) {
      return CompletableFuture.completedFuture(Map.of("success", "false"));
    }
    return userDatabaseTable().findUser(userId)
      .thenCompose(user -> sessionDatabaseTable.findSession(sessionId)
        .thenCompose(session -> bundleDatabaseTable.bundleExists(user.id())
          .thenCompose(hasPersonalBundle ->
            findLatestBundleExpiration(user, hasPersonalBundle)
              .thenApply(latestExpiration -> refreshVerification(refreshToken,
                user, session, latestExpiration > System.currentTimeMillis(),
                latestExpiration)))));
  }

  private Map<String, Object> refreshVerification(
    String refreshToken, User user, Session session, boolean bundleEnabled,
    long expiration
  ) {
    if (expiration == -10 || !bundleEnabled) {
      return Map.of("success", "false");
    }
    if (!session.lastRefreshToken().equals(refreshToken)) {
      return Map.of("success", "false");
    }
    var verification = Verification.create(userDatabaseTable(), homeKey,
      productKey, refreshKey, "", "");
    var newProductApiKey = verification.generateProductApiKey(user.id(),
      session.id(), expiration);
    var newRefreshToken = verification.generateRefreshToken(user.id(),
      session.id(), expiration);
    sessionDatabaseTable.updateSessionRefreshToken(session.id(), newRefreshToken);
    return Map.of("success", "true", "productApiKey", newProductApiKey,
      "refreshToken", newRefreshToken);
  }

  @RequestMapping(path = "/verification/logout/", method = RequestMethod.GET)
  public void logout(HttpServletRequest request) {
    sessionDatabaseTable.closeSession(findSessionId(request));
  }

  @RequestMapping(path = "/verification/isValid/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> isValid(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var result = verifyToken(productKey, body.getString("token"));
    if (result.getKey() != HttpServletResponse.SC_ACCEPTED) {
      response.setStatus(result.getKey());
      return CompletableFuture.completedFuture(Map.of("isValid", "false"));
    }
    var userId = UUID.fromString(result.getValue().get("id", String.class));
    return userDatabaseTable().userExists(userId)
      .thenApply(exists -> Map.of("isValid", exists ? "true" : "false"));
  }

  private Map.Entry<Integer, Claims> verifyToken(Key key, String token) {
    try {
      return new AbstractMap.SimpleEntry(HttpServletResponse.SC_ACCEPTED,
        Jwts.parser()
          .setSigningKey(key)
          .build()
          .parseClaimsJws(token)
          .getPayload());
    } catch (ExpiredJwtException exception) {
      return new AbstractMap.SimpleEntry(
        HttpServletResponse.SC_EXPECTATION_FAILED, null);
    } catch (Exception exception) {
      return new AbstractMap.SimpleEntry(
        HttpServletResponse.SC_FORBIDDEN, null);
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
    return userDatabaseTable().userExists(body.getString("email"))
      .thenApply(exists -> Map.of("exists", exists));
  }
}
