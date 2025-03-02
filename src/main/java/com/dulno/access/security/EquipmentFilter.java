package com.dulno.access.security;

import com.dulno.core.environment.DulnoEnvironment;
import com.google.api.client.util.Lists;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EquipmentFilter extends OncePerRequestFilter {
  private final DulnoEnvironment environment;
  private final List<String> allowedOrigins = Lists.newArrayList();

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    prepareResponseHeaders(request, response);
    if (request.getMethod().equals(RequestMethod.OPTIONS.name())) {
      response.setStatus(HttpServletResponse.SC_OK);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static final List<String> ALLOWED_ORIGINS = List.of("https://dulno.com",
    "https://panel.dulno.com", "https://documentation.dulno.com");

  private void prepareResponseHeaders(
    HttpServletRequest request, HttpServletResponse response
  ) {
    if (allowedOrigins.isEmpty()) {
      fillAllowedOrigins();
    }
    var origin = request.getHeader("Origin");
    if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
      response.setHeader("Access-Control-Allow-Origin", origin);
    }
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, " +
      "DELETE, OPTIONS");
    response.setHeader("Access-Control-Max-Age", "3600");
    response.setHeader("Access-Control-Allow-Headers", "content-type, " +
      "authorization, home-authorization, whitelist-key");
    response.setHeader("Content-Type", "application/json");
  }

  private void fillAllowedOrigins() {
    if (environment.isLocal()) {
      allowedOrigins.add("http://0.0.0.0:8000");
      return;
    }
    allowedOrigins.add("https://" + environment.domain());
    allowedOrigins.add("https://panel." + environment.domain());
    allowedOrigins.add("https://documentation." + environment.domain());
    if (environment.isStaging()) {
      allowedOrigins.add("http://0.0.0.0:8000");
    }
  }
}
