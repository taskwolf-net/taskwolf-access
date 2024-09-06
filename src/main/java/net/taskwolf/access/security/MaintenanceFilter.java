package net.taskwolf.access.security;

import com.google.common.collect.Lists;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.maintenance.MaintenanceSchedule;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MaintenanceFilter extends OncePerRequestFilter {
  private final MaintenanceSchedule maintenanceSchedule;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    if (maintenanceSchedule.isMaintenanceRunning()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static final String CURRENT_API_VERSION = "v1";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    var controllers = Lists.<String>newArrayList();
    controllers.add("/" + CURRENT_API_VERSION + "/");
    controllers.add("/" + CURRENT_API_VERSION + "/whitelist/isValid/");
    controllers.add("/" + CURRENT_API_VERSION + "/maintenance/scheduled/");
    controllers.add("/" + CURRENT_API_VERSION + "/maintenance/running/");
    return controllers.contains(request.getRequestURI());
  }
}
