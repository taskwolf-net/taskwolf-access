package net.taskflow.access.workflow;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.taskwolf.core.action.ActionDatabaseTable;
import net.taskwolf.core.action.ActionEntry;
import net.taskwolf.core.trigger.TriggerDatabaseTable;
import net.taskwolf.core.trigger.TriggerEntry;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.WorkflowEntry;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CrossOrigin
@RestController
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorkflowInformationController {
  private final Key secretKey;;
  private final UserDatabaseTable userDatabaseTable;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final WorkflowDatabaseTable workflowDatabaseTable;

  @RequestMapping(path = "/workflow/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findWorkflow(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var workflowId = UUID.fromString((String) input.get("workflow"));
    findUser(request).thenAccept(user -> workflowDatabaseTable
      .findWorkflow(workflowId).thenAccept(workflow ->
        findWorkflow(user, workflow).thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findWorkflow(
    User user, WorkflowEntry workflow
  ) {
    if (!checkWorkflowAuthorization(user, workflow)) {
      var futureResponse = new CompletableFuture<Map<String, Object>>();
      futureResponse.complete(Maps.newHashMap());
      return futureResponse;
    }
    return gatherWorkflowInformation(workflow);
  }

  @RequestMapping(path = "/workflows/selected/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> selectedWorkflows(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var ownerId = UUID.fromString((String) input.get("owner"));
    findUser(request).thenApply(user -> findSelectedWorkflows(user, ownerId));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findSelectedWorkflows(
    User user, UUID ownerId
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    if (!checkWorkflowAuthorization(user, ownerId)) {
      futureResponse.complete(Maps.newHashMap());
      return futureResponse;
    }
    collectWorkflows(Lists.newArrayList(ownerId)).thenAccept(workflows ->
      collectWorkflowInformation(workflows).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  @RequestMapping(path = "/workflows/all/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> allWorkflows(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenApply(user -> collectWorkflows(Stream.concat(
      user.organizations().stream(), Stream.of(user.id())).collect(Collectors.toList()))
      .thenAccept(workflows -> collectWorkflowInformation(workflows)
        .thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<List<WorkflowEntry>> collectWorkflows(
    List<UUID> ownerIds
  ) {
    var futureResponse = new CompletableFuture<List<WorkflowEntry>>();
    var workflows = Lists.<WorkflowEntry>newArrayList();
    var counter = new AtomicInteger();
    for (var ownerId : ownerIds) {
      workflowDatabaseTable.findWorkflowsOfOwner(ownerId).thenAccept(workflows::addAll)
        .thenAccept(value -> counter.incrementAndGet())
        .thenApply(value -> counter.get() == ownerIds.size() &&
          futureResponse.complete(workflows));
    }
    return futureResponse;
  }


  private CompletableFuture<Map<String, Object>> collectWorkflowInformation(
    List<WorkflowEntry> workflows
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var information = Lists.<Map<String, Object>>newArrayList();
    for (WorkflowEntry workflow : workflows) {
      gatherWorkflowInformation(workflow).thenAccept(information::add)
        .thenApply(value -> information.size() == workflows.size() &&
          futureResponse.complete(Map.of("workflows", information)));
    }
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherWorkflowInformation(
    WorkflowEntry workflow
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userDatabaseTable.findUser(workflow.creatorId()).thenAccept(creator ->
      triggerDatabaseTable.findTrigger(workflow.triggerId()).thenAccept(trigger ->
        actionDatabaseTable.findActionsByWorkflow(workflow.id())
          .thenAccept(actions -> futureResponse.complete(
            assemblyWorkflowInformation(workflow, creator, trigger, actions)))));
    return futureResponse;
  }

  private Map<String, Object> assemblyWorkflowInformation(
    WorkflowEntry workflow, User creator, TriggerEntry trigger,
    List<ActionEntry> actions
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("name", workflow.name());
    information.put("description", workflow.description());
    information.put("creator", creator.name());
    information.put("triggerModule", trigger.module());
    information.put("triggerType", trigger.type());
    information.put("triggerContent", trigger.content());
    var actionsInformation = Lists.<Map<String, Object>>newArrayList();
    for (ActionEntry action : actions) {
      var actionInformation = Maps.<String, Object>newHashMap();
      actionInformation.put("actionModule", action.module());
      actionInformation.put("actionType", action.type());
      actionInformation.put("actionContent", action.content());
      actionsInformation.add(actionInformation);
    }
    information.put("actions", actionsInformation);
    return information;
  }

  private boolean checkWorkflowAuthorization(User user, WorkflowEntry workflow) {
    return checkWorkflowAuthorization(user, workflow.ownerId());
  }

  private boolean checkWorkflowAuthorization(User user, UUID workflowOwnerId) {
    return workflowOwnerId.equals(user.id()) ||
      user.organizations().contains(workflowOwnerId);
  }

  private static final String API_KEY_IDENTIFIER = "API-KEY";

  private CompletableFuture<User> findUser(HttpServletRequest request) {
    var apiKey = request.getHeader(API_KEY_IDENTIFIER);
    var email = Jwts.parser().setSigningKey(secretKey).build()
      .parseClaimsJws(apiKey).getPayload().get("email", String.class);
    return userDatabaseTable.findUser(email);
  }
}
