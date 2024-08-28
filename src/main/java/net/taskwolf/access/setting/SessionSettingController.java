package net.taskwolf.access.setting;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.session.Session;
import net.taskwolf.core.session.SessionDatabaseTable;
import net.taskwolf.core.session.SessionStatus;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class SessionSettingController extends TaskwolfRestController {
  private final SessionDatabaseTable sessionDatabaseTable;

  private SessionSettingController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    SessionDatabaseTable sessionDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.sessionDatabaseTable = sessionDatabaseTable;
  }

  @RequestMapping(path = "/settings/sessions/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> sessions(
    HttpServletRequest request
  ) {
    var userSessionId = findSessionId(request);
    return sessionDatabaseTable.findSessionsOfUserByStatus(
      findUserId(request), SessionStatus.ACTIVE)
      .thenApply(sessions -> Map.of("sessions", sessions.stream().map(session ->
        assemblySessionInformation(userSessionId, session)).toList()));
  }

  private Map<String, Object> assemblySessionInformation(
    UUID userSessionId, Session session
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", session.id());
    information.put("platform", session.devicePlatform());
    information.put("country", session.country());
    information.put("city", session.city());
    information.put("openTime", session.openTime());
    information.put("status", session.status());
    information.put("isCurrent", userSessionId.equals(session.id()));
    return information;
  }

  @RequestMapping(path = "/settings/session/close/", method = RequestMethod.POST)
  public void closeSession(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var sessionId = body.getUUID("session");
    var userId = findUserId(request);
    var userSessionId = findSessionId(request);
    sessionDatabaseTable.sessionExists(sessionId)
      .thenAccept(exists -> closeSession(sessionId, userId, userSessionId, exists));
  }

  private void closeSession(
    UUID sessionId, UUID userId, UUID userSessionId, boolean sessionExists
  ) {
    if (!sessionExists) {
      return;
    }
    sessionDatabaseTable.findSession(sessionId)
      .thenAccept(session -> closeSession(session, userId, userSessionId));
  }

  private void closeSession(Session session, UUID userId, UUID userSessionId) {
    if (!session.userId().equals(userId)) {
      return;
    }
    if (userSessionId.equals(session.id())) {
      return;
    }
    sessionDatabaseTable.closeSession(session.id());
  }
}