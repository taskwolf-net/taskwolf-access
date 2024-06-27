package net.taskwolf.access;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.action.ActionDatabaseTable;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.condition.ConditionDatabaseTable;
import net.taskwolf.core.condition.ConditionInformationRepository;
import net.taskwolf.core.database.DatabaseConnection;
import net.taskwolf.core.database.DatabaseKeyspace;
import net.taskwolf.core.module.ModuleLoader;
import net.taskwolf.core.notification.NotificationDatabaseTable;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.template.TemplateDatabaseTable;
import net.taskwolf.core.ticket.TicketDatabaseTable;
import net.taskwolf.core.ticket.TicketMessageDatabaseTable;
import net.taskwolf.core.trigger.TriggerDatabaseTable;
import net.taskwolf.core.tutorial.TutorialDatabaseTable;
import net.taskwolf.core.tutorial.level.TutorialLevelRegistry;
import net.taskwolf.core.user.*;
import net.taskwolf.core.whitelist.WhitelistConfiguration;
import net.taskwolf.core.worker.WorkerDistribution;
import net.taskwolf.core.worker.client.WorkerProxyClient;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.WorkflowExecutionDatabaseTable;
import net.taskwolf.core.workflow.timeline.TimelineDatabaseTable;
import net.taskwolf.core.workflow.timeline.TimelineFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.security.Key;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class AccessContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final Key key;
  private final ModuleLoader moduleLoader;
  private final DatabaseConnection databaseConnection;
  private final DatabaseKeyspace databaseKeyspace;
  private final UserDatabaseTable userDatabaseTable;
  private final UserVerificationDatabaseTable userVerificationDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final UserPasswordResetDatabaseTable userPasswordResetDatabaseTable;
  private final UserEmailChangeDatabaseTable userEmailChangeDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final ConditionDatabaseTable conditionDatabaseTable;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final WorkflowExecutionDatabaseTable workflowExecutionDatabaseTable;
  private final TemplateDatabaseTable templateDatabaseTable;
  private final TimelineDatabaseTable timelineDatabaseTable;
  private final TicketDatabaseTable ticketDatabaseTable;
  private final TicketMessageDatabaseTable ticketMessageDatabaseTable;
  private final NotificationDatabaseTable notificationDatabaseTable;
  private final TutorialDatabaseTable tutorialDatabaseTable;
  private final TutorialLevelRegistry tutorialLevelRegistry;
  private final WhitelistConfiguration whitelistConfiguration;
  private final WorkerDistribution distribution;
  private final WorkerProxyClient workerProxyClient;
  private final ConditionInformationRepository conditionRepository;
  private final TimelineFactory timelineFactory;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final CoreModule coreModule;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("key", key);
    beanFactory.registerSingleton("moduleLoader", moduleLoader);
    beanFactory.registerSingleton("databaseConnection", databaseConnection);
    beanFactory.registerSingleton("databaseKeyspace", databaseKeyspace);
    beanFactory.registerSingleton("userDatabaseTable", userDatabaseTable);
    beanFactory.registerSingleton("userVerificationDatabaseTable", userVerificationDatabaseTable);
    beanFactory.registerSingleton("userTargetDatabaseTable", userTargetDatabaseTable);
    beanFactory.registerSingleton("userPasswordResetDatabaseTable", userPasswordResetDatabaseTable);
    beanFactory.registerSingleton("userEmailChangeDatabaseTable", userEmailChangeDatabaseTable);
    beanFactory.registerSingleton("organizationDatabaseTable", organizationDatabaseTable);
    beanFactory.registerSingleton("triggerDatabaseTable", triggerDatabaseTable);
    beanFactory.registerSingleton("actionDatabaseTable", actionDatabaseTable);
    beanFactory.registerSingleton("conditionDatabaseTable", conditionDatabaseTable);
    beanFactory.registerSingleton("workflowDatabaseTable", workflowDatabaseTable);
    beanFactory.registerSingleton("workflowExecutionDatabaseTable", workflowExecutionDatabaseTable);
    beanFactory.registerSingleton("templateDatabaseTable", templateDatabaseTable);
    beanFactory.registerSingleton("timelineDatabaseTable", timelineDatabaseTable);
    beanFactory.registerSingleton("ticketDatabaseTable", ticketDatabaseTable);
    beanFactory.registerSingleton("ticketMessageDatabaseTable", ticketMessageDatabaseTable);
    beanFactory.registerSingleton("notificationDatabaseTable", notificationDatabaseTable);
    beanFactory.registerSingleton("tutorialDatabaseTable", tutorialDatabaseTable);
    beanFactory.registerSingleton("tutorialLevelRegistry", tutorialLevelRegistry);
    beanFactory.registerSingleton("whitelistConfiguration", whitelistConfiguration);
    beanFactory.registerSingleton("distribution", distribution);
    beanFactory.registerSingleton("workerProxyClient", workerProxyClient);
    beanFactory.registerSingleton("conditionRepository", conditionRepository);
    beanFactory.registerSingleton("timelineFactory", timelineFactory);
    beanFactory.registerSingleton("bundleDatabaseTable", bundleDatabaseTable);
    beanFactory.registerSingleton("coreModule", coreModule);
  }
}
