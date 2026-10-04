package net.taskwolf.access.workflow;

import net.taskwolf.workflow.loop.LoopDatabaseTable;
import net.taskwolf.workflow.loop.LoopInformationRepository;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.workflow.action.ActionDatabaseTable;
import net.taskwolf.workflow.condition.ConditionDatabaseTable;
import net.taskwolf.workflow.condition.ConditionInformationRepository;
import net.taskwolf.workflow.trigger.TriggerDatabaseTable;
import net.taskwolf.workflow.structure.WorkflowDatabaseTable;
import net.taskwolf.workflow.operation.OperationDatabaseTable;
import net.taskwolf.workflow.throttle.WorkflowThrottleDatabaseTable;
import net.taskwolf.workflow.timeline.TimelineDatabaseTable;
import net.taskwolf.workflow.timeline.TimelineFactory;
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
  private final ConditionInformationRepository conditionRepository;
  private final LoopDatabaseTable loopDatabaseTable;
  private final LoopInformationRepository loopRepository;
  private final TimelineDatabaseTable timelineDatabaseTable;
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
    beanFactory.registerSingleton("conditionRepository", conditionRepository);
    beanFactory.registerSingleton("loopDatabaseTable", loopDatabaseTable);
    beanFactory.registerSingleton("loopRepository", loopRepository);
    beanFactory.registerSingleton("timelineDatabaseTable", timelineDatabaseTable);
    beanFactory.registerSingleton("timelineFactory", timelineFactory);
  }
}
