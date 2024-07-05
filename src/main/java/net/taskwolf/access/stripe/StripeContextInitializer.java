package net.taskwolf.access.stripe;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.stripe.StripeConfiguration;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class StripeContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final StripeConfiguration stripeConfiguration;
  private final StripeDatabaseTable stripeDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("stripeConfiguration", stripeConfiguration);
    beanFactory.registerSingleton("stripeDatabaseTable", stripeDatabaseTable);
  }
}
