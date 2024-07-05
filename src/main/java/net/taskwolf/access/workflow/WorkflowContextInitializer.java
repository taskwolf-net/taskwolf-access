package net.taskwolf.access.workflow;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.action.ActionDatabaseTable;
import net.taskwolf.core.condition.ConditionDatabaseTable;
import net.taskwolf.core.condition.ConditionInformationRepository;
import net.taskwolf.core.trigger.TriggerDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.WorkflowExecutionDatabaseTable;
import net.taskwolf.core.workflow.operation.OperationDatabaseTable;
import net.taskwolf.core.workflow.timeline.TimelineDatabaseTable;
import net.taskwolf.core.workflow.timeline.TimelineFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class WorkflowContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final WorkflowExecutionDatabaseTable workflowExecutionDatabaseTable;
  private final OperationDatabaseTable operationDatabaseTable;
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
    beanFactory.registerSingleton("workflowExecutionDatabaseTable", workflowExecutionDatabaseTable);
    beanFactory.registerSingleton("operationDatabaseTable", operationDatabaseTable);
    beanFactory.registerSingleton("triggerDatabaseTable", triggerDatabaseTable);
    beanFactory.registerSingleton("actionDatabaseTable", actionDatabaseTable);
    beanFactory.registerSingleton("conditionDatabaseTable", conditionDatabaseTable);
    beanFactory.registerSingleton("timelineDatabaseTable", timelineDatabaseTable);
    beanFactory.registerSingleton("conditionRepository", conditionRepository);
    beanFactory.registerSingleton("timelineFactory", timelineFactory);
  }
}
