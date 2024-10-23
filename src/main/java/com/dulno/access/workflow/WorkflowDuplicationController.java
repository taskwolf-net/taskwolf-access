package com.dulno.access.workflow;

import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.CoreModule;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.action.ActionDatabaseTable;
import com.dulno.core.action.ActionEntry;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.condition.ConditionDatabaseTable;
import com.dulno.core.condition.ConditionEntry;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.organization.team.TeamDatabaseTable;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.trigger.TriggerDatabaseTable;
import com.dulno.core.trigger.TriggerEntry;
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
import java.util.Map;
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
    processes.add(workflowDatabaseTable().insertWorkflow(WorkflowEntry.create(
      duplicateWorkflowId, workflow.ownerId(), workflow.creatorId(),
      duplicateTriggerId, duplicateActionIds, duplicateConditionIds,
      workflow.modules(), System.currentTimeMillis(), workflow.name() +
        " (" + coreModule.translate(user, "workflow.duplicated") + ")",
      workflow.description(), workflow.state())));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> duplicateWorkflowTrigger(
    WorkflowEntry workflow, UUID duplicateWorkflowId, UUID duplicateTriggerId
  ) {
    return triggerDatabaseTable.findTrigger(workflow.triggerId())
      .thenCompose(trigger -> coreModule.findTrigger(trigger.module(),
          trigger.type()).get().findContent(trigger.id())
        .thenCompose(content -> duplicateWorkflowTrigger(duplicateWorkflowId,
          duplicateTriggerId, trigger, content)));
  }

  private CompletableFuture<Void> duplicateWorkflowTrigger(
    UUID duplicateWorkflowId, UUID duplicateTriggerId, TriggerEntry trigger,
    Map<String, Object> content
  ) {
    return coreModule.findTrigger(trigger.module(), trigger.type()).get()
      .insert(duplicateTriggerId, content).thenCompose(value ->
        triggerDatabaseTable.insertTrigger(duplicateTriggerId, trigger.ownerId(),
          duplicateWorkflowId, trigger.module(), trigger.type(),
          trigger.state().toString()));
  }

  private CompletableFuture<Void> duplicateWorkflowActions(
    WorkflowEntry workflow, UUID duplicateWorkflowId, List<UUID> actionIds
  ) {
    var actions = Maps.<ActionEntry, Map<String, Object>>newHashMap();
    return AsyncIterator.execute(workflow.actionIds(), actionId ->
        actionDatabaseTable.findAction(actionId)
          .thenCompose(action -> coreModule.findAction(action.module(),
              action.type()).get().findContent(actionId)
            .thenAccept(content -> actions.put(action, content)))
          .thenCompose(value -> duplicateWorkflowActions(duplicateWorkflowId,
            actions, actionIds)))
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> duplicateWorkflowActions(
    UUID duplicateWorkflowId, Map<ActionEntry, Map<String, Object>> actions,
    List<UUID> actionIds
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    var currentIdIndex = 0;
    for (var entry : actions.entrySet()) {
      var action = entry.getKey();
      var content = entry.getValue();
      var duplicateActionId = actionIds.get(currentIdIndex);
      processes.add(duplicateWorkflowAction(duplicateWorkflowId,
        duplicateActionId, action, content));
      currentIdIndex++;
    }
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> duplicateWorkflowAction(
    UUID duplicateWorkflowId, UUID duplicateActionId, ActionEntry action,
    Map<String, Object> content
  ) {
    return coreModule.findAction(action.module(), action.type()).get()
      .insert(duplicateActionId, content).thenCompose(value ->
        actionDatabaseTable.insertAction(duplicateActionId, action.ownerId(),
          duplicateWorkflowId, action.actionIndex(), action.module(),
          action.type()));
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
