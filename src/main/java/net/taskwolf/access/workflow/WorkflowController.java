package net.taskwolf.access.workflow;

import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.action.ActionDatabaseTable;
import net.taskwolf.core.condition.ConditionDatabaseTable;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.WorkflowEntry;

import java.security.Key;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Accessors(fluent = true)
public class WorkflowController extends TaskwolfRestController {
  @Getter(AccessLevel.PROTECTED)
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final ConditionDatabaseTable conditionDatabaseTable;
  @Getter(AccessLevel.PROTECTED)
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final TeamTargetDatabaseTable teamTargetDatabaseTable;

  protected WorkflowController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable,
    ActionDatabaseTable actionDatabaseTable,
    ConditionDatabaseTable conditionDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.actionDatabaseTable = actionDatabaseTable;
    this.conditionDatabaseTable = conditionDatabaseTable;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.teamTargetDatabaseTable = teamTargetDatabaseTable;
  }

  protected void performWorkflowOperation(
    UUID userId, UUID workflowId, Consumer<WorkflowEntry> operation,
    Runnable failResponse
  ) {
    userDatabaseTable().findUser(userId).thenAccept(user ->
      performWorkflowOperation(user, workflowId, operation, failResponse));
  }

  protected void performWorkflowOperation(
    User user, UUID workflowId, Consumer<WorkflowEntry> operation,
    Runnable failResponse
  ) {
    workflowDatabaseTable.workflowExists(workflowId).thenAccept(exists ->
      performWorkflowOperation(user, workflowId, exists, operation,
        failResponse));
  }

  private void performWorkflowOperation(
    User user, UUID workflowId, boolean workflowExists,
    Consumer<WorkflowEntry> operation, Runnable failResponse
  ) {
    if (!workflowExists) {
      failResponse.run();
      return;
    }
    workflowDatabaseTable.findWorkflow(workflowId).thenAccept(workflow ->
      performWorkflowOperation(user, workflow, operation, failResponse));
  }

  private void performWorkflowOperation(
    User user, WorkflowEntry workflow, Consumer<WorkflowEntry> operation,
    Runnable failResponse
  ) {
    if (!checkWorkflowAuthorization(user, workflow)) {
      failResponse.run();
      return;
    }
    operation.accept(workflow);
  }

  protected boolean checkWorkflowAuthorization(User user, WorkflowEntry workflow) {
    return checkWorkflowAuthorization(user, workflow.ownerId());
  }

  protected boolean checkWorkflowAuthorization(User user, UUID workflowOwnerId) {
    return workflowOwnerId.equals(user.id()) ||
      user.organizations().contains(workflowOwnerId);
  }

  protected CompletableFuture<List<UUID>> generateActionIds(int number) {
    return generateMultipleIds(number, actionDatabaseTable::generateAvailableActionId);
  }

  protected CompletableFuture<List<UUID>> generateConditionIds(int number) {
    return generateMultipleIds(number, conditionDatabaseTable::generateAvailableConditionId);
  }

  protected CompletableFuture<List<UUID>> generateMultipleIds(
    int number, Callable<CompletableFuture<UUID>> generator
  ) {
    var futureResponse = new CompletableFuture<List<UUID>>();
    var ids = Collections.synchronizedList(Lists.<UUID>newArrayList());
    if (number == 0) {
      futureResponse.complete(ids);
      return futureResponse;
    }
    for (int i = 0; i < number; i++) {
      try {
        generator.call().thenAccept(ids::add)
          .thenApply(value -> ids.size() == number &&
            futureResponse.complete(ids));
      } catch (Exception exception) {
        exception.printStackTrace();
      }
    }
    return futureResponse;
  }

  protected CompletableFuture<List<WorkflowEntry>> findViewableWorkflows(UUID userId) {
    return userTargetDatabaseTable.findTargetSecured(userId)
      .thenCompose(target -> findViewableWorkflows(userId, target));
  }

  protected CompletableFuture<List<WorkflowEntry>> findViewableWorkflows(
    UUID userId, UUID target
  ) {
    return userId.equals(target) ?
      workflowDatabaseTable.findWorkflowsOfOwner(target) :
      teamTargetDatabaseTable.findTargetSecured(userId)
        .thenCompose(team -> team.isEmpty() ?
          workflowDatabaseTable.findGlobalOrganizationWorkflows(target) :
          workflowDatabaseTable.findOrganizationTeamWorkflows(target, team.get()));
  }
}
