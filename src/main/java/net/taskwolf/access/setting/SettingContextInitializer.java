package net.taskwolf.access.setting;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.notification.NotificationDatabaseTable;
import net.taskwolf.core.user.mfa.MultiFactorAuthDatabaseTable;
import net.taskwolf.core.user.mfa.MultiFactorAuthFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class SettingContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final NotificationDatabaseTable notificationDatabaseTable;
  private final MultiFactorAuthDatabaseTable multiFactorAuthDatabaseTable;
  private final MultiFactorAuthFactory multiFactorAuthFactory;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("notificationDatabaseTable", notificationDatabaseTable);
    beanFactory.registerSingleton("multiFactorAuthDatabaseTable", multiFactorAuthDatabaseTable);
    beanFactory.registerSingleton("multiFactorAuthFactory", multiFactorAuthFactory);
  }
}
