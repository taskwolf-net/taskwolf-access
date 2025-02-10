package com.dulno.access.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class EquipmentFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    if (request.getMethod().equals(RequestMethod.OPTIONS.name())) {
      response.setStatus(HttpServletResponse.SC_OK);
      return;
    }
    filterChain.doFilter(request, response);
  }
}
