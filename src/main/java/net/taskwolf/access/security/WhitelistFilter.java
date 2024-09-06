package net.taskwolf.access.security;

import com.google.common.collect.Lists;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.whitelist.WhitelistConfiguration;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class WhitelistFilter extends OncePerRequestFilter {
  private final WhitelistConfiguration whitelistConfiguration;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    if (!whitelistConfiguration.whitelistEnabled()) {
      filterChain.doFilter(request, response);
      return;
    }
    var key = request.getHeader("WHITELIST-KEY");
    if (key == null) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    if (!whitelistConfiguration.whitelistKey().equals(key)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static final String CURRENT_API_VERSION = "v1";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    var controllers = Lists.<String>newArrayList();
    controllers.add("/" + CURRENT_API_VERSION + "/");
    controllers.add("/" + CURRENT_API_VERSION + "/whitelist/isValid/");
    controllers.add("/" + CURRENT_API_VERSION + "/maintenance/scheduled/");
    controllers.add("/" + CURRENT_API_VERSION + "/maintenance/running/");
    controllers.add("/" + CURRENT_API_VERSION + "/stripe/checkout/");
    controllers.add("/" + CURRENT_API_VERSION + "/stripe/payment/");
    controllers.add("/" + CURRENT_API_VERSION + "/discord/guild/add/");
    controllers.add("/" + CURRENT_API_VERSION + "/google/login/");
    controllers.add("/" + CURRENT_API_VERSION + "/google/account/add/");
    return controllers.contains(request.getRequestURI());
  }
}
