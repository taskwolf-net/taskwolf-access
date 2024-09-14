package com.dulno.access.workflow;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import com.dulno.core.action.ActionDatabaseTable;
import com.dulno.core.condition.ConditionDatabaseTable;
import com.dulno.core.condition.ConditionInformationRepository;
import com.dulno.core.trigger.TriggerDatabaseTable;
import com.dulno.core.workflow.WorkflowDatabaseTable;
import com.dulno.core.workflow.operation.OperationDatabaseTable;
import com.dulno.core.workflow.throttle.WorkflowThrottleDatabaseTable;
import com.dulno.core.workflow.timeline.TimelineDatabaseTable;
import com.dulno.core.workflow.timeline.TimelineFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class WorkflowContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final OperationDatabaseTable operationDatabaseTable;
  private final WorkflowThrottleDatabaseTable workflowThrottleDatabaseTable;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final ConditionDatabaseTable conditionDatabaseTable;
  private final TimelineDatabaseTable timelineDatabaseTable;
  private final ConditionInformationRepository conditionRepository;
  private final TimelineFactory timelineFactory;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("workflowDatabaseTable", workflowDatabaseTable);
    beanFactory.registerSingleton("operationDatabaseTable", operationDatabaseTable);
    beanFactory.registerSingleton("workflowThrottleDatabaseTable",
      workflowThrottleDatabaseTable);
    beanFactory.registerSingleton("triggerDatabaseTable", triggerDatabaseTable);
    beanFactory.registerSingleton("actionDatabaseTable", actionDatabaseTable);
    beanFactory.registerSingleton("conditionDatabaseTable", conditionDatabaseTable);
    beanFactory.registerSingleton("timelineDatabaseTable", timelineDatabaseTable);
    beanFactory.registerSingleton("conditionRepository", conditionRepository);
    beanFactory.registerSingleton("timelineFactory", timelineFactory);
  }
}
