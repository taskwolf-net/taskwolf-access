package net.taskwolf.access;

import lombok.RequiredArgsConstructor;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.action.ActionDatabaseTable;
import net.taskwolf.core.condition.ConditionDatabaseTable;
import net.taskwolf.core.condition.ConditionInformationRepository;
import net.taskwolf.core.database.DatabaseConnection;
import net.taskwolf.core.database.DatabaseKeyspace;
import net.taskwolf.core.distribution.Distribution;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.template.TemplateDatabaseTable;
import net.taskwolf.core.trigger.TriggerDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserVerificationDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.WorkflowExecutionDatabaseTable;
import net.taskwolf.core.workflow.timeline.TimelineDatabaseTable;
import net.taskwolf.core.workflow.timeline.TimelineFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@RequiredArgsConstructor(staticName = "create")
public class AccessContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final DatabaseConnection databaseConnection;
  private final DatabaseKeyspace databaseKeyspace;
  private final UserDatabaseTable userDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final ConditionDatabaseTable conditionDatabaseTable;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final WorkflowExecutionDatabaseTable workflowExecutionDatabaseTable;
  private final TemplateDatabaseTable templateDatabaseTable;
  private final TimelineDatabaseTable timelineDatabaseTable;
  private final Distribution distribution;
  private final ConditionInformationRepository conditionRepository;
  private final TimelineFactory timelineFactory;
  private final CoreModule coreModule;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("databaseConnection", databaseConnection);
    beanFactory.registerSingleton("databaseKeyspace", databaseKeyspace);
    beanFactory.registerSingleton("userDatabaseTable", userDatabaseTable);
    beanFactory.registerSingleton("userVerificationDatabaseTable", userVerificationDatabaseTable);
    beanFactory.registerSingleton("organizationDatabaseTable", organizationDatabaseTable);
    beanFactory.registerSingleton("triggerDatabaseTable", triggerDatabaseTable);
    beanFactory.registerSingleton("actionDatabaseTable", actionDatabaseTable);
    beanFactory.registerSingleton("conditionDatabaseTable", conditionDatabaseTable);
    beanFactory.registerSingleton("workflowDatabaseTable", workflowDatabaseTable);
    beanFactory.registerSingleton("workflowExecutionDatabaseTable", workflowExecutionDatabaseTable);
    beanFactory.registerSingleton("templateDatabaseTable", templateDatabaseTable);
    beanFactory.registerSingleton("timelineDatabaseTable", timelineDatabaseTable);
    beanFactory.registerSingleton("distribution", distribution);
    beanFactory.registerSingleton("conditionRepository", conditionRepository);
    beanFactory.registerSingleton("timelineFactory", timelineFactory);
    beanFactory.registerSingleton("coreModule", coreModule);
  }
}
