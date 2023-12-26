package net.taskwolf.access.verification;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.distribution.Distribution;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserVerificationDatabaseTable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class VerificationController {
  private final Key secretKey;
  private final String verificationMailHost;
  private final String verificationMail;
  private final String verificationMailPassword;
  private final UserDatabaseTable userDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final Distribution distribution;

  private VerificationController(
    Key secretKey, @Qualifier("verificationMailHost") String verificationMailHost,
    @Qualifier("verificationMail") String verificationMail,
    @Qualifier("verificationMailPassword") String verificationMailPassword,
    UserDatabaseTable userDatabaseTable,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    Distribution distribution
  ) {
    this.secretKey = secretKey;
    this.verificationMailHost = verificationMailHost;
    this.verificationMail = verificationMail;
    this.verificationMailPassword = verificationMailPassword;
    this.userDatabaseTable = userDatabaseTable;
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
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

  private void insertNewUser(
    UUID userId, String name, String email, String passwordHash
  ) {
    userDatabaseTable.insertUser(userId, name, email, passwordHash,
      Lists.newArrayList());
    var token = UUID.randomUUID().toString();
    userVerificationDatabaseTable.insertVerification(userId, token);
    VerificationMail.create(verificationMailHost, verificationMail,
      verificationMailPassword, email, name, userId, token).send();
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
    futureResponse.complete(Map.of("apiKey", verification.generateApiKey(
      user.id(), user.name())));
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
