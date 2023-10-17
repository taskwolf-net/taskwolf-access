package net.taskwolf.access;

import net.taskwolf.core.action.ActionDatabaseTable;
import net.taskwolf.core.trigger.TriggerDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

import java.security.Key;

@RequiredArgsConstructor(staticName = "create")
public class AccessContextInitializer implements ApplicationContextInitializer<GenericApplicationContext> {
  private final Key secretKey;
  private final UserDatabaseTable userDatabaseTable;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final WorkflowDatabaseTable workflowDatabaseTable;

  @Override
  public void initialize(GenericApplicationContext context) {
    context.getBeanFactory().registerSingleton("secretKey", secretKey);
    context.getBeanFactory().registerSingleton("userDatabaseTable",
      userDatabaseTable);
    context.getBeanFactory().registerSingleton("triggerDatabaseTable",
      triggerDatabaseTable);
    context.getBeanFactory().registerSingleton("actionDatabaseTable",
      actionDatabaseTable);
    context.getBeanFactory().registerSingleton("workflowDatabaseTable",
      workflowDatabaseTable);
  }
}