package com.dulno.access.workflow;

import com.dulno.core.workflow.timeline.entry.TimelineWorkflowFailureEntry;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.CoreModule;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.action.ActionDatabaseTable;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.condition.ConditionDatabaseTable;
import com.dulno.core.organization.team.TeamDatabaseTable;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
import com.dulno.core.workflow.WorkflowDatabaseTable;
import com.dulno.core.workflow.WorkflowEntry;
import com.dulno.core.workflow.timeline.Timeline;
import com.dulno.core.workflow.timeline.TimelineDatabaseTable;
import com.dulno.core.workflow.timeline.TimelineFactory;
import com.dulno.core.workflow.timeline.entry.TimelineEntry;
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
    var body = DulnoRequestBody.of(payload, response);
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
      if (entry instanceof TimelineWorkflowFailureEntry failureEntry) {
        entryInformation.put("isFailure", true);
        entryInformation.put("failureModuleName",
          coreModule.translate(user, failureEntry.moduleName()));
        entryInformation.put("failureStepName",
          coreModule.translate(user, failureEntry.stepName()));
        entryInformation.put("failureStepIndex", failureEntry.stepIndex());
      } else {
        entryInformation.put("isFailure", false);
      }
      information.add(entryInformation);
    }
    return Map.of("timeline", information);
  }

  @RequestMapping(path = "/workflow/timeline/reset/", method = RequestMethod.POST)
  public void resetTimeline(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    findUser(request).thenAccept(user -> performWorkflowOperation(user,
      body.getUUID("workflow"), this::resetTimeline, () -> {}));
  }

  private void resetTimeline(WorkflowEntry workflow) {
    timelineDatabaseTable.findEntriesByWorkflow(workflow.id())
      .thenAccept(entries -> entries.forEach(entry ->
        timelineDatabaseTable.deleteEntry(entry.id())));
  }
}

