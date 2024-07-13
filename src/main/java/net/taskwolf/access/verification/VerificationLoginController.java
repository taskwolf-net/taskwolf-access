package net.taskwolf.access.verification;

import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.bundle.Bundle;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserVerificationDatabaseTable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class VerificationLoginController {
  private final Key homeKey;
  private final Key productKey;
  private final UserDatabaseTable userDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;

  private VerificationLoginController(
    @Qualifier("homeKey") Key homeKey, @Qualifier("productKey") Key productKey,
    UserDatabaseTable userDatabaseTable,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable
  ) {
    this.homeKey = homeKey;
    this.productKey = productKey;
    this.userDatabaseTable = userDatabaseTable;
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
  }

  @RequestMapping(path = "/verification/login/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> login(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var loginFuture = login(body.getString("email"), body.getString("password"));
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

  private CompletableFuture<Map<String, Object>> login(String email, String password) {
    var verification = Verification.create(userDatabaseTable, homeKey, productKey,
      email.replace(" ", ""), hashPassword(password));
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    verification.isAuthenticated().thenAccept(isAuthenticated ->
      checkAuthorization(verification, futureResponse, isAuthenticated));
    return futureResponse;
  }

  private void checkAuthorization(
    Verification verification, CompletableFuture<Map<String, Object>> futureResponse,
    boolean isAuthenticated
  ) {
    if (!isAuthenticated) {
      futureResponse.complete(Map.of("success", false, "error", 1000));
      return;
    }
    processAuthorizedLogin(verification, futureResponse);
  }

  public void processAuthorizedLogin(
    Verification verification, CompletableFuture<Map<String, Object>> futureResponse
  ) {
    userDatabaseTable.findUser(verification.email()).thenAccept(user ->
      userVerificationDatabaseTable.verificationExists(user.id())
        .thenAccept(completionPending -> checkBundle(verification, futureResponse,
          user, completionPending)));
  }

  private void checkBundle(
    Verification verification, CompletableFuture<Map<String, Object>> futureResponse,
    User user, boolean completionPending
  ) {
    if (completionPending) {
      futureResponse.complete(Map.of("success", false, "error", 1001));
      return;
    }
    bundleDatabaseTable.bundleExists(user.id()).thenAccept(hasPersonalBundle ->
      findLatestBundleExpiration(user, hasPersonalBundle).thenAccept(
        latestExpiration -> completeLogin(verification, futureResponse, user,
          latestExpiration > System.currentTimeMillis(), latestExpiration)));
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
    Verification verification, CompletableFuture<Map<String, Object>> futureResponse,
    User user, boolean bundleEnabled, long expiration
  ) {
    var homeApiKey = verification.generateHomeApiKey(user.id());
    if (expiration == -10) {
      futureResponse.complete(Map.of("success", false, "error", 1002,
        "homeApiKey", homeApiKey));
      return;
    }
    if (!bundleEnabled) {
      futureResponse.complete(Map.of("success", false, "error", 1003,
        "homeApiKey", homeApiKey));
      return;
    }
    var productApiKey = verification.generateProductApiKey(user.id(), expiration);
    futureResponse.complete(Map.of("success", true, "productApiKey", productApiKey,
      "homeApiKey", homeApiKey));
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
