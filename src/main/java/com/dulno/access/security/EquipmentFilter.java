package com.dulno.access.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class EquipmentFilter extends OncePerRequestFilter {
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
    "https://panel.dulno.com");

  private void prepareResponseHeaders(
    HttpServletRequest request, HttpServletResponse response
  ) {
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
}
