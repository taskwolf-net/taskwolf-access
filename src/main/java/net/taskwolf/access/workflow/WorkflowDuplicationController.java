package net.taskwolf.access.workflow;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.action.ActionDatabaseTable;
import net.taskwolf.core.action.ActionEntry;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.condition.ConditionDatabaseTable;
import net.taskwolf.core.condition.ConditionEntry;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.organization.team.TeamDatabaseTable;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.core.trigger.TriggerDatabaseTable;
import net.taskwolf.core.trigger.TriggerEntry;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.WorkflowEntry;
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
    var body = TaskwolfRequestBody.of(payload, response);
    return findUser(request).thenCompose(user ->
      userTargetDatabaseTable().findTargetSecured(user.id()).thenCompose(target ->
        checkWorkflowNumberLimit(user, target).thenAccept(limitReached ->
          duplicateWorkflow(user, body, limitReached, response))));
  }

  private void duplicateWorkflow(
    User user, TaskwolfRequestBody body, boolean limitReached,
    HttpServletResponse response
  ) {
    if (limitReached) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    performWorkflowOperation(user.id(),
      body.getUUID("workflow"), workflow ->
        workflowDatabaseTable().generateAvailableWorkflowId()
          .thenAccept(workflowId -> triggerDatabaseTable.generateAvailableTriggerId()
            .thenAccept(triggerId -> generateActionIds(workflow.actionIds().size())
              .thenAccept(actionIds -> generateConditionIds(workflow.conditionIds().size())
                .thenAccept(conditionIds -> duplicateWorkflow(user, workflow,
                  workflowId, triggerId, actionIds, conditionIds))))),
      () -> {});
  }

  private void duplicateWorkflow(
    User user, WorkflowEntry workflow, UUID duplicateWorkflowId,
    UUID duplicateTriggerId, List<UUID> duplicateActionIds,
    List<UUID> duplicateConditionIds
  ) {
    workflowDatabaseTable().insertWorkflow(WorkflowEntry.create(duplicateWorkflowId,
      workflow.creatorId(), workflow.ownerId(), duplicateTriggerId,
      duplicateActionIds, duplicateConditionIds, workflow.modules(),
      System.currentTimeMillis(), workflow.name() +
        " (" + coreModule.translate(user, "workflow.duplicated") + ")",
      workflow.description(), workflow.state()));
    duplicateWorkflowTrigger(workflow, duplicateWorkflowId, duplicateTriggerId);
    duplicateWorkflowActions(workflow, duplicateWorkflowId, duplicateActionIds);
    duplicateWorkflowConditions(workflow, duplicateWorkflowId, duplicateConditionIds);
  }

  private void duplicateWorkflowTrigger(
    WorkflowEntry workflow, UUID duplicateWorkflowId, UUID duplicateTriggerId
  ) {
    triggerDatabaseTable.findTrigger(workflow.triggerId()).thenAccept(trigger ->
      coreModule.findTrigger(trigger.module(), trigger.type()).get()
        .findContent(trigger.id()).thenAccept(content -> duplicateWorkflowTrigger(
          duplicateWorkflowId, duplicateTriggerId, trigger, content)));
  }

  private void duplicateWorkflowTrigger(
    UUID duplicateWorkflowId, UUID duplicateTriggerId, TriggerEntry trigger,
    Map<String, Object> content
  ) {
    coreModule.findTrigger(trigger.module(), trigger.type()).get()
      .insert(duplicateTriggerId, content);
    triggerDatabaseTable.insertTrigger(duplicateTriggerId, trigger.ownerId(),
      duplicateWorkflowId, trigger.module(), trigger.type(),
      trigger.state().toString());
  }

  private void duplicateWorkflowActions(
    WorkflowEntry workflow, UUID duplicateWorkflowId, List<UUID> actionIds
  ) {
    var actions = Maps.<ActionEntry, Map<String, Object>>newHashMap();
    AsyncIterator.execute(workflow.actionIds(), actionId ->
        actionDatabaseTable.findAction(actionId).thenCompose(action ->
          coreModule.findAction(action.module(), action.type())
            .get().findContent(actionId).thenAccept(content ->
              actions.put(action, content))).thenAccept(
      value -> duplicateWorkflowActions(duplicateWorkflowId, actions, actionIds)));
  }

  private void duplicateWorkflowActions(
    UUID duplicateWorkflowId, Map<ActionEntry, Map<String, Object>> actions,
    List<UUID> actionIds
  ) {
    var currentIdIndex = 0;
    for (var entry : actions.entrySet()) {
      var action = entry.getKey();
      var content = entry.getValue();
      var duplicateActionId = actionIds.get(currentIdIndex);
      coreModule.findAction(action.module(), action.type()).get()
        .insert(duplicateActionId, content);
      actionDatabaseTable.insertAction(duplicateActionId, action.ownerId(),
        duplicateWorkflowId, action.actionIndex(), action.module(), action.type());
      currentIdIndex++;
    }
  }

  private void duplicateWorkflowConditions(
    WorkflowEntry workflow, UUID duplicateWorkflowId, List<UUID> conditionIds
  ) {
    AsyncIterator.execute(workflow.conditionIds(),
      conditionDatabaseTable::findCondition).thenAccept(conditions ->
        duplicateWorkflowConditions(duplicateWorkflowId, conditions, conditionIds));
  }

  private void duplicateWorkflowConditions(
    UUID duplicateWorkflowId, List<ConditionEntry> conditions,
    List<UUID> conditionIds
  ) {
    for (var i = 0; i < conditions.size(); i++) {
      var condition = conditions.get(i);
      var duplicateConditionId = conditionIds.get(i);
      conditionDatabaseTable.insertCondition(duplicateConditionId, condition.ownerId(),
        duplicateWorkflowId, condition.actionIndex(), condition.conditionIndex(),
        condition.type(), condition.content());
    }
  }
}
