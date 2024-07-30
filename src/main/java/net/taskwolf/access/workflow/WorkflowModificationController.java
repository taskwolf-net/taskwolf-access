package net.taskwolf.access.workflow;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.action.ActionDatabaseTable;
import net.taskwolf.core.action.ActionEntry;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.condition.ConditionDatabaseTable;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.core.trigger.TriggerDatabaseTable;
import net.taskwolf.core.trigger.TriggerEntry;
import net.taskwolf.core.trigger.TriggerState;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.workflow.*;
import net.taskwolf.core.workflow.timeline.TimelineDatabaseEntry;
import net.taskwolf.core.workflow.timeline.TimelineDatabaseTable;
import org.json.JSONObject;
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
public final class WorkflowModificationController extends WorkflowController {
  private final CoreModule coreModule;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final ConditionDatabaseTable conditionDatabaseTable;
  private final WorkflowExecutionDatabaseTable workflowExecutionDatabaseTable;
  private final TimelineDatabaseTable timelineDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;

  private WorkflowModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable, CoreModule coreModule,
    WorkflowDatabaseTable workflowDatabaseTable,
    TriggerDatabaseTable triggerDatabaseTable, ActionDatabaseTable actionDatabaseTable,
    ConditionDatabaseTable conditionDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    WorkflowExecutionDatabaseTable workflowExecutionDatabaseTable,
    TimelineDatabaseTable timelineDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, workflowDatabaseTable,
      actionDatabaseTable, conditionDatabaseTable, userTargetDatabaseTable,
      teamTargetDatabaseTable);
    this.coreModule = coreModule;
    this.triggerDatabaseTable = triggerDatabaseTable;
    this.actionDatabaseTable = actionDatabaseTable;
    this.conditionDatabaseTable = conditionDatabaseTable;
    this.workflowExecutionDatabaseTable = workflowExecutionDatabaseTable;
    this.timelineDatabaseTable = timelineDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
  }

  private static final long MAX_WORKFLOW_BYTES = 500 * 1000;

  @RequestMapping(path = "/workflow/add/", method = RequestMethod.POST)
  public CompletableFuture<Void> addWorkflow(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    if (payload.getBytes().length > MAX_WORKFLOW_BYTES) {
      response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
      return CompletableFuture.completedFuture(null);
    }
    var body = TaskwolfRequestBody.of(payload, response);
    return findUser(request).thenCompose(user ->
      userTargetDatabaseTable().findTargetSecured(user.id()).thenCompose(target ->
        checkWorkflowNumberLimit(target).thenAccept(limitReached ->
          addWorkflow(user, target, body, limitReached, response))));
  }

  private CompletableFuture<Boolean> checkWorkflowNumberLimit(UUID target) {
    return bundleDatabaseTable.findBundle(target).thenCompose(bundle ->
      workflowDatabaseTable().findWorkflowsOfOwner(target).thenApply(
        workflows -> bundle.workflowNumberLimit() > 0 &&
          workflows.size() >= bundle.workflowNumberLimit()));
  }

  private void addWorkflow(
    User user, UUID target, TaskwolfRequestBody body, boolean limitReached,
    HttpServletResponse response
  ) {
    if (limitReached) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    var created = System.currentTimeMillis();
    findTeam(user, target).thenAccept(team ->
      createWorkflowCreateTimelineEntry(user).thenAccept(workflowCreateEntry ->
        createWorkflow(user, target, team, body.getObject("trigger"),
          body.getObjectList("actions"), body.getObjectList("conditions"),
          created, body.getString("name"), body.getString("description"),
          Lists.newArrayList(), Lists.newArrayList(workflowCreateEntry),
          WorkflowState.OPERATIONAL)));
  }

  private CompletableFuture<UUID> findTeam(User user, UUID target) {
    return user.id().equals(target) ?
      CompletableFuture.completedFuture(null) :
      teamTargetDatabaseTable().findTargetSecured(user.id())
        .thenApply(team -> team.orElse(null));
  }

  private CompletableFuture<TimelineDatabaseEntry> createWorkflowCreateTimelineEntry(
    User creator
  ) {
    return timelineDatabaseTable.generateAvailableEntryId().thenApply(id ->
      TimelineDatabaseEntry.create(id, null, System.currentTimeMillis(),
        "timeline-workflow-create", new JSONObject(Map.of("creator",
          creator.id().toString())).toString()));
  }

  @RequestMapping(path = "/workflow/update/", method = RequestMethod.POST)
  public void updateWorkflow(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    if (payload.getBytes().length > MAX_WORKFLOW_BYTES) {
      response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
      return;
    }
    var body = TaskwolfRequestBody.of(payload, response);
    var workflowId = body.getUUID("workflow");
    findUser(request).thenAccept(user -> workflowDatabaseTable().findWorkflow(workflowId)
      .thenAccept(workflow -> workflowExecutionDatabaseTable.findWorkflowExecutions(workflowId)
        .thenAccept(executions -> timelineDatabaseTable.findEntriesByWorkflow(workflowId)
          .thenAccept(timelineEntries -> checkWorkflowTeamMatch(user.id(), workflow)
            .thenAccept(teamMatch -> updateWorkflow(user, workflow, teamMatch,
              body.getObject("trigger"), body.getObjectList("actions"),
              body.getObjectList("conditions"), body.getString("name"),
              body.getString("description"), executions, timelineEntries,
              workflow.state()))))));
  }

  private void updateWorkflow(
    User user, WorkflowEntry entry,  boolean teamMatch,
    TaskwolfRequestBody triggerData, List<TaskwolfRequestBody> actionData,
    List<TaskwolfRequestBody> conditionData, String name, String description,
    List<Long> executions, List<TimelineDatabaseEntry> timelineEntries,
    WorkflowState state
  ) {
    if (!checkWorkflowAuthorization(user, entry) || !teamMatch) {
      return;
    }
    deleteWorkflow(user, entry);
    userDatabaseTable().findUser(entry.creatorId()).thenAccept(creator ->
      workflowDatabaseTable().generateAvailableWorkflowId().thenAccept(workflowId ->
        updateWorkflow(workflowId, user, creator, entry, triggerData, actionData,
          conditionData, name, description, executions, timelineEntries, state)));
  }

  private void updateWorkflow(
    UUID workflowId, User user, User creator, WorkflowEntry entry,
    TaskwolfRequestBody triggerData, List<TaskwolfRequestBody> actionData,
    List<TaskwolfRequestBody> conditionData, String name, String description,
    List<Long> executions, List<TimelineDatabaseEntry> timelineEntries,
    WorkflowState state
  ) {
    createWorkflow(workflowId, creator, entry.ownerId(), entry.teamId(),
      triggerData, actionData, conditionData, entry.created(), name, description,
      executions, timelineEntries, state);
    WorkflowAlterationSupervisor.create(timelineDatabaseTable, workflowId,
      entry, name, description, actionData, conditionData).evaluate(user);
  }

  private void createWorkflow(
    User creator, UUID ownerId, UUID teamId, TaskwolfRequestBody triggerData,
    List<TaskwolfRequestBody> actionData,  List<TaskwolfRequestBody> conditionData,
    long created, String name, String description, List<Long> executions,
    List<TimelineDatabaseEntry> timelineEntries, WorkflowState state
  ) {
    if (!checkWorkflowAuthorization(creator, ownerId)) {
      return;
    }
    workflowDatabaseTable().generateAvailableWorkflowId().thenAccept(workflowId ->
      createWorkflow(workflowId, creator, ownerId, teamId, triggerData, actionData,
        conditionData, created, name, description, executions, timelineEntries, state));
  }

  private void createWorkflow(
    UUID workflowId, User creator, UUID ownerId, UUID teamId,
    TaskwolfRequestBody triggerData, List<TaskwolfRequestBody> actionData,
    List<TaskwolfRequestBody> conditionData, long created, String name,
    String description, List<Long> executions,
    List<TimelineDatabaseEntry> timelineEntries, WorkflowState state
  ) {
    triggerDatabaseTable.generateAvailableTriggerId().thenAccept(triggerId ->
      generateActionIds(actionData.size()).thenAccept(actionIds ->
        generateConditionIds(conditionData.size()).thenAccept(conditionIds ->
          createWorkflow(workflowId, creator.id(), ownerId, teamId, triggerId,
            triggerData, actionIds, actionData, conditionIds, conditionData,
            created, name, description, executions, timelineEntries, state))));
  }

  private void createWorkflow(
    UUID workflowId, UUID creatorId, UUID ownerId, UUID teamId,
    UUID triggerId, TaskwolfRequestBody triggerData, List<UUID> actionIds,
    List<TaskwolfRequestBody> actionData, List<UUID> conditionIds,
    List<TaskwolfRequestBody> conditionData, long created, String name,
    String description, List<Long> executions,
    List<TimelineDatabaseEntry> timelineEntries, WorkflowState state
  ) {
    var modules = Lists.<String>newArrayList();
    createTrigger(triggerId, ownerId, workflowId, triggerData);
    modules.add(triggerData.getString("module"));
    for (int i = 0; i < actionData.size(); i++) {
      createAction(actionIds.get(i), ownerId, workflowId, actionData.get(i));
      modules.add(actionData.get(i).getString("module"));
    }
    for (int i = 0; i < conditionData.size(); i++) {
      createCondition(conditionIds.get(i), ownerId, workflowId, conditionData.get(i));
    }
    workflowDatabaseTable().insertWorkflow(workflowId, creatorId, ownerId, teamId,
      triggerId, actionIds, conditionIds, modules, created, name, description,
      state.toString());
    workflowExecutionDatabaseTable.insertWorkflowExecution(workflowId, executions);
    for (var entry : timelineEntries) {
      timelineDatabaseTable.insertEntry(entry.id(), workflowId, entry.time(),
        entry.type(), entry.content());
    }
  }

  private void createTrigger(
    UUID triggerId, UUID ownerId, UUID workflowId, TaskwolfRequestBody triggerData
  ) {
    var module = triggerData.getString("module");
    var type = triggerData.getString("type");
    triggerDatabaseTable.insertTrigger(triggerId, ownerId, workflowId, module,
      type, TriggerState.ARMED.toString());
    coreModule.findTrigger(module, type).ifPresent(trigger -> trigger.insert(
      triggerId, new JSONObject(triggerData.getString("content")).toMap()));
  }

  private void createAction(
    UUID actionId, UUID ownerId, UUID workflowId, TaskwolfRequestBody actionData
  ) {
    var module = actionData.getString("module");
    var type = actionData.getString("type");
    actionDatabaseTable.insertAction(actionId, ownerId, workflowId,
      actionData.getInt("index"), module, type);
    coreModule.findAction(module, type).ifPresent(action -> action.insert(
      actionId, new JSONObject(actionData.getString("content")).toMap()));
  }

  private void createCondition(
    UUID conditionId, UUID ownerId, UUID workflowId, TaskwolfRequestBody conditionData
  ) {
    conditionDatabaseTable.insertCondition(conditionId, ownerId, workflowId,
      conditionData.getInt("actionIndex"), conditionData.getInt("conditionIndex"),
      conditionData.getString("type"), conditionData.getString("content"));
  }

  @RequestMapping(path = "/workflow/state/change/", method = RequestMethod.POST)
  public void changeWorkflowState(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var isArmed = body.getBoolean("armed") ? TriggerState.ARMED :
      TriggerState.DISABLED;
    performWorkflowOperation(findUserId(request), body.getUUID("workflow"),
      workflow -> triggerDatabaseTable.changeState(workflow.triggerId(), isArmed),
      () -> {});
  }

  @RequestMapping(path = "/workflow/remove/", method = RequestMethod.POST)
  public void removeWorkflow(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    performWorkflowOperation(findUserId(request), body.getUUID("workflow"),
      this::deleteWorkflow, () -> {});
  }

  private void deleteWorkflow(User user, WorkflowEntry workflow) {
    if (!checkWorkflowAuthorization(user, workflow)) {
      return;
    }
    deleteWorkflow(workflow);
  }

  public void deleteWorkflow(WorkflowEntry workflow) {
    AsyncIterator.execute(workflow.actionIds(), actionDatabaseTable::findAction)
      .thenAccept(actions -> triggerDatabaseTable.findTrigger(workflow.triggerId())
        .thenAccept(trigger -> deleteWorkflow(workflow, trigger, actions)));
  }

  private void deleteWorkflow(
    WorkflowEntry workflow, TriggerEntry trigger, List<ActionEntry> actions
  ) {
    workflowDatabaseTable().deleteWorkflow(workflow.id());
    triggerDatabaseTable.deleteTrigger(workflow.triggerId());
    coreModule.findTrigger(trigger.module(), trigger.type()).ifPresent(value ->
      value.delete(trigger.id()));
    for (var action : actions) {
      actionDatabaseTable.deleteAction(action.id());
      coreModule.findAction(action.module(), action.type()).ifPresent(value ->
        value.delete(action.id()));
    }
    for (var condition : workflow.conditionIds()) {
      conditionDatabaseTable.deleteCondition(condition);
    }
    workflowExecutionDatabaseTable.deleteWorkflowExecutions(workflow.id());
    timelineDatabaseTable.findEntriesByWorkflow(workflow.id()).thenAccept(entries ->
      entries.forEach(timelineDatabaseEntry ->
        timelineDatabaseTable.deleteEntry(timelineDatabaseEntry.id())));
  }
}
