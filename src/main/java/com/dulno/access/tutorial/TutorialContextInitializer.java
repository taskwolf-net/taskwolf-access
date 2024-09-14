package com.dulno.access.tutorial;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import com.dulno.core.tutorial.TutorialDatabaseTable;
import com.dulno.core.tutorial.level.TutorialLevelRegistry;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class TutorialContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final TutorialDatabaseTable tutorialDatabaseTable;
  private final TutorialLevelRegistry tutorialLevelRegistry;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("tutorialDatabaseTable", tutorialDatabaseTable);
    beanFactory.registerSingleton("tutorialLevelRegistry", tutorialLevelRegistry);
  }
}
