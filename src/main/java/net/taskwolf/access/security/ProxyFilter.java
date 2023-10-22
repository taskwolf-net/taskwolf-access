package net.taskwolf.access.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.access.ProxyStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ProxyFilter extends OncePerRequestFilter {
  private final String proxyToken;
  private final ProxyStatus proxyStatus;

  private ProxyFilter(
    @Qualifier("proxyToken") String proxyToken, ProxyStatus proxyStatus
  ) {
    this.proxyToken = proxyToken;
    this.proxyStatus = proxyStatus;
  }

  private static final String PROXY_TOKEN_IDENTIFIER = "PROXYTOKEN";

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    if (proxyStatus.isDisabled()) {
      filterChain.doFilter(request, response);
      return;
    }
    var token = request.getHeader(PROXY_TOKEN_IDENTIFIER);
    if (token == null) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    if (!token.equals(proxyToken)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    filterChain.doFilter(request, response);
  }
}
