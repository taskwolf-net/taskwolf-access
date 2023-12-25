package net.taskwolf.access.workflow;

import lombok.RequiredArgsConstructor;
import net.taskwolf.core.user.User;
import net.taskwolf.core.workflow.WorkflowEntry;
import net.taskwolf.core.workflow.timeline.TimelineDatabaseEntry;
import net.taskwolf.core.workflow.timeline.TimelineDatabaseTable;
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
  private final List<Map<String, Object>> actions;
  private final List<Map<String, Object>> conditions;

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
  }

  private void createWorkflowTimelineEntry(long time, String type, String content) {
    timelineDatabaseTable.generateAvailableEntryId().thenAccept(id ->
      timelineDatabaseTable.insertEntry(TimelineDatabaseEntry.create(id,
        workflowId, time, type, content)));
  }
}
