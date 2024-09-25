package com.dulno.access.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class FilterConfiguration {
  @Autowired
  private EquipmentFilter equipmentFilter;
  @Autowired
  private WhitelistFilter whitelistFilter;
  @Autowired
  private MaintenanceFilter maintenanceFilter;
  @Autowired
  private ProductAuthorizationFilter productAuthorizationFilter;
  @Autowired
  private HomeAuthorizationFilter homeAuthorizationFilter;
  @Autowired
  private MixedAuthorizationFilter mixedAuthorizationFilter;

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
  public FilterRegistrationBean<MaintenanceFilter> provideMaintenanceFilter() {
    var registrationBean = new FilterRegistrationBean<MaintenanceFilter>();
    registrationBean.setFilter(maintenanceFilter);
    registrationBean.setOrder(3);
    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<ProductAuthorizationFilter> provideProductAuthorizationFilter() {
    var registrationBean = new FilterRegistrationBean<ProductAuthorizationFilter>();
    registrationBean.setFilter(productAuthorizationFilter);
    registrationBean.setOrder(4);
    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<HomeAuthorizationFilter> provideHomeAuthorizationFilter() {
    var registrationBean = new FilterRegistrationBean<HomeAuthorizationFilter>();
    registrationBean.setFilter(homeAuthorizationFilter);
    registrationBean.setOrder(5);
    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<MixedAuthorizationFilter> provideMixedAuthorizationFilter() {
    var registrationBean = new FilterRegistrationBean<MixedAuthorizationFilter>();
    registrationBean.setFilter(mixedAuthorizationFilter);
    registrationBean.setOrder(6);
    return registrationBean;
  }
}
