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
  private WhitelistFilter whitelistFilter;
  @Autowired
  private EquipmentFilter equipmentFilter;

  @Bean
  public FilterRegistrationBean<EquipmentFilter> provideEquipmentFilter() {
    var registrationBean = new FilterRegistrationBean<EquipmentFilter>();
    registrationBean.setFilter(equipmentFilter);
    registrationBean.setOrder(1);
    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<WhitelistFilter> provideWhitelistFilter() {
    var registrationBean = new FilterRegistrationBean<WhitelistFilter>();
    registrationBean.setFilter(whitelistFilter);
    registrationBean.setOrder(2);
    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<AuthorizationFilter> provideAuthorizationFilter() {
    var registrationBean = new FilterRegistrationBean<AuthorizationFilter>();
    registrationBean.setFilter(authorizationFilter);
    registrationBean.setOrder(3);
    return registrationBean;
  }
}
