package net.taskwolf.access.workflow;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.WorkflowEntry;
import net.taskwolf.core.workflow.timeline.Timeline;
import net.taskwolf.core.workflow.timeline.TimelineFactory;
import net.taskwolf.core.workflow.timeline.entry.TimelineEntry;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class WorkflowTimelineController extends TaskwolfRestController {
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final TimelineFactory timelineFactory;

  private WorkflowTimelineController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable, TimelineFactory timelineFactory
  ) {
    super(secretKey, userDatabaseTable);
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.timelineFactory = timelineFactory;
  }

  @RequestMapping(path = "/workflow/timeline/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findTimeline(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var workflowId = UUID.fromString((String) input.get("workflow"));
    findUser(request).thenAccept(user -> workflowDatabaseTable
      .findWorkflow(workflowId).thenAccept(workflow ->
        findTimeline(user, workflow).thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findTimeline(
    User user, WorkflowEntry workflow
  ) {
    if (!checkWorkflowAuthorization(user, workflow)) {
      var futureResponse = new CompletableFuture<Map<String, Object>>();
      futureResponse.complete(Maps.newHashMap());
      return futureResponse;
    }
    return timelineFactory.createTimeline(workflow.id())
      .thenApply(this::assemblyTimelineInformation);
  }

  private Map<String, Object> assemblyTimelineInformation(Timeline timeline) {
    var information = Lists.newArrayList();
    var entries = timeline.findAllEntries().stream()
      .sorted(Comparator.comparing(TimelineEntry::rawTime)).toList();
    for (var entry : entries) {
      var entryInformation = Maps.<String, Object>newHashMap();
      entryInformation.put("title", entry.title());
      entryInformation.put("description", entry.description());
      entryInformation.put("level", entry.level());
      entryInformation.put("time", entry.formattedTime());
      information.add(entryInformation);
    }
    return Map.of("timeline", information);
  }

  private boolean checkWorkflowAuthorization(User user, WorkflowEntry workflow) {
    return checkWorkflowAuthorization(user, workflow.ownerId());
  }

  private boolean checkWorkflowAuthorization(User user, UUID workflowOwnerId) {
    return workflowOwnerId.equals(user.id()) ||
      user.organizations().contains(workflowOwnerId);
  }
}

