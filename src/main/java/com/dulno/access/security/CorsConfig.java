package com.dulno.access.security;

import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor(staticName = "create")
public final class CorsConfig implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
      .allowedOrigins("https://dulno.com", "https://panel.dulno.com")
      .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
      .maxAge(3600)
      .allowedHeaders("content-type", "authorization", "home-authorization", "whitelist-key")
      .allowCredentials(true);
  }
}