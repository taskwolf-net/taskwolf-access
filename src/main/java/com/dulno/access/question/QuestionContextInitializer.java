package com.dulno.access.question;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import com.dulno.core.question.QuestionDatabaseTable;
import com.dulno.core.question.QuestionMessageDatabaseTable;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class QuestionContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final QuestionDatabaseTable questionDatabaseTable;
  private final QuestionMessageDatabaseTable questionMessageDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("questionDatabaseTable", questionDatabaseTable);
    beanFactory.registerSingleton("questionMessageDatabaseTable",
      questionMessageDatabaseTable);
  }
}
