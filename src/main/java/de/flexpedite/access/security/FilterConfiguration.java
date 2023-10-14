package de.flexpedite.access.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class FilterConfiguration {
  @Autowired
  private AuthorizationFilter authorizationFilter;

  @Bean
  public FilterRegistrationBean<AuthorizationFilter> registrationBean() {
    var registrationBean = new FilterRegistrationBean<AuthorizationFilter>();
    registrationBean.setFilter(authorizationFilter);
    return registrationBean;
  }
}
