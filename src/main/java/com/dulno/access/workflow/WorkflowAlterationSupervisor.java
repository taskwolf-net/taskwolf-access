package com.dulno.access.workflow;

import lombok.RequiredArgsConstructor;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.user.User;
import com.dulno.workflow.structure.WorkflowEntry;
import com.dulno.workflow.timeline.TimelineDatabaseEntry;
import com.dulno.workflow.timeline.TimelineDatabaseTable;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor(staticName = "create")
public final class WorkflowAlterationSupervisor {
  private final TimelineDatabaseTable timelineDatabaseTable;
  private final UUID workflowId;
  private final WorkflowEntry currentEntry;
  private final String name;
  private final String description;
  private final List<DulnoRequestBody> actions;
  private final List<DulnoRequestBody> conditions;
  private final DulnoRequestBody loop;

  public void evaluate(User actor) {
    var time = System.currentTimeMillis();
    var actorContent =  new JSONObject(Map.of("actor", actor.id().toString())).toString();
    if (!currentEntry.name().equals(name) || !currentEntry.description().equals(description)) {
      createWorkflowTimelineEntry(time, "timeline-workflow-presentation", actorContent);
    }
    if (actions.size() > currentEntry.actionIds().size()) {
      createWorkflowTimelineEntry(time, "timeline-workflow-action-add", actorContent);
    }
    if (actions.size() < currentEntry.actionIds().size()) {
      createWorkflowTimelineEntry(time, "timeline-workflow-action-remove", actorContent);
    }
    if (conditions.size() > currentEntry.conditionIds().size()) {
      createWorkflowTimelineEntry(time, "timeline-workflow-condition-add", actorContent);
    }
    if (conditions.size() < currentEntry.conditionIds().size()) {
      createWorkflowTimelineEntry(time, "timeline-workflow-condition-remove", actorContent);
    }
    if (loop.getBoolean("enabled") && currentEntry.loopId() == null) {
      createWorkflowTimelineEntry(time, "timeline-workflow-loop-add", actorContent);
    }
    if (!loop.getBoolean("enabled") && currentEntry.loopId() != null) {
      createWorkflowTimelineEntry(time, "timeline-workflow-loop-remove", actorContent);
    }
  }

  private void createWorkflowTimelineEntry(long time, String type, String content) {
    timelineDatabaseTable.generateAvailableEntryId().thenAccept(id ->
      timelineDatabaseTable.insertEntry(TimelineDatabaseEntry.create(id,
        workflowId, time, type, content)));
  }
}
