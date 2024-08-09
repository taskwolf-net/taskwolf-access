package net.taskwolf.access.security;

import com.google.common.collect.Lists;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Key;

@Component
public class HomeAuthorizationFilter extends OncePerRequestFilter {
  private final Key homeKey;

  private HomeAuthorizationFilter(@Qualifier("homeKey") Key homeKey) {
    this.homeKey = homeKey;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    var apiKey = request.getHeader("Home-Authorization");
    if (apiKey == null) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    apiKey = apiKey.replace("Bearer ", "");
    if (!validateApiKey(apiKey)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static final String CURRENT_API_VERSION = "v1";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    var shouldFilter = Lists.<String>newArrayList();
    shouldFilter.add("/" + CURRENT_API_VERSION + "/checkout/");
    shouldFilter.add("/" + CURRENT_API_VERSION + "/offer/find/");
    shouldFilter.add("/" + CURRENT_API_VERSION + "/offer/accept/");
    shouldFilter.add("/" + CURRENT_API_VERSION + "/offer/decline/");
    shouldFilter.add("/" + CURRENT_API_VERSION + "/trial/use/");
    shouldFilter.add("/" + CURRENT_API_VERSION + "/organization/join/");
    var url = request.getRequestURI();
    return !shouldFilter.contains(url);
  }

  private boolean validateApiKey(String apiKey) {
    try {
      Jwts.parser()
        .setSigningKey(homeKey)
        .build()
        .parseClaimsJws(apiKey);
      return true;
    } catch (Exception exception) {
      return false;
    }
  }
}
