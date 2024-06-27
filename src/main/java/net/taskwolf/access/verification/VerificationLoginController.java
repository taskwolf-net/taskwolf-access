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
  private final Key secretKey;
  private final UserDatabaseTable userDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;

  private VerificationLoginController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable
  ) {
    this.secretKey = secretKey;
    this.userDatabaseTable = userDatabaseTable;
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
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
      futureResponse.complete(Map.of("success", false, "error", 1000));
      return;
    }
    userDatabaseTable.findUser(verification.email()).thenAccept(user ->
      userVerificationDatabaseTable.verificationExists(user.id())
        .thenAccept(completionPending -> checkBundle(servletResponse, verification,
          futureResponse, user, completionPending)));
  }

  private void checkBundle(
    HttpServletResponse servletResponse, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse, User user,
    boolean completionPending
  ) {
    if (completionPending) {
      servletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      futureResponse.complete(Map.of("success", false, "error", 1001));
      return;
    }
    bundleDatabaseTable.bundleExists(user.id()).thenAccept(hasPersonalBundle ->
      findLatestBundleExpiration(user, hasPersonalBundle).thenAccept(
        latestExpiration -> completeLogin(servletResponse, verification,
          futureResponse, user, latestExpiration > System.currentTimeMillis(),
          latestExpiration)));
  }

  private CompletableFuture<Long> findLatestBundleExpiration(
    User user, boolean hasPersonalBundle
  ) {
    var targets = Lists.newArrayList(user.organizations());
    if (hasPersonalBundle) {
      targets.add(user.id());
    }
    return AsyncIterator.execute(targets, bundleDatabaseTable::findBundle)
      .thenApply(bundles -> bundles.stream().map(Bundle::expiration)
        .sorted(Comparator.reverseOrder()).findFirst().get());
  }

  private void completeLogin(
    HttpServletResponse servletResponse, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse, User user,
    boolean bundleEnabled, long expiration
  ) {
    if (!bundleEnabled) {
      servletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      futureResponse.complete(Map.of("success", false, "error", 1002));
      return;
    }
    var apiKey = verification.generateApiKey(user.id(), expiration);
    futureResponse.complete(Map.of("success", true, "apiKey", apiKey));
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
