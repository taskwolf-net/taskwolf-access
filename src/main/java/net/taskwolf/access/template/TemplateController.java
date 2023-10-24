package net.taskwolf.access.template;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.template.Template;
import net.taskwolf.core.template.TemplateDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@CrossOrigin
@RestController
public final class TemplateController extends TaskwolfRestController {
  private final TemplateDatabaseTable templateDatabaseTable;

  private TemplateController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    TemplateDatabaseTable templateDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.templateDatabaseTable = templateDatabaseTable;
  }

  @RequestMapping(path = "/templates/all/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findAllTemplates() {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    templateDatabaseTable.findAllTemplates().thenAccept(templates ->
      futureResponse.complete(Map.of("templates",
        templates.stream().map(this::assemblyTemplateInformation).toList())));
    return futureResponse;
  }

  @RequestMapping(path = "/templates/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findTemplate(
    @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    templateDatabaseTable.findTemplatesByModule(module).thenAccept(templates ->
      futureResponse.complete(Map.of("templates",
        templates.stream().map(this::assemblyTemplateInformation).toList())));
    return futureResponse;
  }

  private Map<String, Object> assemblyTemplateInformation(Template template) {
    var information = Maps.<String, Object>newHashMap();
    information.put("name", template.name());
    information.put("description", template.description());
    var triggerInformation = Maps.<String, Object>newHashMap();
    triggerInformation.put("module", template.trigger().module());
    triggerInformation.put("type", template.trigger().type());
    triggerInformation.put("content", template.trigger().content());
    information.put("trigger", triggerInformation);
    var actionsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var action : template.actions()) {
      var actionInformation = Maps.<String, Object>newHashMap();
      actionInformation.put("module", action.module());
      actionInformation.put("type", action.type());
      actionInformation.put("content", action.content());
      actionsInformation.add(actionInformation);
    }
    information.put("actions", actionsInformation);
    return information;
  }
}

