package net.taskwolf.access.workflow;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.action.ActionDatabaseTable;
import net.taskwolf.core.condition.ConditionDatabaseTable;
import net.taskwolf.core.trigger.TriggerDatabaseTable;
import net.taskwolf.core.trigger.TriggerState;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.workflow.*;
import net.taskwolf.core.workflow.timeline.TimelineDatabaseEntry;
import net.taskwolf.core.workflow.timeline.TimelineDatabaseTable;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;

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

  private WorkflowModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    TriggerDatabaseTable triggerDatabaseTable, ActionDatabaseTable actionDatabaseTable,
    ConditionDatabaseTable conditionDatabaseTable, WorkflowDatabaseTable workflowDatabaseTable,
    WorkflowExecutionDatabaseTable workflowExecutionDatabaseTable,
    TimelineDatabaseTable timelineDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.triggerDatabaseTable = triggerDatabaseTable;
    this.actionDatabaseTable = actionDatabaseTable;
    this.conditionDatabaseTable = conditionDatabaseTable;
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.workflowExecutionDatabaseTable = workflowExecutionDatabaseTable;
    this.timelineDatabaseTable = timelineDatabaseTable;
  }

  @RequestMapping(path = "/workflow/add/", method = RequestMethod.POST)
  public void addWorkflow(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var ownerId = UUID.fromString((String) input.get("owner"));
    var name = (String) input.get("name");
    var description = (String) input.get("description");
    var triggerData = (Map<String, Object>) input.get("trigger");
    var actionData = (List<Map<String, Object>>) input.get("actions");
    var conditionData = (List<Map<String, Object>>) input.get("conditions");
    var created = System.currentTimeMillis();
    findUser(request).thenAccept(user -> createWorkflowCreateTimelineEntry(user)
      .thenAccept(workflowCreateEntry -> createWorkflow(user, ownerId, triggerData,
        actionData, conditionData, created, name, description, Lists.newArrayList(),
        Lists.newArrayList(workflowCreateEntry), WorkflowState.OPERATIONAL)));
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
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var workflowId = UUID.fromString((String) input.get("workflow"));
    var name = (String) input.get("name");
    var description = (String) input.get("description");
    var triggerData = (Map<String, Object>) input.get("trigger");
    var actionData = (List<Map<String, Object>>) input.get("actions");
    var conditionData = (List<Map<String, Object>>) input.get("conditions");
    findUser(request).thenAccept(user -> workflowDatabaseTable.findWorkflow(workflowId)
      .thenAccept(workflow -> workflowExecutionDatabaseTable.findWorkflowExecutions(workflowId)
        .thenAccept(executions -> timelineDatabaseTable.findEntriesByWorkflow(workflowId)
          .thenAccept(timelineEntries -> updateWorkflow(user, workflow, triggerData,
            actionData, conditionData, name, description, executions, timelineEntries,
            workflow.state())))));
  }

  private void updateWorkflow(
    User user, WorkflowEntry entry, Map<String, Object> triggerData,
    List<Map<String, Object>> actionData, List<Map<String, Object>> conditionData,
    String name, String description, List<Long> executions,
    List<TimelineDatabaseEntry> timelineEntries, WorkflowState state
  ) {
    if (!checkWorkflowAuthorization(user, entry)) {
      return;
    }
    deleteWorkflow(user, entry.id());
    userDatabaseTable().findUser(entry.creatorId()).thenAccept(creator ->
      workflowDatabaseTable.generateAvailableWorkflowId().thenAccept(workflowId ->
        updateWorkflow(workflowId, user, creator, entry, triggerData, actionData,
          conditionData, name, description, executions, timelineEntries, state)));
  }

  private void updateWorkflow(
    UUID workflowId, User user, User creator, WorkflowEntry entry,
    Map<String, Object> triggerData, List<Map<String, Object>> actionData,
    List<Map<String, Object>> conditionData, String name, String description,
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
    User creator, UUID ownerId, Map<String, Object> triggerData,
    List<Map<String, Object>> actionData,  List<Map<String, Object>> conditionData,
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
    UUID workflowId, User creator, UUID ownerId, Map<String, Object> triggerData,
    List<Map<String, Object>> actionData,  List<Map<String, Object>> conditionData,
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
    UUID triggerId, Map<String, Object> triggerData, List<UUID> actionIds,
    List<Map<String, Object>> actionData, List<UUID> conditionIds,
    List<Map<String, Object>> conditionData, long created, String name,
    String description, List<Long> executions,
    List<TimelineDatabaseEntry> timelineEntries, WorkflowState state
  ) {
    var modules = Lists.<String>newArrayList();
    createTrigger(triggerId, ownerId, workflowId, triggerData);
    modules.add((String) triggerData.get("module"));
    for (int i = 0; i < actionData.size(); i++) {
      createAction(actionIds.get(i), ownerId, workflowId, actionData.get(i));
      modules.add((String) actionData.get(i).get("module"));
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
    UUID triggerId, UUID ownerId, UUID workflowId, Map<String, Object> triggerData
  ) {
    var module = (String) triggerData.get("module");
    var type = (String) triggerData.get("type");
    var content = (String) triggerData.get("content");
    triggerDatabaseTable.insertTrigger(triggerId, ownerId, workflowId,
      module, type, content, TriggerState.ARMED.toString());
  }

  private void createAction(
    UUID actionId, UUID ownerId, UUID workflowId, Map<String, Object> actionData
  ) {
    var index = (Integer) actionData.get("index");
    var module = (String) actionData.get("module");
    var type = (String) actionData.get("type");
    var content = (String) actionData.get("content");
    actionDatabaseTable.insertAction(actionId, ownerId, workflowId, index,
      module, type, content);
  }

  private void createCondition(
    UUID conditionId, UUID ownerId, UUID workflowId, Map<String, Object> conditionData
  ) {
    var index = (Integer) conditionData.get("index");
    var type = (String) conditionData.get("type");
    var content = (String) conditionData.get("content");
    conditionDatabaseTable.insertCondition(conditionId, ownerId, workflowId,
      index, type, content);
  }

  @RequestMapping(path = "/workflow/state/change/", method = RequestMethod.POST)
  public void changeWorkflowState(@RequestBody Map<String, Object> input) {
    var workflowId = UUID.fromString((String) input.get("workflow"));
    var isArmed = (boolean) input.get("armed") ? TriggerState.ARMED : TriggerState.DISABLED;
    workflowDatabaseTable.findWorkflow(workflowId).thenAccept(workflow ->
      triggerDatabaseTable.changeState(workflow.triggerId(), isArmed));
  }

  @RequestMapping(path = "/workflow/remove/", method = RequestMethod.POST)
  public void removeWorkflow(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var workflowId = UUID.fromString((String) input.get("workflow"));
    findUser(request).thenAccept(user -> deleteWorkflow(user, workflowId));
  }

  private void deleteWorkflow(User user, UUID workflowId) {
    if (!checkWorkflowAuthorization(user, workflowId)) {
      return;
    }
    workflowDatabaseTable.findWorkflow(workflowId).thenAccept(this::deleteWorkflow);
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
