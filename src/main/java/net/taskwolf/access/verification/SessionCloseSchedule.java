package net.taskwolf.access.verification;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.taskwolf.access.organization.OrganizationModificationController;
import net.taskwolf.access.setting.AccountSettingController;
import net.taskwolf.core.bundle.Bundle;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.session.Session;
import net.taskwolf.core.session.SessionDatabaseTable;
import net.taskwolf.core.session.SessionStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public final class SessionCloseSchedule {
  private final SessionDatabaseTable sessionDatabaseTable;
  private final Key refreshSecret;
  private final ScheduledExecutorService executorService =
    Executors.newScheduledThreadPool(1);
  private ScheduledFuture<?> scheduler;

  public SessionCloseSchedule(
    SessionDatabaseTable sessionDatabaseTable,
    @Qualifier("refreshKey") Key refreshSecret
  ) {
    this.sessionDatabaseTable = sessionDatabaseTable;
    this.refreshSecret = refreshSecret;
  }

  private static final int RESET_INTERVAL = 1000 * 60 * 60 * 24;
  private static final TimeUnit RESET_TIME_UNIT = TimeUnit.MILLISECONDS;

  public void start() {
    scheduler = executorService.scheduleAtFixedRate(this::execute,
      calculateInitialDelay(), RESET_INTERVAL, RESET_TIME_UNIT);
  }

  private long calculateInitialDelay() {
    var calendar = Calendar.getInstance();
    calendar.add(Calendar.DAY_OF_MONTH, 1);
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTimeInMillis() - System.currentTimeMillis();
  }

  private void execute() {
    sessionDatabaseTable.findAllSessionsByStatus(SessionStatus.ACTIVE)
      .thenAcceptAsync(this::closeExpiredSessions);
  }

  private void closeExpiredSessions(List<Session> sessions) {
    for (var session : sessions) {
      try {
        Jwts.parser()
          .setSigningKey(refreshSecret)
          .build()
          .parseClaimsJws(session.lastRefreshToken())
          .getPayload();
      } catch (ExpiredJwtException exception) {
        sessionDatabaseTable.closeSession(session);
      }
    }
  }

  public void stop() {
    scheduler.cancel(false);
  }
}
