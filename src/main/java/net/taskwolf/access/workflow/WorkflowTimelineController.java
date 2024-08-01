package net.taskwolf.access.workflow;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.action.ActionDatabaseTable;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.condition.ConditionDatabaseTable;
import net.taskwolf.core.organization.team.TeamDatabaseTable;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.WorkflowEntry;
import net.taskwolf.core.workflow.timeline.Timeline;
import net.taskwolf.core.workflow.timeline.TimelineDatabaseTable;
import net.taskwolf.core.workflow.timeline.TimelineFactory;
import net.taskwolf.core.workflow.timeline.entry.TimelineEntry;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class WorkflowTimelineController extends WorkflowController {
  private final CoreModule coreModule;
  private final TimelineFactory timelineFactory;
  private final TimelineDatabaseTable timelineDatabaseTable;

  private WorkflowTimelineController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable,
    ActionDatabaseTable actionDatabaseTable,
    ConditionDatabaseTable conditionDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable, TeamDatabaseTable teamDatabaseTable,
    CoreModule coreModule, TimelineFactory timelineFactory,
    TimelineDatabaseTable timelineDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, workflowDatabaseTable,
      actionDatabaseTable, conditionDatabaseTable, userTargetDatabaseTable,
      teamTargetDatabaseTable, bundleDatabaseTable, teamDatabaseTable);
    this.coreModule = coreModule;
    this.timelineFactory = timelineFactory;
    this.timelineDatabaseTable = timelineDatabaseTable;
  }

  @RequestMapping(path = "/workflow/timeline/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findTimeline(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user ->
      performWorkflowOperation(user, body.getUUID("workflow"), workflow ->
          findTimeline(user, workflow).thenAccept(futureResponse::complete),
        () -> futureResponse.complete(Maps.newHashMap())));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findTimeline(
    User user, WorkflowEntry workflow
  ) {
    return timelineFactory.createTimeline(workflow.id()).thenApply(timeline ->
      assemblyTimelineInformation(user, timeline));
  }

  private Map<String, Object> assemblyTimelineInformation(
    User user, Timeline timeline
  ) {
    var information = Lists.newArrayList();
    var entries = timeline.findAllEntries().stream()
      .sorted(Comparator.comparing(TimelineEntry::rawTime).reversed()).toList();
    for (var entry : entries) {
      var entryInformation = Maps.<String, Object>newHashMap();
      entryInformation.put("title", entry.title(coreModule, user));
      entryInformation.put("description", entry.description(coreModule, user));
      entryInformation.put("level", entry.level());
      entryInformation.put("time", entry.formattedTime());
      information.add(entryInformation);
    }
    return Map.of("timeline", information);
  }

  @RequestMapping(path = "/workflow/timeline/reset/", method = RequestMethod.POST)
  public void resetTimeline(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    findUser(request).thenAccept(user -> performWorkflowOperation(user,
      body.getUUID("workflow"), this::resetTimeline, () -> {}));
  }

  private void resetTimeline(WorkflowEntry workflow) {
    timelineDatabaseTable.findEntriesByWorkflow(workflow.id())
      .thenAccept(entries -> entries.forEach(entry ->
        timelineDatabaseTable.deleteEntry(entry.id())));
  }
}

