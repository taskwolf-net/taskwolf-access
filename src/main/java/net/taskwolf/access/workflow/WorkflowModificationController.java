package net.taskwolf.access.workflow;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.action.ActionDatabaseTable;
import net.taskwolf.core.condition.ConditionDatabaseTable;
import net.taskwolf.core.trigger.TriggerDatabaseTable;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

@RestController
public final class WorkflowModificationController extends TaskwolfRestController {
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final ConditionDatabaseTable conditionDatabaseTable;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final WorkflowExecutionDatabaseTable workflowExecutionDatabaseTable;
  private final TimelineDatabaseTable timelineDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;

  private WorkflowModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    TriggerDatabaseTable triggerDatabaseTable, ActionDatabaseTable actionDatabaseTable,
    ConditionDatabaseTable conditionDatabaseTable, WorkflowDatabaseTable workflowDatabaseTable,
    WorkflowExecutionDatabaseTable workflowExecutionDatabaseTable,
    TimelineDatabaseTable timelineDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.triggerDatabaseTable = triggerDatabaseTable;
    this.actionDatabaseTable = actionDatabaseTable;
    this.conditionDatabaseTable = conditionDatabaseTable;
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.workflowExecutionDatabaseTable = workflowExecutionDatabaseTable;
    this.timelineDatabaseTable = timelineDatabaseTable;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
  }

  @RequestMapping(path = "/workflow/add/", method = RequestMethod.POST)
  public void addWorkflow(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var created = System.currentTimeMillis();
    findUser(request).thenAccept(user ->
      userTargetDatabaseTable.findTargetSecured(user.id()).thenAccept(target ->
        createWorkflowCreateTimelineEntry(user).thenAccept(workflowCreateEntry ->
          createWorkflow(user, target, body.getObject("trigger"),
            body.getObjectList("actions"), body.getObjectList("conditions"),
            created, body.getString("name"), body.getString("description"),
            Lists.newArrayList(), Lists.newArrayList(workflowCreateEntry),
            WorkflowState.OPERATIONAL))));
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
    var body = TaskwolfRequestBody.of(payload, response);
    var workflowId = body.getUUID("workflow");
    findUser(request).thenAccept(user -> workflowDatabaseTable.findWorkflow(workflowId)
      .thenAccept(workflow -> workflowExecutionDatabaseTable.findWorkflowExecutions(workflowId)
        .thenAccept(executions -> timelineDatabaseTable.findEntriesByWorkflow(workflowId)
          .thenAccept(timelineEntries -> updateWorkflow(user, workflow,
            body.getObject("trigger"), body.getObjectList("actions"),
            body.getObjectList("conditions"), body.getString("name"),
            body.getString("description"), executions, timelineEntries,
            workflow.state())))));
  }

  private void updateWorkflow(
    User user, WorkflowEntry entry, TaskwolfRequestBody triggerData,
    List<TaskwolfRequestBody> actionData, List<TaskwolfRequestBody> conditionData,
    String name, String description, List<Long> executions,
    List<TimelineDatabaseEntry> timelineEntries, WorkflowState state
  ) {
    if (!checkWorkflowAuthorization(user, entry)) {
      return;
    }
    deleteWorkflow(user, entry);
    userDatabaseTable().findUser(entry.creatorId()).thenAccept(creator ->
      workflowDatabaseTable.generateAvailableWorkflowId().thenAccept(workflowId ->
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
    createWorkflow(workflowId, creator, entry.ownerId(), triggerData, actionData,
      conditionData, entry.created(), name, description, executions,
      timelineEntries, state);
    WorkflowAlterationSupervisor.create(timelineDatabaseTable, workflowId,
      entry, name, description, actionData, conditionData).evaluate(user);
  }

  private void createWorkflow(
    User creator, UUID ownerId, TaskwolfRequestBody triggerData,
    List<TaskwolfRequestBody> actionData,  List<TaskwolfRequestBody> conditionData,
    long created, String name, String description, List<Long> executions,
    List<TimelineDatabaseEntry> timelineEntries, WorkflowState state
  ) {
    if (!checkWorkflowAuthorization(creator, ownerId)) {
      return;
    }
    workflowDatabaseTable.generateAvailableWorkflowId().thenAccept(workflowId ->
      createWorkflow(workflowId, creator, ownerId, triggerData, actionData,
        conditionData, created, name, description, executions, timelineEntries, state));
  }

  private void createWorkflow(
    UUID workflowId, User creator, UUID ownerId, TaskwolfRequestBody triggerData,
    List<TaskwolfRequestBody> actionData,  List<TaskwolfRequestBody> conditionData,
    long created, String name, String description, List<Long> executions,
    List<TimelineDatabaseEntry> timelineEntries, WorkflowState state
  ) {
    triggerDatabaseTable.generateAvailableTriggerId().thenAccept(triggerId ->
      generateActionIds(actionData.size()).thenAccept(actionIds ->
        generateConditionIds(conditionData.size()).thenAccept(conditionIds ->
          createWorkflow(workflowId, creator.id(), ownerId, triggerId, triggerData,
            actionIds, actionData, conditionIds, conditionData, created, name,
            description, executions, timelineEntries, state))));
  }

  private CompletableFuture<List<UUID>> generateActionIds(int number) {
    return generateMultipleIds(number, actionDatabaseTable::generateAvailableActionId);
  }

  private CompletableFuture<List<UUID>> generateConditionIds(int number) {
    return generateMultipleIds(number, conditionDatabaseTable::generateAvailableConditionId);
  }

  private CompletableFuture<List<UUID>> generateMultipleIds(
    int number, Callable<CompletableFuture<UUID>> generator
  ) {
    var futureResponse = new CompletableFuture<List<UUID>>();
    var ids = Lists.<UUID>newArrayList();
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

  private void createWorkflow(
    UUID workflowId, UUID creatorId, UUID ownerId,
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
    workflowDatabaseTable.insertWorkflow(workflowId, creatorId,
      findAffiliation(creatorId, ownerId).toString(), ownerId, triggerId,
      actionIds, conditionIds, modules, created, name, description, state.toString());
    workflowExecutionDatabaseTable.insertWorkflowExecution(workflowId, executions);
    for (var entry : timelineEntries) {
      timelineDatabaseTable.insertEntry(entry.id(), workflowId, entry.time(),
        entry.type(), entry.content());
    }
  }

  private WorkflowAffiliation findAffiliation(UUID creatorId, UUID ownerId) {
    return ownerId.equals(creatorId) ? WorkflowAffiliation.PRIVATE :
      WorkflowAffiliation.ORGANIZATION;
  }

  private void createTrigger(
    UUID triggerId, UUID ownerId, UUID workflowId, TaskwolfRequestBody triggerData
  ) {
    triggerDatabaseTable.insertTrigger(triggerId, ownerId, workflowId,
      triggerData.getString("module"), triggerData.getString("type"),
      triggerData.getString("content"), TriggerState.ARMED.toString());
  }

  private void createAction(
    UUID actionId, UUID ownerId, UUID workflowId, TaskwolfRequestBody actionData
  ) {
    actionDatabaseTable.insertAction(actionId, ownerId, workflowId,
      actionData.getInt("index"), actionData.getString("module"),
      actionData.getString("type"), actionData.getString("content"));
  }

  private void createCondition(
    UUID conditionId, UUID ownerId, UUID workflowId, TaskwolfRequestBody conditionData
  ) {
    conditionDatabaseTable.insertCondition(conditionId, ownerId, workflowId,
      conditionData.getInt("index"), conditionData.getString("type"),
      conditionData.getString("content"));
  }

  @RequestMapping(path = "/workflow/state/change/", method = RequestMethod.POST)
  public void changeWorkflowState(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var isArmed = body.getBoolean("armed") ? TriggerState.ARMED :
      TriggerState.DISABLED;
    workflowDatabaseTable.findWorkflow(body.getUUID("workflow")).thenAccept(
      workflow -> triggerDatabaseTable.changeState(workflow.triggerId(), isArmed));
  }

  @RequestMapping(path = "/workflow/remove/", method = RequestMethod.POST)
  public void removeWorkflow(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var workflowId = body.getUUID("workflow");
    findUser(request).thenAccept(user -> workflowDatabaseTable.workflowExists(
      workflowId).thenAccept(exists -> deleteWorkflow(user, workflowId, exists)));
  }

  private void deleteWorkflow(User user, UUID workflowId, boolean workflowExists) {
    if (!workflowExists) {
      return;
    }
    workflowDatabaseTable.findWorkflow(workflowId).thenAccept(workflow ->
      deleteWorkflow(user, workflow));
  }

  private void deleteWorkflow(User user, WorkflowEntry workflow) {
    if (!checkWorkflowAuthorization(user, workflow)) {
      return;
    }
    deleteWorkflow(workflow);
  }

  public void deleteWorkflow(WorkflowEntry workflow) {
    workflowDatabaseTable.deleteWorkflow(workflow.id());
    triggerDatabaseTable.deleteTrigger(workflow.triggerId());
    for (var action : workflow.actionIds()) {
      actionDatabaseTable.deleteAction(action);
    }
    for (var condition : workflow.conditionIds()) {
      conditionDatabaseTable.deleteCondition(condition);
    }
    workflowExecutionDatabaseTable.deleteWorkflowExecutions(workflow.id());
    timelineDatabaseTable.findEntriesByWorkflow(workflow.id()).thenAccept(entries ->
      entries.forEach(timelineDatabaseEntry ->
        timelineDatabaseTable.deleteEntry(timelineDatabaseEntry.id())));
  }

  private boolean checkWorkflowAuthorization(User user, WorkflowEntry workflow) {
    return checkWorkflowAuthorization(user, workflow.ownerId());
  }

  private boolean checkWorkflowAuthorization(User user, UUID workflowOwnerId) {
    return workflowOwnerId.equals(user.id()) ||
      user.organizations().contains(workflowOwnerId);
  }
}
