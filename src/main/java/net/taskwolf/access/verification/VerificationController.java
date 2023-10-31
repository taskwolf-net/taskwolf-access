package net.taskwolf.access.verification;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.distribution.Distribution;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@CrossOrigin
@RestController
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class VerificationController {
  private final Key secretKey;
  private final UserDatabaseTable userDatabaseTable;
  private final Distribution distribution;

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
    distribution.addNewUser(userId);
  }

  @RequestMapping(path = "/verification/login/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> login(
    @RequestBody Map<String, Object> input, HttpServletResponse servletResponse
  ) {
    var verification = Verification.create(userDatabaseTable, secretKey,
      (String) input.get("email"), hashPassword((String) input.get("password")));
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    verification.isAuthenticated().thenAccept(isAuthenticated ->
      completeLogin(servletResponse, verification, futureResponse, isAuthenticated));
    return futureResponse;
  }

  private void completeLogin(
    HttpServletResponse servletResponse, Verification verification,
    CompletableFuture<Map<String, Object>> futureResponse, boolean isAuthenticated
  ) {
    if (!isAuthenticated) {
      servletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      futureResponse.complete(Maps.newHashMap());
      return;
    }
    userDatabaseTable.findUser(verification.email()).thenAccept(user ->
      futureResponse.complete(Map.of("apiKey", verification.generateApiKey(user.id(), user.name()))));
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
