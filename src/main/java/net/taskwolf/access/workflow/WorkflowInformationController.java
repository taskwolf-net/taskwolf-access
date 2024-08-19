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
import net.taskwolf.core.condition.ConditionInformationRepository;
import net.taskwolf.core.database.DatabaseDirection;
import net.taskwolf.core.database.DatabasePage;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.organization.team.TeamDatabaseTable;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.core.trigger.TriggerDatabaseTable;
import net.taskwolf.core.trigger.TriggerEntry;
import net.taskwolf.core.trigger.TriggerState;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.WorkflowEntry;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
public final class WorkflowInformationController extends WorkflowController {
  private final CoreModule coreModule;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final ConditionDatabaseTable conditionDatabaseTable;
  private final ConditionInformationRepository conditionRepository;
  private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy");

  private WorkflowInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable, CoreModule coreModule,
    TriggerDatabaseTable triggerDatabaseTable, ActionDatabaseTable actionDatabaseTable,
    ConditionDatabaseTable conditionDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable, TeamDatabaseTable teamDatabaseTable,
    ConditionInformationRepository conditionRepository
  ) {
    super(secretKey, userDatabaseTable, workflowDatabaseTable,
      actionDatabaseTable, conditionDatabaseTable, userTargetDatabaseTable,
      teamTargetDatabaseTable, bundleDatabaseTable, teamDatabaseTable);
    this.coreModule = coreModule;
    this.triggerDatabaseTable = triggerDatabaseTable;
    this.actionDatabaseTable = actionDatabaseTable;
    this.conditionDatabaseTable = conditionDatabaseTable;
    this.conditionRepository = conditionRepository;
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

  @RequestMapping(path = "/workflows/page/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findWorkflowPage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var targetPage = body.getInt("targetPage");
    var creatorId = body.getUUID("creator");
    var startTime = body.getLong("startTime");
    var endTime = body.getLong("endTime");
    return findUser(request).thenCompose(user -> findWorkflowTarget(user.id())
      .thenCompose(target -> workflowDatabaseTable()
        .findWorkflowsOfOwner(target, targetPage, creatorId, startTime, endTime)
        .thenCompose(result -> collectWorkflowInformation(user, result))));
  }

  @RequestMapping(path = "/workflows/page/shift/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findPreviousWorkflowPage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var pageState = body.getString("pageState");
    var startingPoint = DatabaseDirection.valueOf(body.getString("startingPoint"));
    var direction = DatabaseDirection.valueOf(body.getString("direction"));
    var creatorId = body.getUUID("creator");
    var startTime = body.getLong("startTime");
    var endTime = body.getLong("endTime");
    return findUser(request).thenCompose(user -> findWorkflowTarget(user.id())
      .thenCompose(target -> workflowDatabaseTable()
        .findWorkflowsOfOwner(target, pageState, startingPoint, direction,
          creatorId, startTime, endTime))
      .thenCompose(result -> collectWorkflowInformation(user, result)));
  }

  private CompletableFuture<Map<String, Object>> collectWorkflowInformation(
    User user, DatabasePage<WorkflowEntry> page
  ) {
    if (page.content().isEmpty()) {
      return CompletableFuture.completedFuture(Map.of("workflows",
        Lists.newArrayList(), "page", page.pageState(), "pageNumber", 0));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(page.content(),
        workflow -> gatherWorkflowInformation(user, workflow))
      .thenApply(information -> information.stream()
        .sorted(Comparator.comparing(content -> content.get("id").toString())))
      .thenAccept(information -> futureResponse.complete(Map.of("workflows",
        information, "page", page.pageState(), "pageNumber", page.pageNumber())));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherWorkflowInformation(
    User user, WorkflowEntry workflow
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userDatabaseTable().findUserIfExists(workflow.creatorId())
      .thenAccept(creator -> triggerDatabaseTable.findTrigger(workflow.triggerId())
        .thenAccept(trigger -> coreModule.findTrigger(trigger.module(), trigger.type())
          .get().findContent(trigger.id())
          .thenAccept(triggerContent -> actionDatabaseTable.findActionsByWorkflow(workflow.id())
            .thenAccept(actions -> findActionsContent(actions)
              .thenAccept(actionsContent -> conditionDatabaseTable.findConditionsByWorkflow(workflow.id())
                .thenAccept(conditions -> futureResponse.complete(
                  assemblyWorkflowInformation(user, workflow, creator, trigger,
                    triggerContent, actionsContent, conditions))))))));
    return futureResponse;
  }

  private CompletableFuture<Map<ActionEntry, Map<String, Object>>> findActionsContent(
    List<ActionEntry> actions
  ) {
    var futureResponse = new CompletableFuture<Map<ActionEntry, Map<String, Object>>>();
    AsyncIterator.execute(actions, action -> coreModule.findAction(action.module(),
      action.type()).get().findContent(action.id()).thenApply(content ->
      new AbstractMap.SimpleEntry(action, content))).thenAccept(result ->
      futureResponse.complete(result.stream().collect(Collectors.toMap(entry ->
        (ActionEntry) entry.getKey(), entry -> (Map<String, Object>) entry.getValue()))));
    return futureResponse;
  }

  private Map<String, Object> assemblyWorkflowInformation(
    User user, WorkflowEntry workflow, User creator, TriggerEntry trigger,
    Map<String, Object> triggerContent, Map<ActionEntry, Map<String, Object>> actions,
    List<ConditionEntry> conditions
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", workflow.id());
    information.put("name", workflow.name());
    information.put("description", workflow.description());
    information.put("created", timeMillisecondsToDate(workflow.created()));
    information.put("creator", creator.name());
    information.put("armed", trigger.state() == TriggerState.ARMED);
    information.putAll(assemblyTriggerInformation(user, trigger, triggerContent));
    information.putAll(assemblyActionsInformation(user, actions));
    information.putAll(assemblyConditionsInformation(user, conditions));
    return information;
  }

  private Map<String, Object> assemblyTriggerInformation(
    User user, TriggerEntry trigger, Map<String, Object> content
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("triggerModule", trigger.module());
    information.put("triggerModuleLogo",
      coreModule.findModuleInformation(trigger.module()).get().logo());
    information.put("triggerType", trigger.type());
    information.put("triggerTypeDescription", coreModule.translate(user,
      coreModule.findTriggerInformation(trigger.module(),
        trigger.type()).get().description()));
    information.put("triggerContent", new JSONObject(content).toString());
    return information;
  }

  private Map<String, Object> assemblyActionsInformation(
    User user, Map<ActionEntry, Map<String, Object>> actions
  ) {
    var information = Maps.<String, Object>newHashMap();
    var actionsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var entry : actions.entrySet()) {
      var action = entry.getKey();
      var content = entry.getValue();
      var actionInformation = Maps.<String, Object>newHashMap();
      actionInformation.put("actionIndex", action.actionIndex());
      actionInformation.put("actionModule", action.module());
      actionInformation.put("actionModuleLogo",
        coreModule.findModuleInformation(action.module()).get().logo());
      actionInformation.put("actionType", action.type());
      actionInformation.put("actionTypeDescription", coreModule.translate(user,
        coreModule.findActionInformation(action.module(),
          action.type()).get().description()));
      actionInformation.put("actionContent", new JSONObject(content).toString());
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
