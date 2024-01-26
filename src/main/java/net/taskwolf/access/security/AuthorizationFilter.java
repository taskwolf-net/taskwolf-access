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
    requestHandlerMapping.getHandlerMethods().forEach((key, value) ->
      controllers.addAll(key.getDirectPaths().stream().map(path -> "/" +
        CURRENT_API_VERSION + path).toList()));
    controllers.remove("/" + CURRENT_API_VERSION + "/whitelist/isValid/");
    controllers.remove("/" + CURRENT_API_VERSION + "/verification/register/");
    controllers.remove("/" + CURRENT_API_VERSION + "/verification/complete/");
    controllers.remove("/" + CURRENT_API_VERSION + "/verification/login/");
    controllers.remove("/" + CURRENT_API_VERSION + "/verification/isValid/");
    controllers.remove("/" + CURRENT_API_VERSION + "/discord/guild/add/");
    controllers.remove("/" + CURRENT_API_VERSION + "/google/login/");
    controllers.remove("/" + CURRENT_API_VERSION + "/google/account/add/");
    controllers.remove("/" + CURRENT_API_VERSION + "/password/reset/request/");
    controllers.remove("/" + CURRENT_API_VERSION + "/password/reset/complete/");
    controllers.removeAll(controllers.stream().filter(controller ->
      controller.startsWith("/" + CURRENT_API_VERSION + "/team/")).toList());
    return !(controllers.contains(request.getRequestURI()));
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