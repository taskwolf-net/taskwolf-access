package com.dulno.access.activity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import com.dulno.core.user.activity.UserActivityDatabaseTable;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class ActivityContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final UserActivityDatabaseTable activityDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("activityDatabaseTable", activityDatabaseTable);
  }
}
