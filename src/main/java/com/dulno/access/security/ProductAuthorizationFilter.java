package com.dulno.access.security;

import com.google.common.collect.Lists;
import io.jsonwebtoken.ExpiredJwtException;
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
public class ProductAuthorizationFilter extends OncePerRequestFilter {
  private final Key productKey;

  private ProductAuthorizationFilter(@Qualifier("productKey") Key productKey) {
    this.productKey = productKey;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    var apiKey = request.getHeader("Authorization");
    if (apiKey == null) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    apiKey = apiKey.replace("Bearer ", "");
    var validation = validateApiKey(apiKey);
    if (validation != HttpServletResponse.SC_ACCEPTED) {
      response.setStatus(validation);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static final String CURRENT_API_VERSION = "v1";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    var controllers = Lists.<String>newArrayList();
    controllers.add("/" + CURRENT_API_VERSION + "/");
    controllers.add("/" + CURRENT_API_VERSION + "/whitelist/isValid/");
    controllers.add("/" + CURRENT_API_VERSION + "/maintenance/scheduled/");
    controllers.add("/" + CURRENT_API_VERSION + "/maintenance/running/");
    controllers.add("/" + CURRENT_API_VERSION + "/bundle/preset/");
    controllers.add("/" + CURRENT_API_VERSION + "/checkout/");
    controllers.add("/" + CURRENT_API_VERSION + "/stripe/checkout/");
    controllers.add("/" + CURRENT_API_VERSION + "/stripe/payment/");
    controllers.add("/" + CURRENT_API_VERSION + "/stripe/complete/");
    controllers.add("/" + CURRENT_API_VERSION + "/offer/find/");
    controllers.add("/" + CURRENT_API_VERSION + "/offer/accept/");
    controllers.add("/" + CURRENT_API_VERSION + "/offer/decline/");
    controllers.add("/" + CURRENT_API_VERSION + "/trial/use/");
    controllers.add("/" + CURRENT_API_VERSION + "/verification/register/");
    controllers.add("/" + CURRENT_API_VERSION + "/verification/email/resend/");
    controllers.add("/" + CURRENT_API_VERSION + "/verification/complete/");
    controllers.add("/" + CURRENT_API_VERSION + "/verification/login/");
    controllers.add("/" + CURRENT_API_VERSION + "/verification/refresh/");
    controllers.add("/" + CURRENT_API_VERSION + "/verification/isValid/");
    controllers.add("/" + CURRENT_API_VERSION + "/verification/home/isValid/");
    controllers.add("/" + CURRENT_API_VERSION + "/email/exists/");
    controllers.add("/" + CURRENT_API_VERSION + "/password/reset/request/");
    controllers.add("/" + CURRENT_API_VERSION + "/password/reset/complete/");
    controllers.add("/" + CURRENT_API_VERSION + "/distribution/add/");
    controllers.add("/" + CURRENT_API_VERSION + "/question/create/");
    controllers.add("/" + CURRENT_API_VERSION + "/sale/create/");
    controllers.add("/" + CURRENT_API_VERSION + "/organization/join/");
    controllers.add("/" + CURRENT_API_VERSION + "/discord/guild/add/");
    controllers.add("/" + CURRENT_API_VERSION + "/google/login/");
    controllers.add("/" + CURRENT_API_VERSION + "/google/account/add/");
    controllers.add("/" + CURRENT_API_VERSION + "/gitlab/authorize/");
    controllers.add("/" + CURRENT_API_VERSION + "/gitlab/event/");
    controllers.add("/" + CURRENT_API_VERSION + "/microsoft/teams/messages/");
    var url = request.getRequestURI();
    return controllers.contains(url) || url.contains("/team/") ||
      url.contains("/webhook/trigger/") || url.contains("unauthorized") ||
      url.contains("/settings/") || url.contains("/ticket/") ||
      url.contains("/tickets/");
  }

  private int validateApiKey(String apiKey) {
    try {
      Jwts.parser()
        .setSigningKey(productKey)
        .build()
        .parseClaimsJws(apiKey);
      return HttpServletResponse.SC_ACCEPTED;
    } catch (ExpiredJwtException exception) {
      return HttpServletResponse.SC_EXPECTATION_FAILED;
    } catch (Exception exception) {
      return HttpServletResponse.SC_FORBIDDEN;
    }
  }
}