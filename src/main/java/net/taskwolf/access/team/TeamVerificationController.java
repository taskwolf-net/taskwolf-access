package net.taskwolf.access.team;

import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.team.TeamDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserVerificationDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TeamVerificationController {
  private final Key secretKey;
  private final UserDatabaseTable userDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final TeamDatabaseTable teamDatabaseTable;

  private TeamVerificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    UserVerificationDatabaseTable userVerificationDatabaseTable,
    TeamDatabaseTable teamDatabaseTable
  ) {
    this.secretKey = secretKey;
    this.userDatabaseTable = userDatabaseTable;
    this.userVerificationDatabaseTable = userVerificationDatabaseTable;
    this.teamDatabaseTable = teamDatabaseTable;
  }

  @RequestMapping(path = "/team/verification/login/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> login(
    @RequestBody Map<String, Object> input, HttpServletResponse servletResponse
  ) {
    var verification = TeamVerification.create(userDatabaseTable, secretKey,
      (String) input.get("email"), hashPassword((String) input.get("password")));
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    verification.isAuthenticated().thenAccept(isAuthenticated ->
      checkAuthorization(servletResponse, verification, futureResponse, isAuthenticated));
    return futureResponse;
  }

  private void checkAuthorization(
    HttpServletResponse servletResponse, TeamVerification verification,
    CompletableFuture<Map<String, Object>> futureResponse, boolean isAuthenticated
  ) {
    if (!isAuthenticated) {
      servletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      futureResponse.complete(Maps.newHashMap());
      return;
    }
    userDatabaseTable.findUser(verification.email()).thenAccept(user ->
      userVerificationDatabaseTable.verificationExists(user.id())
        .thenAccept(completionPending -> teamDatabaseTable.teamMemberExists(user.id())
          .thenAccept(teamMemberExists -> completeLogin(servletResponse,
            verification, futureResponse, user, completionPending, teamMemberExists))));
  }

  private void completeLogin(
    HttpServletResponse servletResponse, TeamVerification verification,
    CompletableFuture<Map<String, Object>> futureResponse, User user,
    boolean completionPending, boolean teamMemberExists
  ) {
    if (completionPending || !teamMemberExists) {
      servletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      futureResponse.complete(Maps.newHashMap());
      return;
    }
    futureResponse.complete(Map.of("apiKey", verification.generateApiKey(user.id())));
  }

  @RequestMapping(path = "/team/verification/isValid/", method = RequestMethod.POST)
  public Map<String, Object> isValid(@RequestBody Map<String, Object> input) {
    var response = Maps.<String, Object>newHashMap();
    try {
      var payload = Jwts.parser()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws((String) input.get("token")).getPayload();
      response.put("isValid", payload.containsKey("team") ? "true" : "false");
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
