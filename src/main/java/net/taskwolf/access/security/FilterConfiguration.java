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
  private EquipmentFilter equipmentFilter;
  @Autowired
  private WhitelistFilter whitelistFilter;
  @Autowired
  private ProductAuthorizationFilter productAuthorizationFilter;
  @Autowired
  private HomeAuthorizationFilter homeAuthorizationFilter;

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
  public FilterRegistrationBean<ProductAuthorizationFilter> provideProductAuthorizationFilter() {
    var registrationBean = new FilterRegistrationBean<ProductAuthorizationFilter>();
    registrationBean.setFilter(productAuthorizationFilter);
    registrationBean.setOrder(3);
    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<HomeAuthorizationFilter> provideHomeAuthorizationFilter() {
    var registrationBean = new FilterRegistrationBean<HomeAuthorizationFilter>();
    registrationBean.setFilter(homeAuthorizationFilter);
    registrationBean.setOrder(4);
    return registrationBean;
  }
}
