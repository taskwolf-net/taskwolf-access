package com.dulno.access.workflow;

import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.action.ActionDatabaseTable;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.condition.ConditionDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.organization.team.Team;
import com.dulno.core.organization.team.TeamDatabaseTable;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
import com.dulno.core.workflow.WorkflowDatabaseTable;
import com.dulno.core.workflow.WorkflowEntry;

import java.security.Key;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Accessors(fluent = true)
public class WorkflowController extends DulnoRestController {
  @Getter(AccessLevel.PROTECTED)
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final ConditionDatabaseTable conditionDatabaseTable;
  @Getter(AccessLevel.PROTECTED)
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  @Getter(AccessLevel.PROTECTED)
  private final TeamTargetDatabaseTable teamTargetDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final TeamDatabaseTable teamDatabaseTable;

  protected WorkflowController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable,
    ActionDatabaseTable actionDatabaseTable,
    ConditionDatabaseTable conditionDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable, TeamDatabaseTable teamDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.actionDatabaseTable = actionDatabaseTable;
    this.conditionDatabaseTable = conditionDatabaseTable;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.teamTargetDatabaseTable = teamTargetDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.teamDatabaseTable = teamDatabaseTable;
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
      checkWorkflowAuthorization(user, workflow).thenAccept(authorized ->
        performWorkflowOperation(workflow, authorized, operation, failResponse)));
  }

  private void performWorkflowOperation(
    WorkflowEntry workflow, boolean authorized, Consumer<WorkflowEntry> operation,
    Runnable failResponse
  ) {
    if (!authorized) {
      failResponse.run();
      return;
    }
    operation.accept(workflow);
  }

  protected CompletableFuture<Boolean> checkWorkflowAuthorization(
    User user, WorkflowEntry workflow
  ) {
    return checkWorkflowAuthorization(user, workflow.ownerId());
  }

  protected CompletableFuture<Boolean> checkWorkflowAuthorization(
    User user, UUID workflowOwnerId
  ) {
    if (workflowOwnerId.equals(user.id()) ||
      user.organizations().contains(workflowOwnerId)
    ) {
      return CompletableFuture.completedFuture(true);
    }
    return teamTargetDatabaseTable.findTargetSecured(user.id())
      .thenApply(teamTarget -> teamTarget.map(uuid ->
        uuid.equals(workflowOwnerId)).orElse(false));
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
      } catch (Exception ignored) {
      }
    }
    return futureResponse;
  }

  protected CompletableFuture<Boolean> checkWorkflowNumberLimit(User user, UUID target) {
    return findOwnersOfTarget(user, target)
      .thenCompose(owners -> AsyncIterator.execute(owners,
          owner -> workflowDatabaseTable().findWorkflowCount(owner))
        .thenApply(sizes -> sizes.stream().mapToLong(Long::longValue).sum())
        .thenCompose(number -> bundleDatabaseTable.findBundle(target)
          .thenApply(bundle ->  bundle.workflowNumberLimit() > 0 &&
            number >= bundle.workflowNumberLimit())));
  }

  private CompletableFuture<List<UUID>> findOwnersOfTarget(User user, UUID target) {
    return user.id().equals(target) ?
      CompletableFuture.completedFuture(Lists.newArrayList(target)) :
      teamDatabaseTable.findTeamsByOrganization(target).thenApply(teams ->
        Stream.concat(teams.stream().map(Team::id).toList().stream(),
          Stream.of(target)).toList());
  }

  protected CompletableFuture<UUID> findWorkflowTarget(UUID userId) {
    return userTargetDatabaseTable.findTargetSecured(userId)
      .thenCompose(target -> findWorkflowTarget(userId, target));
  }

  private CompletableFuture<UUID> findWorkflowTarget(
    UUID userId, UUID target
  ) {
    return userId.equals(target) ? CompletableFuture.completedFuture(target) :
      teamTargetDatabaseTable.findTargetSecured(userId)
        .thenApply(team -> team.orElse(target));
  }
}
