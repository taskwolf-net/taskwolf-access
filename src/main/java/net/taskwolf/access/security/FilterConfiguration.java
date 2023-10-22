package net.taskwolf.access.security;

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
  @Autowired
  private ProxyFilter proxyFilter;

  @Bean
  public FilterRegistrationBean<AuthorizationFilter> provideAuthorizationFilter() {
    var registrationBean = new FilterRegistrationBean<AuthorizationFilter>();
    registrationBean.setFilter(authorizationFilter);
    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<ProxyFilter> provideProxyFilter() {
    var registrationBean = new FilterRegistrationBean<ProxyFilter>();
    registrationBean.setFilter(proxyFilter);
    return registrationBean;
  }
}
