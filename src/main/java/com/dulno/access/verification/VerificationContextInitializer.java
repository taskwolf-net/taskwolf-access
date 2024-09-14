package com.dulno.access.verification;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.maxmind.geoip2.DatabaseReader;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import com.dulno.core.recaptcha.RecaptchaConfiguration;
import com.dulno.core.session.SessionDatabaseTable;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserEmailChangeDatabaseTable;
import com.dulno.core.user.UserPasswordResetDatabaseTable;
import com.dulno.core.user.UserVerificationDatabaseTable;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class VerificationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final UserDatabaseTable userDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final UserPasswordResetDatabaseTable userPasswordResetDatabaseTable;
  private final UserEmailChangeDatabaseTable userEmailChangeDatabaseTable;
  private final RecaptchaConfiguration recaptchaConfiguration;
  private final SessionDatabaseTable sessionDatabaseTable;
  private final DatabaseReader databaseReader;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("userDatabaseTable", userDatabaseTable);
    beanFactory.registerSingleton("userVerificationDatabaseTable", userVerificationDatabaseTable);
    beanFactory.registerSingleton("userPasswordResetDatabaseTable", userPasswordResetDatabaseTable);
    beanFactory.registerSingleton("userEmailChangeDatabaseTable", userEmailChangeDatabaseTable);
    beanFactory.registerSingleton("recaptchaConfiguration", recaptchaConfiguration);
    beanFactory.registerSingleton("sessionDatabaseTable", sessionDatabaseTable);
    beanFactory.registerSingleton("databaseReader", databaseReader);
  }
}
