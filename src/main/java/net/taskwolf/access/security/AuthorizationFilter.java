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
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.security.Key;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthorizationFilter extends OncePerRequestFilter {
  private final Key secretKey;
  private final RequestMappingHandlerMapping requestHandlerMapping;

  private static final String API_KEY_IDENTIFIER = "API-KEY";

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    prepareResponseHeaders(response);
    var apiKey = request.getHeader(API_KEY_IDENTIFIER);
    if (apiKey == null) {
      return;
    }
    if (!validateApiKey(apiKey)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    filterChain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    var controllers = Lists.<String>newArrayList();
    requestHandlerMapping.getHandlerMethods().forEach((key, value) ->
      controllers.addAll(key.getDirectPaths()));
    controllers.remove("/");
    controllers.remove("/verification/register/");
    controllers.remove("/verification/login/");
    controllers.remove("/verification/isValid/");
    controllers.remove("/discord/guild/add/");
    return !(controllers.contains(request.getRequestURI()));
  }

  private void prepareResponseHeaders(HttpServletResponse response) {
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    response.setHeader("Access-Control-Max-Age", "3600");
    response.setHeader("Access-Control-Allow-Headers", "content-type, " + API_KEY_IDENTIFIER);
    response.addHeader("Access-Control-Expose-Headers", API_KEY_IDENTIFIER);
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