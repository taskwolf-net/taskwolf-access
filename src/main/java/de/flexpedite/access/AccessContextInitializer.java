package de.flexpedite.access;

import de.flexpedite.core.action.ActionDatabaseTable;
import de.flexpedite.core.trigger.TriggerDatabaseTable;
import de.flexpedite.core.user.UserDatabaseTable;
import de.flexpedite.core.workflow.WorkflowDatabaseTable;
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