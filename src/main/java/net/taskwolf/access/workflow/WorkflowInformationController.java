package net.taskwolf.access.workflow;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.action.ActionDatabaseTable;
import net.taskwolf.core.action.ActionEntry;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.iterator.AsyncListIterator;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.trigger.TriggerDatabaseTable;
import net.taskwolf.core.trigger.TriggerEntry;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.WorkflowEntry;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public final class WorkflowInformationController extends TaskwolfRestController {
  private final CoreModule coreModule;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;

  private WorkflowInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable, CoreModule coreModule,
    TriggerDatabaseTable triggerDatabaseTable, ActionDatabaseTable actionDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.coreModule = coreModule;
    this.triggerDatabaseTable = triggerDatabaseTable;
    this.actionDatabaseTable = actionDatabaseTable;
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
  }

  @RequestMapping(path = "/workflow/owners/all/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> allWorkflowOwners(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> collectOwnersInformation(
      user.organizations(), user.id()).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> collectOwnersInformation(
    List<UUID> organizations, UUID applicantId
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(organizations, this::gatherOrganizationInformation,
      organizations.size(), information -> futureResponse.complete(
        finishOwnersInformation(information, applicantId)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherOrganizationInformation(
    UUID organizationId
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    organizationDatabaseTable.findOrganization(organizationId).thenAccept(
      organization -> futureResponse.complete(Map.of("id", organization.id(),
        "name", organization.name())));
    return futureResponse;
  }

  private Map<String, Object> finishOwnersInformation(
    List<Map<String, Object>> organizations, UUID applicantId
  ) {
    var owners = Lists.<Map<String, Object>>newArrayList();
    owners.add(Map.of("id", applicantId, "name", "You / Personal"));
    owners.addAll(organizations);
    return Map.of("owners", owners);
  }

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
    findUser(request).thenApply(user -> findSelectedWorkflows(user, ownerId)
      .thenApply(futureResponse::complete));
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

  @RequestMapping(path = "/workflows/all/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> allWorkflows(
    HttpServletRequest request
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
    AsyncListIterator.execute(ownerIds, workflowDatabaseTable::findWorkflowsOfOwner,
      ownerIds.size(), futureResponse::complete);
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> collectWorkflowInformation(
    List<WorkflowEntry> workflows
  ) {
    if (workflows.isEmpty()) {
      return CompletableFuture.completedFuture(Map.of("workflows",
        Lists.newArrayList()));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(workflows, this::gatherWorkflowInformation, workflows.size(),
      information -> futureResponse.complete(Map.of("workflows", information)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherWorkflowInformation(
    WorkflowEntry workflow
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userDatabaseTable().findUser(workflow.creatorId()).thenAccept(creator ->
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
    information.put("id", workflow.id());
    information.put("name", workflow.name());
    information.put("description", workflow.description());
    information.put("creator", creator.name());
    information.put("triggerModule", trigger.module());
    information.put("triggerModuleLogo",
      coreModule.findModuleInformation(trigger.module()).get().logo());
    information.put("triggerType", trigger.type());
    information.put("triggerTypeDescription",
      coreModule.findTriggerInformation(trigger.module(), trigger.type()).get().description());
    information.put("triggerContent", trigger.content());
    var actionsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var action : actions) {
      var actionInformation = Maps.<String, Object>newHashMap();
      actionInformation.put("actionModule", action.module());
      actionInformation.put("actionModuleLogo",
        coreModule.findModuleInformation(action.module()).get().logo());
      actionInformation.put("actionType", action.type());
      actionInformation.put("actionTypeDescription",
        coreModule.findActionInformation(action.module(), action.type()).get().description());
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
}
