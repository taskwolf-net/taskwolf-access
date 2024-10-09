package com.dulno.access.stripe;

import com.dulno.core.stripe.StripeCompletionDatabaseTable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.stripe.StripeClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import com.dulno.core.stripe.StripeConfiguration;
import com.dulno.core.stripe.StripeDatabaseTable;
import com.dulno.core.stripe.TerminationDatabaseTable;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class StripeContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final StripeConfiguration stripeConfiguration;
  private final StripeDatabaseTable stripeDatabaseTable;
  private final StripeCompletionDatabaseTable stripeCompletionDatabaseTable;
  private final StripeClient stripeClient;
  private final TerminationDatabaseTable terminationDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("stripeConfiguration", stripeConfiguration);
    beanFactory.registerSingleton("stripeDatabaseTable", stripeDatabaseTable);
    beanFactory.registerSingleton("stripeCompletionDatabaseTable",
      stripeCompletionDatabaseTable);
    beanFactory.registerSingleton("stripeClient", stripeClient);
    beanFactory.registerSingleton("terminationDatabaseTable", terminationDatabaseTable);
  }
}
