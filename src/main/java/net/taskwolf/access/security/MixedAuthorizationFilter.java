package net.taskwolf.access.security;

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
public class MixedAuthorizationFilter extends OncePerRequestFilter {
  private final Key productKey;
  private final Key homeKey;

  private MixedAuthorizationFilter(
    @Qualifier("productKey") Key productKey, @Qualifier("homeKey") Key homeKey
  ) {
    this.productKey = productKey;
    this.homeKey = homeKey;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    var productAuthorized = checkAuthorization(request, productKey, "Authorization");
    var homeAuthorized = checkAuthorization(request, homeKey, "Home-Authorization");
    if (!productAuthorized && !homeAuthorized) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean checkAuthorization(
    HttpServletRequest request, Key key, String headerName
  ) {
    var apiKey = request.getHeader(headerName);
    if (apiKey == null) {
      return false;
    }
    apiKey = apiKey.replace("Bearer ", "");
    return validateApiKey(key, apiKey);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    var url = request.getRequestURI();
    return !(url.contains("/settings/") ||
      url.contains("/ticket/") || url.contains("/tickets/"));
  }

  private boolean validateApiKey(Key key, String apiKey) {
    try {
      Jwts.parser()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(apiKey);
      return true;
    } catch (Exception exception) {
      return false;
    }
  }
}
