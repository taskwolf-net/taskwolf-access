package net.taskwolf.access.security;

import com.google.common.collect.Lists;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Key;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthorizationFilter extends OncePerRequestFilter {
  private final Key secretKey;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    prepareResponseHeaders(response);
    var apiKey = request.getHeader("Authorization");
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
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    var controllers = Lists.<String>newArrayList();
    controllers.add("/" + CURRENT_API_VERSION + "/whitelist/isValid/");
    controllers.add("/" + CURRENT_API_VERSION + "/verification/register/");
    controllers.add("/" + CURRENT_API_VERSION + "/verification/complete/");
    controllers.add("/" + CURRENT_API_VERSION + "/verification/login/");
    controllers.add("/" + CURRENT_API_VERSION + "/verification/isValid/");
    controllers.add("/" + CURRENT_API_VERSION + "/discord/guild/add/");
    controllers.add("/" + CURRENT_API_VERSION + "/google/login/");
    controllers.add("/" + CURRENT_API_VERSION + "/google/account/add/");
    controllers.add("/" + CURRENT_API_VERSION + "/password/reset/request/");
    controllers.add("/" + CURRENT_API_VERSION + "/password/reset/complete/");
    controllers.addAll(controllers.stream().filter(controller ->
      controller.startsWith("/" + CURRENT_API_VERSION + "/team/")).toList());
    return controllers.contains(request.getRequestURI());
  }

  private void prepareResponseHeaders(HttpServletResponse response) {
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    response.setHeader("Access-Control-Max-Age", "3600");
    response.setHeader("Access-Control-Allow-Headers", "content-type, authorization, whitelist-key");
  }

  private boolean validateApiKey(String apiKey) {
    try {
      Jwts.parser()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws(apiKey);
      return true;
    } catch (Exception exception) {
      return false;
    }
  }
}