package com.dulno.access.workflow;

import com.google.api.client.util.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.CoreModule;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.action.ActionDatabaseTable;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.condition.ConditionDatabaseTable;
import com.dulno.core.condition.ConditionEntry;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.organization.team.TeamDatabaseTable;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.trigger.TriggerDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
import com.dulno.core.workflow.WorkflowDatabaseTable;
import com.dulno.core.workflow.WorkflowEntry;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class WorkflowDuplicationController extends WorkflowController {
  private final CoreModule coreModule;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final ConditionDatabaseTable conditionDatabaseTable;

  private WorkflowDuplicationController(
    Key secretKey, UserDatabaseTable userDatabaseTable, CoreModule coreModule,
    WorkflowDatabaseTable workflowDatabaseTable,
    TriggerDatabaseTable triggerDatabaseTable, ActionDatabaseTable actionDatabaseTable,
    ConditionDatabaseTable conditionDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable, TeamDatabaseTable teamDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, workflowDatabaseTable,
      actionDatabaseTable, conditionDatabaseTable, userTargetDatabaseTable,
      teamTargetDatabaseTable, bundleDatabaseTable, teamDatabaseTable);
    this.coreModule = coreModule;
    this.triggerDatabaseTable = triggerDatabaseTable;
    this.actionDatabaseTable = actionDatabaseTable;
    this.conditionDatabaseTable = conditionDatabaseTable;
  }

  @RequestMapping(path = "/workflow/duplicate/", method = RequestMethod.POST)
  public CompletableFuture<Void> duplicateWorkflow(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return findUser(request).thenCompose(user ->
      userTargetDatabaseTable().findTargetSecured(user.id()).thenCompose(target ->
        checkWorkflowNumberLimit(user, target).thenCompose(limitReached ->
          duplicateWorkflow(user, body, limitReached, response))));
  }

  private CompletableFuture<Void> duplicateWorkflow(
    User user, DulnoRequestBody body, boolean limitReached,
    HttpServletResponse response
  ) {
    if (limitReached) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return CompletableFuture.completedFuture(null);
    }
    var futureResponse = new CompletableFuture<Void>();
    performWorkflowOperation(user.id(),
      body.getUUID("workflow"), workflow ->
        workflowDatabaseTable().generateAvailableWorkflowId()
          .thenAccept(workflowId -> triggerDatabaseTable.generateAvailableTriggerId()
            .thenAccept(triggerId -> generateActionIds(workflow.actionIds().size())
              .thenAccept(actionIds -> generateConditionIds(workflow.conditionIds().size())
                .thenAccept(conditionIds -> duplicateWorkflow(user, workflow,
                  workflowId, triggerId, actionIds, conditionIds)
                  .thenAccept(futureResponse::complete))))),
      () -> futureResponse.complete(null));
    return futureResponse;
  }

  private CompletableFuture<Void> duplicateWorkflow(
    User user, WorkflowEntry workflow, UUID duplicateWorkflowId,
    UUID duplicateTriggerId, List<UUID> duplicateActionIds,
    List<UUID> duplicateConditionIds
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(duplicateWorkflowTrigger(workflow, duplicateWorkflowId,
      duplicateTriggerId));
    processes.add(duplicateWorkflowActions(workflow, duplicateWorkflowId,
      duplicateActionIds));
    processes.add(duplicateWorkflowConditions(workflow, duplicateWorkflowId,
      duplicateConditionIds));
    var name = workflow.name() + " (" +
      coreModule.translate(user, "workflow.duplicated") + ")";
    name = name.substring(0, Math.min(64, name.length()));
    processes.add(workflowDatabaseTable().insertWorkflow(WorkflowEntry.create(
      duplicateWorkflowId, workflow.ownerId(), workflow.creatorId(),
      duplicateTriggerId, duplicateActionIds, duplicateConditionIds,
      workflow.modules(), System.currentTimeMillis(), name,
      workflow.description(), workflow.state())));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> duplicateWorkflowTrigger(
    WorkflowEntry workflow, UUID duplicateWorkflowId, UUID duplicateTriggerId
  ) {
    return triggerDatabaseTable.triggerExists(workflow.triggerId())
      .thenCompose(exists -> duplicateWorkflowTrigger(workflow,
        duplicateWorkflowId, duplicateTriggerId, exists));
  }

  private CompletableFuture<Void> duplicateWorkflowTrigger(
    WorkflowEntry workflow, UUID duplicateWorkflowId, UUID duplicateTriggerId,
    boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(null);
    }
    return triggerDatabaseTable.findTrigger(workflow.triggerId())
      .thenCompose(entry -> triggerDatabaseTable.insertTrigger(duplicateTriggerId,
          entry.ownerId(), duplicateWorkflowId, entry.module(), entry.type(),
          entry.state().toString())
        .thenCompose(value -> coreModule.findTrigger(entry.module(), entry.type())
          .map(trigger -> trigger.findContent(entry.id())
            .thenCompose(content -> trigger.insert(duplicateTriggerId, content))
            .exceptionally(throwable -> null))
          .orElse(CompletableFuture.completedFuture(null))));
  }

  private CompletableFuture<Void> duplicateWorkflowActions(
    WorkflowEntry workflow, UUID duplicateWorkflowId, List<UUID> newActionIds
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    var currentIdIndex = 0;
    for (var action : workflow.actionIds()) {
      var duplicateActionId = newActionIds.get(currentIdIndex);
      processes.add(duplicateWorkflowAction(action, duplicateWorkflowId,
        duplicateActionId));
      currentIdIndex++;
    }
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> duplicateWorkflowAction(
    UUID actionId, UUID duplicateWorkflowId, UUID duplicateActionId
  ) {
    return actionDatabaseTable.actionExists(actionId)
      .thenCompose(exists -> duplicateWorkflowAction(actionId,
        duplicateWorkflowId, duplicateActionId, exists));
  }

  private CompletableFuture<Void> duplicateWorkflowAction(
    UUID actionId, UUID duplicateWorkflowId, UUID duplicateActionId,
    boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(null);
    }
    return actionDatabaseTable.findAction(actionId)
      .thenCompose(entry -> actionDatabaseTable.insertAction(duplicateActionId,
          entry.ownerId(), duplicateWorkflowId, entry.actionIndex(),
          entry.module(), entry.type())
        .thenCompose(value -> coreModule.findAction(entry.module(), entry.type())
          .map(action -> action.findContent(entry.id())
            .thenCompose(content -> action.insert(duplicateActionId, content))
            .exceptionally(throwable -> null))
          .orElse(CompletableFuture.completedFuture(null))));
  }

  private CompletableFuture<Void> duplicateWorkflowConditions(
    WorkflowEntry workflow, UUID duplicateWorkflowId, List<UUID> conditionIds
  ) {
    return AsyncIterator.execute(workflow.conditionIds(),
      conditionDatabaseTable::findCondition).thenCompose(conditions ->
        duplicateWorkflowConditions(duplicateWorkflowId, conditions, conditionIds));
  }

  private CompletableFuture<Void> duplicateWorkflowConditions(
    UUID duplicateWorkflowId, List<ConditionEntry> conditions,
    List<UUID> conditionIds
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    for (var i = 0; i < conditions.size(); i++) {
      var condition = conditions.get(i);
      var duplicateConditionId = conditionIds.get(i);
      processes.add(conditionDatabaseTable.insertCondition(duplicateConditionId,
        condition.ownerId(), duplicateWorkflowId, condition.actionIndex(),
        condition.conditionIndex(), condition.type(), condition.content()));
    }
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }
}
