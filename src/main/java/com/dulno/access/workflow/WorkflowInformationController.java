package com.dulno.access.workflow;

import com.google.common.collect.Lists;
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
import com.dulno.core.condition.ConditionInformationRepository;
import com.dulno.core.database.paging.DatabaseDirection;
import com.dulno.core.database.paging.DatabaseOrder;
import com.dulno.core.database.paging.DatabasePage;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.locale.Translation;
import com.dulno.core.organization.team.TeamDatabaseTable;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.trigger.TriggerDatabaseTable;
import com.dulno.core.trigger.TriggerEntry;
import com.dulno.core.trigger.TriggerState;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
import com.dulno.core.workflow.WorkflowDatabaseTable;
import com.dulno.core.workflow.WorkflowEntry;
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
  private final Translation translation;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final ConditionDatabaseTable conditionDatabaseTable;
  private final ConditionInformationRepository conditionRepository;
  private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy");

  private WorkflowInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable, CoreModule coreModule,
    Translation translation, TriggerDatabaseTable triggerDatabaseTable,
    ActionDatabaseTable actionDatabaseTable,
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
    this.translation = translation;
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
    var body = DulnoRequestBody.of(payload, response);
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
    var body = DulnoRequestBody.of(payload, response);
    var targetPage = body.getInt("targetPage");
    var sortingColumn = body.getString("sorting");
    var sortingOrder = DatabaseOrder.valueOf(body.getString("order"));
    var search = body.getString("search");
    var module = body.has("application") ? body.getString("application") : null;
    var creatorId = body.has("creator") ? body.getUUID("creator") : null;
    var startTime = body.has("startTime") ? body.getLong("startTime") : -1;
    var endTime = body.has("endTime") ? body.getLong("endTime") : -1;
    var state = body.has("state") ? body.getString("state") : null;
    return findUser(request).thenCompose(user -> findWorkflowTarget(user.id())
      .thenCompose(target -> workflowDatabaseTable()
        .findWorkflowsOfOwner(target, targetPage, sortingColumn, sortingOrder,
          search, module, creatorId, startTime, endTime, state)
        .thenCompose(result -> collectWorkflowInformation(user, result))));
  }

  @RequestMapping(path = "/workflows/page/shift/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findPreviousWorkflowPage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var pageState = body.getString("pageState");
    var startingPoint = DatabaseDirection.valueOf(body.getString("startingPoint"));
    var direction = DatabaseDirection.valueOf(body.getString("direction"));
    var sortingColumn = body.getString("sorting");
    var sortingOrder = DatabaseOrder.valueOf(body.getString("order"));
    var module = body.has("application") ? body.getString("application") : null;
    var creatorId = body.has("creator") ? body.getUUID("creator") : null;
    var startTime = body.has("startTime") ? body.getLong("startTime") : -1;
    var endTime = body.has("endTime") ? body.getLong("endTime") : -1;
    var state = body.has("state") ? body.getString("state") : null;
    return findUser(request).thenCompose(user -> findWorkflowTarget(user.id())
      .thenCompose(target -> workflowDatabaseTable()
        .findWorkflowsOfOwner(target, pageState, startingPoint, direction,
          sortingColumn, sortingOrder, module, creatorId, startTime, endTime, state))
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
      .thenApply(information -> reconstructWorkflowOrder(page, information))
      .thenAccept(information -> futureResponse.complete(Map.of("workflows",
        information, "page", page.pageState(), "pageNumber", page.pageNumber())));
    return futureResponse;
  }

  private List<Map<String, Object>> reconstructWorkflowOrder(
    DatabasePage<WorkflowEntry> page, List<Map<String, Object>> information
  ) {
    var result = Lists.<Map<String, Object>>newArrayList();
    for (var workflow : page.content()) {
      for (var entry : information) {
        if (workflow.id().toString().equals(entry.get("id").toString())) {
          result.add(entry);
          break;
        }
      }
    }
    return result;
  }

  private CompletableFuture<Map<String, Object>> gatherWorkflowInformation(
    User user, WorkflowEntry workflow
  ) {
    return userDatabaseTable().findUserIfExists(workflow.creatorId())
      .thenCompose(creator -> gatherTriggerInformation(user, workflow)
        .thenCompose(trigger -> gatherActionsInformation(user, workflow)
          .thenCompose(actions -> gatherConditionsInformation(user, workflow)
            .thenApply(conditions -> assemblyWorkflowInformation(workflow,
              creator, trigger, actions, conditions)))));
  }

  private CompletableFuture<Map<String, Object>> gatherTriggerInformation(
    User user, WorkflowEntry workflow
  ) {
    return triggerDatabaseTable.triggerExists(workflow.triggerId())
      .thenCompose(exists -> gatherTriggerInformation(user, workflow, exists));
  }

  private CompletableFuture<Map<String, Object>> gatherTriggerInformation(
    User user, WorkflowEntry workflow, boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return triggerDatabaseTable.findTrigger(workflow.triggerId())
      .thenCompose(entry -> coreModule.findTrigger(entry.module(), entry.type())
        .get().findContent(entry.id())
        .thenApply(content -> assemblyTriggerInformation(user, entry, content))
        .exceptionally(value -> Maps.newHashMap()));
  }

  private Map<String, Object> assemblyTriggerInformation(
    User user, TriggerEntry trigger, Map<String, Object> content
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("triggerModule", trigger.module());
    information.put("triggerModuleLogo",
      coreModule.findModuleInformation(trigger.module()).get().logo());
    information.put("triggerType", trigger.type());
    information.put("triggerTypeDescription", translation.translate(user,
      coreModule.findTriggerInformation(trigger.module(),
        trigger.type()).get().description()));
    information.put("triggerContent", new JSONObject(content).toString());
    information.put("armed", trigger.state() == TriggerState.ARMED);
    return information;
  }

  private CompletableFuture<Map<String, Object>> gatherActionsInformation(
    User user, WorkflowEntry workflow
  ) {
    return actionDatabaseTable.findActionsByWorkflow(workflow.id())
      .thenCompose(actions -> AsyncIterator.execute(actions,
          action -> gatherActionInformation(user, action))
        .thenApply(information -> Map.of("actions", information)));
  }

  private CompletableFuture<Map<String, Object>> gatherActionInformation(
    User user, ActionEntry entry
  ) {
    return coreModule.findAction(entry.module(), entry.type())
      .get().findContent(entry.id())
      .thenApply(content -> assemblyActionInformation(user, entry, content))
      .exceptionally(value -> Maps.newHashMap());
  }

  private Map<String, Object> assemblyActionInformation(
    User user, ActionEntry action, Map<String, Object> content
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("actionIndex", action.actionIndex());
    information.put("actionModule", action.module());
    information.put("actionModuleLogo",
      coreModule.findModuleInformation(action.module()).get().logo());
    information.put("actionType", action.type());
    information.put("actionTypeDescription", translation.translate(user,
      coreModule.findActionInformation(action.module(),
        action.type()).get().description()));
    information.put("actionContent", new JSONObject(content).toString());
    return information;
  }

  private CompletableFuture<Map<String, Object>> gatherConditionsInformation(
    User user, WorkflowEntry workflow
  ) {
    return conditionDatabaseTable.findConditionsByWorkflow(workflow.id())
      .thenApply(conditions -> Map.of("conditions", conditions.stream()
        .map(condition -> assemblyConditionInformation(user, condition)).toList()));
  }

  private Map<String, Object> assemblyConditionInformation(
    User user, ConditionEntry condition
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("conditionActionIndex", condition.actionIndex());
    information.put("conditionConditionIndex",
      condition.conditionIndex());
    information.put("conditionType", condition.type());
    information.put("conditionTypeName", translation.translate(user,
      conditionRepository.findByIdentifier(condition.type()).get().name()));
    information.put("conditionContent", condition.content());
    return information;
  }

  private Map<String, Object> assemblyWorkflowInformation(
    WorkflowEntry workflow, User creator, Map<String, Object> triggerInformation,
    Map<String, Object> actionInformation, Map<String, Object> conditionInformation
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", workflow.id());
    information.put("name", workflow.name());
    information.put("description", workflow.description());
    information.put("created", timeMillisecondsToDate(workflow.created()));
    information.put("creator", creator.name());
    information.putAll(triggerInformation);
    information.putAll(actionInformation);
    information.putAll(conditionInformation);
    return information;
  }

  private String timeMillisecondsToDate(long milliseconds) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(milliseconds);
    return simpleDateFormat.format(calendar.getTime());
  }
}
