package net.taskwolf.access.workflow;

import net.taskwolf.core.locale.Translation;
import net.taskwolf.workflow.WorkflowModule;
import net.taskwolf.workflow.loop.LoopDatabaseTable;
import com.google.api.client.util.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.workflow.action.ActionDatabaseTable;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.workflow.condition.ConditionDatabaseTable;
import net.taskwolf.workflow.condition.ConditionEntry;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.organization.team.TeamDatabaseTable;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.workflow.trigger.TriggerDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.workflow.structure.WorkflowDatabaseTable;
import net.taskwolf.workflow.structure.WorkflowEntry;
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
  private final Translation translation;
  private final WorkflowModule workflowModule;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final ConditionDatabaseTable conditionDatabaseTable;
  private final LoopDatabaseTable loopDatabaseTable;

  private WorkflowDuplicationController(
    Key secretKey, UserDatabaseTable userDatabaseTable, Translation translation,
    WorkflowModule workflowModule, WorkflowDatabaseTable workflowDatabaseTable,
    TriggerDatabaseTable triggerDatabaseTable, ActionDatabaseTable actionDatabaseTable,
    ConditionDatabaseTable conditionDatabaseTable, LoopDatabaseTable loopDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable, TeamDatabaseTable teamDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, workflowDatabaseTable,
      actionDatabaseTable, conditionDatabaseTable, userTargetDatabaseTable,
      teamTargetDatabaseTable, bundleDatabaseTable, teamDatabaseTable);
    this.translation = translation;
    this.workflowModule = workflowModule;
    this.triggerDatabaseTable = triggerDatabaseTable;
    this.actionDatabaseTable = actionDatabaseTable;
    this.conditionDatabaseTable = conditionDatabaseTable;
    this.loopDatabaseTable = loopDatabaseTable;
  }

  @RequestMapping(path = "/workflow/duplicate/", method = RequestMethod.POST)
  public CompletableFuture<Void> duplicateWorkflow(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    return findUser(request).thenCompose(user ->
      userTargetDatabaseTable().findTargetSecured(user.id()).thenCompose(target ->
        checkWorkflowNumberLimit(user, target).thenCompose(limitReached ->
          duplicateWorkflow(user, body, limitReached, response))));
  }

  private CompletableFuture<Void> duplicateWorkflow(
    User user, TaskwolfRequestBody body, boolean limitReached,
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
                .thenAccept(conditionIds -> generateLoopId(workflow)
                  .thenAccept(loopId -> duplicateWorkflow(user, workflow,
                    workflowId, triggerId, actionIds, conditionIds, loopId)
                    .thenAccept(futureResponse::complete)))))),
      () -> futureResponse.complete(null));
    return futureResponse;
  }

  private CompletableFuture<UUID> generateLoopId(WorkflowEntry workflowEntry) {
    if (workflowEntry.loopId() == null) {
      return CompletableFuture.completedFuture(null);
    }
    return loopDatabaseTable.generateAvailableLoopId();
  }

  private CompletableFuture<Void> duplicateWorkflow(
    User user, WorkflowEntry workflow, UUID duplicateWorkflowId,
    UUID duplicateTriggerId, List<UUID> duplicateActionIds,
    List<UUID> duplicateConditionIds, UUID duplicateLoopId
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(duplicateWorkflowTrigger(workflow, duplicateWorkflowId,
      duplicateTriggerId));
    processes.add(duplicateWorkflowActions(workflow, duplicateWorkflowId,
      duplicateActionIds));
    processes.add(duplicateWorkflowConditions(workflow, duplicateWorkflowId,
      duplicateConditionIds));
    if (duplicateLoopId != null) {
      processes.add(duplicateWorkflowLoop(workflow, duplicateWorkflowId,
        duplicateLoopId));
    }
    var name = workflow.name() + " (" +
      translation.translate(user, "workflow.duplicated") + ")";
    name = name.substring(0, Math.min(64, name.length()));
    processes.add(workflowDatabaseTable().insertWorkflow(WorkflowEntry.create(
      duplicateWorkflowId, workflow.ownerId(), workflow.creatorId(),
      duplicateTriggerId, duplicateActionIds, duplicateConditionIds,
      duplicateLoopId, workflow.modules(), workflow.timeZone(),
      workflow.timeLocale(), System.currentTimeMillis(), name,
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
        .thenCompose(value -> workflowModule.findTrigger(entry.module(), entry.type())
          .map(trigger -> trigger.findContent(entry.id())
            .thenCompose(content -> trigger.insert(duplicateTriggerId,
              workflow.ownerId(), content))
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
      processes.add(duplicateWorkflowAction(action, workflow.ownerId(),
        duplicateWorkflowId, duplicateActionId));
      currentIdIndex++;
    }
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> duplicateWorkflowAction(
    UUID actionId, UUID ownerId, UUID duplicateWorkflowId, UUID duplicateActionId
  ) {
    return actionDatabaseTable.actionExists(actionId)
      .thenCompose(exists -> duplicateWorkflowAction(actionId, ownerId,
        duplicateWorkflowId, duplicateActionId, exists));
  }

  private CompletableFuture<Void> duplicateWorkflowAction(
    UUID actionId, UUID ownerId, UUID duplicateWorkflowId, UUID duplicateActionId,
    boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(null);
    }
    return actionDatabaseTable.findAction(actionId)
      .thenCompose(entry -> actionDatabaseTable.insertAction(duplicateActionId,
          entry.ownerId(), duplicateWorkflowId, entry.module(), entry.type(),
          entry.index())
        .thenCompose(value -> workflowModule.findAction(entry.module(), entry.type())
          .map(action -> action.findContent(entry.id())
            .thenCompose(content -> action.insert(duplicateActionId, ownerId,
              content))
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
        condition.ownerId(), duplicateWorkflowId, condition.type(),
        condition.content(), condition.index()));
    }
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> duplicateWorkflowLoop(
    WorkflowEntry workflow, UUID duplicateWorkflowId, UUID loopId
  ) {
    return loopDatabaseTable.findLoop(workflow.loopId())
      .thenCompose(loop -> loopDatabaseTable.insertLoop(loopId, loop.ownerId(),
        duplicateWorkflowId, loop.type(), loop.content(), loop.index()));
  }
}
