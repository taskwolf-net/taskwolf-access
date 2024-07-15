package net.taskwolf.access.verification;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.recaptcha.RecaptchaConfiguration;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserEmailChangeDatabaseTable;
import net.taskwolf.core.user.UserPasswordResetDatabaseTable;
import net.taskwolf.core.user.UserVerificationDatabaseTable;
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

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("userDatabaseTable", userDatabaseTable);
    beanFactory.registerSingleton("userVerificationDatabaseTable", userVerificationDatabaseTable);
    beanFactory.registerSingleton("userPasswordResetDatabaseTable", userPasswordResetDatabaseTable);
    beanFactory.registerSingleton("userEmailChangeDatabaseTable", userEmailChangeDatabaseTable);
    beanFactory.registerSingleton("recaptchaConfiguration", recaptchaConfiguration);
  }
}
