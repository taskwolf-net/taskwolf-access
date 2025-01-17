package com.dulno.access.workflow;

import com.dulno.core.database.paging.DatabasePage;
import com.dulno.core.locale.Translation;
import com.dulno.workflow.timeline.Timeline;
import com.dulno.workflow.timeline.TimelineDatabaseEntry;
import com.dulno.workflow.timeline.TimelineDatabaseTable;
import com.dulno.workflow.timeline.TimelineFactory;
import com.dulno.workflow.timeline.entry.TimelineEntry;
import com.dulno.workflow.timeline.entry.TimelineWorkflowFailureEntry;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.workflow.action.ActionDatabaseTable;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.workflow.condition.ConditionDatabaseTable;
import com.dulno.core.organization.team.TeamDatabaseTable;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
import com.dulno.workflow.structure.WorkflowDatabaseTable;
import com.dulno.workflow.structure.WorkflowEntry;
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
  private final Translation translation;
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
    Translation translation, TimelineFactory timelineFactory,
    TimelineDatabaseTable timelineDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, workflowDatabaseTable,
      actionDatabaseTable, conditionDatabaseTable, userTargetDatabaseTable,
      teamTargetDatabaseTable, bundleDatabaseTable, teamDatabaseTable);
    this.translation = translation;
    this.timelineFactory = timelineFactory;
    this.timelineDatabaseTable = timelineDatabaseTable;
  }

  @RequestMapping(path = "/workflow/timeline/page/first/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> firstTimelinePage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var workflowId = body.getUUID("workflow");
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user ->
      performWorkflowOperation(user, workflowId, workflow ->
          timelineDatabaseTable.firstTimelinePage(workflow.id())
            .thenCompose(page -> collectTimelineInformation(user, page))
            .thenAccept(futureResponse::complete),
        () -> futureResponse.complete(Maps.newHashMap())));
    return futureResponse;
  }

  @RequestMapping(path = "/workflow/timeline/page/next/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> nextTimelinePage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var workflowId = body.getUUID("workflow");
    var pageState = body.getString("pageState");
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user ->
      performWorkflowOperation(user, workflowId, workflow ->
          timelineDatabaseTable.nextTimelinePage(workflow.id(), pageState)
            .thenCompose(page -> collectTimelineInformation(user, page))
            .thenAccept(futureResponse::complete),
        () -> futureResponse.complete(Maps.newHashMap())));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> collectTimelineInformation(
    User user, DatabasePage<TimelineDatabaseEntry> page
  ) {
    return timelineFactory.createTimeline(page.content())
      .thenApply(timeline -> Map.of("timeline",
        assemblyTimelineInformation(user, timeline), "page", page.pageState()));
  }

  private Map<String, Object> assemblyTimelineInformation(
    User user, Timeline timeline
  ) {
    var information = Lists.newArrayList();
    var entries = timeline.findAllEntries().stream()
      .sorted(Comparator.comparing(TimelineEntry::rawTime).reversed()).toList();
    for (var entry : entries) {
      var entryInformation = Maps.<String, Object>newHashMap();
      entryInformation.put("title", entry.title(translation, user));
      entryInformation.put("description", entry.description(translation, user));
      entryInformation.put("level", entry.level());
      entryInformation.put("time", entry.formattedTime());
      if (entry instanceof TimelineWorkflowFailureEntry failureEntry) {
        entryInformation.put("isFailure", true);
        entryInformation.put("failureModuleName",
          translation.translate(user, failureEntry.moduleName()));
        entryInformation.put("failureStepName",
          translation.translate(user, failureEntry.stepName()));
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
      body.getUUID("workflow"), workflow ->
        timelineDatabaseTable.clearWorkflowEntries(workflow.id()), () -> {}));
  }
}

