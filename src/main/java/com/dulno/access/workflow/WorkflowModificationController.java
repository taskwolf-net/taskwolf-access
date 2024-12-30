package com.dulno.access.workflow;

import com.dulno.workflow.WorkflowModule;
import com.dulno.workflow.loop.LoopDatabaseTable;
import com.dulno.workflow.timeline.TimelineDatabaseEntry;
import com.dulno.workflow.timeline.TimelineDatabaseTable;
import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.workflow.action.ActionDatabaseTable;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.workflow.condition.ConditionDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.organization.team.TeamDatabaseTable;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.workflow.trigger.TriggerDatabaseTable;
import com.dulno.workflow.trigger.TriggerState;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
import com.dulno.workflow.structure.*;
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
  private final WorkflowModule workflowModule;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final ConditionDatabaseTable conditionDatabaseTable;
  private final LoopDatabaseTable loopDatabaseTable;
  private final TimelineDatabaseTable timelineDatabaseTable;

  private WorkflowModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable, WorkflowModule workflowModule,
    WorkflowDatabaseTable workflowDatabaseTable,
    TriggerDatabaseTable triggerDatabaseTable, ActionDatabaseTable actionDatabaseTable,
    ConditionDatabaseTable conditionDatabaseTable, LoopDatabaseTable loopDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable, TeamDatabaseTable teamDatabaseTable,
    TimelineDatabaseTable timelineDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, workflowDatabaseTable,
      actionDatabaseTable, conditionDatabaseTable, userTargetDatabaseTable,
      teamTargetDatabaseTable, bundleDatabaseTable, teamDatabaseTable);
    this.workflowModule = workflowModule;
    this.triggerDatabaseTable = triggerDatabaseTable;
    this.actionDatabaseTable = actionDatabaseTable;
    this.conditionDatabaseTable = conditionDatabaseTable;
    this.loopDatabaseTable = loopDatabaseTable;
    this.timelineDatabaseTable = timelineDatabaseTable;
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
    var body = DulnoRequestBody.of(payload, response);
    return findUser(request).thenCompose(user ->
      userTargetDatabaseTable().findTargetSecured(user.id()).thenCompose(target ->
        checkWorkflowNumberLimit(user, target).thenCompose(limitReached ->
          addWorkflow(user, target, body, limitReached, response))));
  }

  private CompletableFuture<Void> addWorkflow(
    User user, UUID target, DulnoRequestBody body, boolean limitReached,
    HttpServletResponse response
  ) {
    if (limitReached) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return CompletableFuture.completedFuture(null);
    }
    var created = System.currentTimeMillis();
    return findWorkflowOwner(user, target).thenCompose(owner ->
      createWorkflowCreateTimelineEntry(user).thenCompose(workflowCreateEntry ->
        createWorkflow(user, owner, body.getObject("trigger"),
          body.getObjectList("actions"), body.getObjectList("conditions"),
          body.getObject("loop"), body.getString("timeZone"),
          body.getString("timeLocale"), created, body.getString("name", 64),
          body.getString("description", 128),
          Lists.newArrayList(workflowCreateEntry), WorkflowState.OPERATIONAL)));
  }

  private CompletableFuture<UUID> findWorkflowOwner(User user, UUID target) {
    return user.id().equals(target) ?
      CompletableFuture.completedFuture(target) :
      teamTargetDatabaseTable().findTargetSecured(user.id())
        .thenApply(team -> team.orElse(target));
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
  public CompletableFuture<Void> updateWorkflow(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    if (payload.getBytes().length > MAX_WORKFLOW_BYTES) {
      response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
      return CompletableFuture.completedFuture(null);
    }
    var body = DulnoRequestBody.of(payload, response);
    var workflowId = body.getUUID("workflow");
    return findUser(request).thenCompose(user ->
      workflowDatabaseTable().findWorkflow(workflowId).thenCompose(workflow ->
        timelineDatabaseTable.findEntriesByWorkflow(workflowId)
          .thenCompose(timelineEntries -> checkWorkflowAuthorization(user, workflow)
            .thenCompose(authorized -> updateWorkflow(user, workflow, authorized,
              body.getObject("trigger"), body.getObjectList("actions"),
              body.getObjectList("conditions"), body.getObject("loop"),
              body.getString("timeZone"), body.getString("timeLocale"),
              body.getString("name", 64), body.getString("description", 128),
              timelineEntries, workflow.state())))));
  }

  private CompletableFuture<Void> updateWorkflow(
    User user, WorkflowEntry entry, boolean authorized,
    DulnoRequestBody triggerData, List<DulnoRequestBody> actionData,
    List<DulnoRequestBody> conditionData, DulnoRequestBody loopData,
    String timeZone, String timeLocale, String name, String description,
    List<TimelineDatabaseEntry> timelineEntries, WorkflowState state
  ) {
    if (!authorized) {
      return CompletableFuture.completedFuture(null);
    }
    return deleteWorkflow(entry).thenCompose(value ->
      userDatabaseTable().findUser(entry.creatorId()).thenCompose(creator ->
        updateWorkflow(entry.id(), user, creator, entry, triggerData, actionData,
          conditionData, loopData, timeZone, timeLocale, name, description,
          timelineEntries, state)));
  }

  private CompletableFuture<Void> updateWorkflow(
    UUID workflowId, User user, User creator, WorkflowEntry entry,
    DulnoRequestBody triggerData, List<DulnoRequestBody> actionData,
    List<DulnoRequestBody> conditionData, DulnoRequestBody loopData,
    String timeZone, String timeLocale, String name, String description,
    List<TimelineDatabaseEntry> timelineEntries, WorkflowState state
  ) {
    WorkflowAlterationSupervisor.create(timelineDatabaseTable, workflowId,
      entry, name, description, actionData, conditionData, loopData).evaluate(user);
    return createWorkflow(workflowId, creator, entry.ownerId(), triggerData,
      actionData, conditionData, loopData, timeZone, timeLocale,
      entry.created(), name, description, timelineEntries, state);
  }

  private CompletableFuture<Void> createWorkflow(
    User creator, UUID ownerId, DulnoRequestBody triggerData,
    List<DulnoRequestBody> actionData,  List<DulnoRequestBody> conditionData,
    DulnoRequestBody loopData,   String timeZone, String timeLocale, long created,
    String name, String description, List<TimelineDatabaseEntry> timelineEntries,
    WorkflowState state
  ) {
    return workflowDatabaseTable().generateAvailableWorkflowId()
      .thenCompose(workflowId -> createWorkflow(workflowId, creator, ownerId,
        triggerData, actionData, conditionData, loopData, timeZone, timeLocale,
        created, name, description, timelineEntries, state));
  }

  private CompletableFuture<Void> createWorkflow(
    UUID workflowId, User creator, UUID ownerId,
    DulnoRequestBody triggerData, List<DulnoRequestBody> actionData,
    List<DulnoRequestBody> conditionData, DulnoRequestBody loopData,
    String timeZone, String timeLocale, long created, String name,
    String description, List<TimelineDatabaseEntry> timelineEntries,
    WorkflowState state
  ) {
    return triggerDatabaseTable.generateAvailableTriggerId().thenCompose(triggerId ->
      generateActionIds(actionData.size()).thenCompose(actionIds ->
        generateConditionIds(conditionData.size()).thenCompose(conditionIds ->
          generateLoopId(loopData).thenCompose(loopId ->
            createWorkflow(workflowId, creator.id(), ownerId, triggerId,
              triggerData, actionIds, actionData, conditionIds, conditionData,
              loopId, loopData, timeZone, timeLocale, created, name, description,
              timelineEntries, state)))));
  }

  private CompletableFuture<UUID> generateLoopId(DulnoRequestBody loopData) {
    if (!loopData.getBoolean("enabled")) {
      return CompletableFuture.completedFuture(null);
    }
    return loopDatabaseTable.generateAvailableLoopId();
  }

  private CompletableFuture<Void> createWorkflow(
    UUID workflowId, UUID creatorId, UUID ownerId, UUID triggerId,
    DulnoRequestBody triggerData, List<UUID> actionIds,
    List<DulnoRequestBody> actionData, List<UUID> conditionIds,
    List<DulnoRequestBody> conditionData, UUID loopId, DulnoRequestBody loopData,
    String timeZone, String timeLocale, long created, String name,
    String description, List<TimelineDatabaseEntry> timelineEntries,
    WorkflowState state
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    var modules = Lists.<String>newArrayList();
    processes.add(createTrigger(triggerId, ownerId, workflowId, triggerData));
    modules.add(triggerData.getString("module"));
    for (int i = 0; i < actionData.size(); i++) {
      processes.add(createAction(actionIds.get(i), ownerId, workflowId,
        actionData.get(i)));
      modules.add(actionData.get(i).getString("module"));
    }
    for (int i = 0; i < conditionData.size(); i++) {
      processes.add(createCondition(conditionIds.get(i), ownerId, workflowId,
        conditionData.get(i)));
    }
    if (loopData.getBoolean("enabled")) {
      processes.add(createLoop(loopId, ownerId, workflowId, loopData));
    }
    for (var entry : timelineEntries) {
      processes.add(timelineDatabaseTable.insertEntry(entry.id(), workflowId,
        entry.time(), entry.type(), entry.content()));
    }
    processes.add(workflowDatabaseTable().insertWorkflow(workflowId, ownerId,
      creatorId, triggerId, actionIds, conditionIds, loopId, modules, timeZone,
      timeLocale, created, name, description, state.toString()));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> createTrigger(
    UUID triggerId, UUID ownerId, UUID workflowId, DulnoRequestBody triggerData
  ) {
    var module = triggerData.getString("module");
    var type = triggerData.getString("type");
    return triggerDatabaseTable.insertTrigger(triggerId, ownerId, workflowId,
      module, type, TriggerState.ARMED.toString())
      .thenCompose(value -> workflowModule.findTrigger(module, type).get().insert(
        triggerId, ownerId, new JSONObject(triggerData.getString("content")).toMap()));
  }

  private CompletableFuture<Void> createAction(
    UUID actionId, UUID ownerId, UUID workflowId, DulnoRequestBody actionData
  ) {
    var module = actionData.getString("module");
    var type = actionData.getString("type");
    return actionDatabaseTable.insertAction(actionId, ownerId, workflowId,
        module, type, actionData.getInt("index"))
      .thenCompose(value -> workflowModule.findAction(module, type).get().insert(
        actionId, ownerId, new JSONObject(actionData.getString("content")).toMap()));
  }

  private CompletableFuture<Void> createCondition(
    UUID conditionId, UUID ownerId, UUID workflowId, DulnoRequestBody conditionData
  ) {
    return conditionDatabaseTable.insertCondition(conditionId, ownerId, workflowId,
      conditionData.getString("type"), conditionData.getString("content"),
      conditionData.getInt("index"));
  }

  private CompletableFuture<Void> createLoop(
    UUID loopId, UUID ownerId, UUID workflowId, DulnoRequestBody loopData
  ) {
    return loopDatabaseTable.insertLoop(loopId, ownerId, workflowId,
      loopData.getString("type"), loopData.getString("content"),
      loopData.getInt("index"));
  }

  @RequestMapping(path = "/workflow/state/change/", method = RequestMethod.POST)
  public void changeWorkflowState(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var isArmed = body.getBoolean("armed") ? TriggerState.ARMED :
      TriggerState.DISABLED;
    performWorkflowOperation(findUserId(request), body.getUUID("workflow"),
      workflow -> triggerDatabaseTable.changeState(workflow.triggerId(), isArmed),
      () -> {});
  }

  @RequestMapping(path = "/workflow/remove/", method = RequestMethod.POST)
  public CompletableFuture<Void> removeWorkflow(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Void>();
    performWorkflowOperation(findUserId(request), body.getUUID("workflow"),
      workflow -> deleteWorkflow(workflow).thenAccept(futureResponse::complete),
      () -> futureResponse.complete(null));
    return futureResponse;
  }

  public CompletableFuture<Void> deleteWorkflow(WorkflowEntry workflow) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    processes.add(deleteWorkflowTrigger(workflow.triggerId()));
    for (var action : workflow.actionIds()) {
      processes.add(deleteWorkflowAction(action));
    }
    for (var condition : workflow.conditionIds()) {
      processes.add(conditionDatabaseTable.deleteCondition(condition));
    }
    if (workflow.loopId() != null) {
      processes.add(loopDatabaseTable.deleteLoop(workflow.loopId()));
    }
    processes.add(timelineDatabaseTable.findEntriesByWorkflow(workflow.id())
      .thenCompose(entries -> AsyncIterator.execute(entries, entry ->
        timelineDatabaseTable.deleteEntry(entry.id())).thenApply(value -> null)));
    processes.add(workflowDatabaseTable().deleteWorkflow(workflow.id()));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(value -> null);
  }

  private CompletableFuture<Void> deleteWorkflowTrigger(UUID triggerId) {
    return triggerDatabaseTable.triggerExists(triggerId)
      .thenCompose(exists -> deleteWorkflowTrigger(triggerId, exists));
  }

  private CompletableFuture<Void> deleteWorkflowTrigger(
    UUID triggerId, boolean triggerExists
  ) {
    if (!triggerExists) {
      return CompletableFuture.completedFuture(null);
    }
    return triggerDatabaseTable.findTrigger(triggerId)
      .thenCompose(entry -> triggerDatabaseTable.deleteTrigger(triggerId)
        .thenCompose(value -> workflowModule.findTrigger(entry.module(), entry.type())
          .get().delete(entry.id())));
  }

  private CompletableFuture<Void> deleteWorkflowAction(UUID actionId) {
    return actionDatabaseTable.actionExists(actionId)
      .thenCompose(exists -> deleteWorkflowAction(actionId, exists));
  }

  private CompletableFuture<Void> deleteWorkflowAction(
    UUID actionId, boolean actionExists
  ) {
    if (!actionExists) {
      return CompletableFuture.completedFuture(null);
    }
    return actionDatabaseTable.findAction(actionId)
      .thenCompose(entry -> actionDatabaseTable.deleteAction(actionId)
        .thenCompose(value -> workflowModule.findAction(entry.module(), entry.type())
          .get().delete(entry.id())));
  }
}
