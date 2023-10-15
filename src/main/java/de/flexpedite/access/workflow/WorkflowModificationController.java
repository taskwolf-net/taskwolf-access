package de.flexpedite.access.workflow;

import com.google.common.collect.Lists;
import de.flexpedite.core.action.ActionDatabaseTable;
import de.flexpedite.core.trigger.TriggerDatabaseTable;
import de.flexpedite.core.trigger.TriggerState;
import de.flexpedite.core.user.User;
import de.flexpedite.core.user.UserDatabaseTable;
import de.flexpedite.core.workflow.WorkflowAffiliation;
import de.flexpedite.core.workflow.WorkflowDatabaseTable;
import de.flexpedite.core.workflow.WorkflowEntry;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@CrossOrigin
@RestController
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorkflowModificationController {
  private final Key secretKey;;
  private final UserDatabaseTable userDatabaseTable;
  private final TriggerDatabaseTable triggerDatabaseTable;
  private final ActionDatabaseTable actionDatabaseTable;
  private final WorkflowDatabaseTable workflowDatabaseTable;

  @RequestMapping(path = "/workflow/add/", method = RequestMethod.POST)
  public void addWorkflow(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var ownerId = UUID.fromString((String) input.get("owner"));
    var name = (String) input.get("name");
    var description = (String) input.get("description");
    var triggerData = (Map<String, Object>) input.get("trigger");
    var actionData = (List<Map<String, Object>>) input.get("actions");
    findUser(request).thenAccept(user -> createWorkflow(user, ownerId,
      triggerData, actionData, name, description));
  }

  private void createWorkflow(
    User creator, UUID ownerId, Map<String, Object> triggerData,
    List<Map<String, Object>> actionData, String name, String description
  ) {
    if (!checkWorkflowAuthorization(creator, ownerId)) {
      return;
    }
    workflowDatabaseTable.generateAvailableWorkflowId().thenAccept(workflowId ->
      triggerDatabaseTable.generateAvailableTriggerId().thenAccept(triggerId ->
        generateActionIds(actionData.size()).thenAccept(actionIds ->
          createWorkflow(workflowId, creator.id(), ownerId, triggerId, triggerData,
            actionIds, actionData, name, description))));
  }

  private CompletableFuture<List<UUID>> generateActionIds(int number) {
    var futureResponse = new CompletableFuture<List<UUID>>();
    var actionIds = Lists.<UUID>newArrayList();
    for (int i = 0; i < number; i++) {
      actionDatabaseTable.generateAvailableActionId().thenAccept(actionIds::add)
        .thenApply(value -> actionIds.size() == number &&
          futureResponse.complete(actionIds));
    }
    return futureResponse;
  }

  private void createWorkflow(
    UUID workflowId, UUID creatorId, UUID ownerId,
    UUID triggerId, Map<String, Object> triggerData, List<UUID> actionIds,
    List<Map<String, Object>> actionData, String name, String description
  ) {
    createTrigger(triggerId, ownerId, workflowId, triggerData);
    for (int i = 0; i < actionData.size(); i++) {
      createAction(actionIds.get(i), ownerId, workflowId, actionData.get(i));
    }
    workflowDatabaseTable.insertWorkflow(workflowId, creatorId,
      findAffiliation(creatorId, ownerId).toString(), ownerId, triggerId,
      actionIds, name, description);
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
    var module = (String) actionData.get("module");
    var type = (String) actionData.get("type");
    var content = (String) actionData.get("content");
    actionDatabaseTable.insertAction(actionId, ownerId, workflowId,
      module, type, content);
  }

  @RequestMapping(path = "/workflow/remove/", method = RequestMethod.POST)
  public void removeWorkflow(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var workflowId = UUID.fromString((String) input.get("workflow"));
    findUser(request).thenAccept(user -> workflowDatabaseTable.findWorkflow(workflowId)
      .thenAccept(workflow -> deleteWorkflow(user, workflow)));
  }

  private void deleteWorkflow(User user, WorkflowEntry workflow) {
    if (!checkWorkflowAuthorization(user, workflow)) {
      return;
    }
    workflowDatabaseTable.deleteWorkflow(workflow.id());
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
