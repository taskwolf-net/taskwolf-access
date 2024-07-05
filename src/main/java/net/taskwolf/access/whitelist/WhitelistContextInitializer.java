package net.taskwolf.access.whitelist;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.whitelist.WhitelistConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class WhitelistContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final WhitelistConfiguration whitelistConfiguration;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("whitelistConfiguration", whitelistConfiguration);
  }
}
