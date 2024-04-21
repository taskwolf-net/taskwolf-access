package net.taskwolf.access.workflow;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.action.ActionDatabaseTable;
import net.taskwolf.core.action.ActionEntry;
import net.taskwolf.core.condition.ConditionDatabaseTable;
import net.taskwolf.core.condition.ConditionEntry;
import net.taskwolf.core.condition.ConditionInformationRepository;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.iterator.AsyncListIterator;
import net.taskwolf.core.trigger.TriggerDatabaseTable;
import net.taskwolf.core.trigger.TriggerEntry;
import net.taskwolf.core.trigger.TriggerState;
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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class WorkflowInformationController extends WorkflowController {
  private final CoreModule coreModule;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final ConditionDatabaseTable conditionDatabaseTable;
  private final ConditionInformationRepository conditionRepository;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy");

  private WorkflowInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable, CoreModule coreModule,
    TriggerDatabaseTable triggerDatabaseTable, ActionDatabaseTable actionDatabaseTable,
    ConditionDatabaseTable conditionDatabaseTable,
    ConditionInformationRepository conditionRepository,
    UserTargetDatabaseTable userTargetDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, workflowDatabaseTable);
    this.coreModule = coreModule;
    this.triggerDatabaseTable = triggerDatabaseTable;
    this.actionDatabaseTable = actionDatabaseTable;
    this.conditionDatabaseTable = conditionDatabaseTable;
    this.conditionRepository = conditionRepository;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
  }

  @RequestMapping(path = "/workflow/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findWorkflow(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> performWorkflowOperation(user,
      body.getUUID("workflow"), workflow -> gatherWorkflowInformation(user, workflow)
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Maps.newHashMap())));
    return futureResponse;
  }

  @RequestMapping(path = "/workflows/selected/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> selectedWorkflows(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenApply(user ->
      userTargetDatabaseTable.findTargetSecured(user.id()).thenAccept(target ->
        findSelectedWorkflows(user, target).thenApply(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findSelectedWorkflows(
    User user, UUID ownerId
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    collectWorkflows(Lists.newArrayList(ownerId)).thenAccept(workflows ->
      collectWorkflowInformation(user, workflows).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<List<WorkflowEntry>> collectWorkflows(
    List<UUID> ownerIds
  ) {
    var futureResponse = new CompletableFuture<List<WorkflowEntry>>();
    AsyncListIterator.execute(ownerIds, workflowDatabaseTable()::findWorkflowsOfOwner,
      ownerIds.size(), futureResponse::complete);
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> collectWorkflowInformation(
    User user, List<WorkflowEntry> workflows
  ) {
    if (workflows.isEmpty()) {
      return CompletableFuture.completedFuture(Map.of("workflows",
        Lists.newArrayList()));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(workflows, workflow ->
        gatherWorkflowInformation(user, workflow), workflows.size(),
      information -> futureResponse.complete(Map.of("workflows", information)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherWorkflowInformation(
    User user, WorkflowEntry workflow
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userDatabaseTable().findUserIfExists(workflow.creatorId()).thenAccept(creator ->
      triggerDatabaseTable.findTrigger(workflow.triggerId()).thenAccept(trigger ->
        actionDatabaseTable.findActionsByWorkflow(workflow.id()).thenAccept(actions ->
          conditionDatabaseTable.findConditionsByWorkflow(workflow.id()).thenAccept(conditions ->
            futureResponse.complete(assemblyWorkflowInformation(user, workflow,
              creator, trigger, actions, conditions))))));
    return futureResponse;
  }

  private Map<String, Object> assemblyWorkflowInformation(
    User user, WorkflowEntry workflow, User creator, TriggerEntry trigger,
    List<ActionEntry> actions, List<ConditionEntry> conditions
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", workflow.id());
    information.put("name", workflow.name());
    information.put("description", workflow.description());
    information.put("created", timeMillisecondsToDate(workflow.created()));
    information.put("creator", creator.name());
    information.put("armed", trigger.state() == TriggerState.ARMED);
    information.putAll(assemblyTriggerInformation(user, trigger));
    information.putAll(assemblyActionsInformation(user, actions));
    information.putAll(assemblyConditionsInformation(user, conditions));
    return information;
  }

  private Map<String, Object> assemblyTriggerInformation(
    User user, TriggerEntry trigger
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("triggerModule", trigger.module());
    information.put("triggerModuleLogo",
      coreModule.findModuleInformation(trigger.module()).get().logo());
    information.put("triggerType", trigger.type());
    information.put("triggerTypeDescription", coreModule.translate(user,
      coreModule.findTriggerInformation(trigger.module(),
        trigger.type()).get().description()));
    information.put("triggerContent", trigger.content());
    return information;
  }

  private Map<String, Object> assemblyActionsInformation(
    User user, List<ActionEntry> actions
  ) {
    var information = Maps.<String, Object>newHashMap();
    var actionsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var action : actions) {
      var actionInformation = Maps.<String, Object>newHashMap();
      actionInformation.put("actionIndex", action.actionIndex());
      actionInformation.put("actionModule", action.module());
      actionInformation.put("actionModuleLogo",
        coreModule.findModuleInformation(action.module()).get().logo());
      actionInformation.put("actionType", action.type());
      actionInformation.put("actionTypeDescription", coreModule.translate(user,
        coreModule.findActionInformation(action.module(),
          action.type()).get().description()));
      actionInformation.put("actionContent", action.content());
      actionsInformation.add(actionInformation);
    }
    information.put("actions", actionsInformation);
    return information;
  }

  private Map<String, Object> assemblyConditionsInformation(
    User user, List<ConditionEntry> conditions
  ) {
    var information = Maps.<String, Object>newHashMap();
    var conditionsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var condition : conditions) {
      var conditionInformation = Maps.<String, Object>newHashMap();
      conditionInformation.put("conditionActionIndex", condition.actionIndex());
      conditionInformation.put("conditionConditionIndex",
        condition.conditionIndex());
      conditionInformation.put("conditionType", condition.type());
      conditionInformation.put("conditionTypeName", coreModule.translate(user,
        conditionRepository.findByIdentifier(condition.type()).get().name()));
      conditionInformation.put("conditionContent", condition.content());
      conditionsInformation.add(conditionInformation);
    }
    information.put("conditions", conditionsInformation);
    return information;
  }

  private String timeMillisecondsToDate(long milliseconds) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(milliseconds);
    return simpleDateFormat.format(calendar.getTime());
  }
}
