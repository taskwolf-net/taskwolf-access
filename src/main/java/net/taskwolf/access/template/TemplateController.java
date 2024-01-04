package net.taskwolf.access.template;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.action.ActionInformation;
import net.taskwolf.core.module.ModuleInformation;
import net.taskwolf.core.template.Template;
import net.taskwolf.core.template.TemplateDatabaseTable;
import net.taskwolf.core.trigger.TriggerInformation;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TemplateController extends TaskwolfRestController {
  private final CoreModule coreModule;
  private final TemplateDatabaseTable templateDatabaseTable;

  private TemplateController(
    Key secretKey, UserDatabaseTable userDatabaseTable, CoreModule coreModule,
    TemplateDatabaseTable templateDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.coreModule = coreModule;
    this.templateDatabaseTable = templateDatabaseTable;
  }

  @RequestMapping(path = "/templates/all/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findAllTemplates(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user ->
      templateDatabaseTable.findAllTemplates().thenAccept(templates ->
        futureResponse.complete(Map.of("templates", templates.stream().map(template ->
          assemblyTemplateInformation(user, template)).toList()))));
    return futureResponse;
  }

  @RequestMapping(path = "/templates/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findTemplate(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user ->
      templateDatabaseTable.findTemplatesByModule(module).thenAccept(templates ->
        futureResponse.complete(Map.of("templates", templates.stream().map(template ->
          assemblyTemplateInformation(user, template)).toList()))));
    return futureResponse;
  }

  private Map<String, Object> assemblyTemplateInformation(
    User user, Template template
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("name", coreModule.translate(user, template.name()));
    information.put("description", coreModule.translate(user, template.description()));
    information.put("modules", template.modules());
    var triggerInformation = Maps.<String, Object>newHashMap();
    var triggerModule = template.trigger().module();
    triggerInformation.put("module", triggerModule);
    triggerInformation.put("moduleLogo", coreModule.findModuleInformation(triggerModule)
      .map(ModuleInformation::logo).orElse(""));
    var triggerType = template.trigger().type();
    triggerInformation.put("type", triggerType);
    triggerInformation.put("typeDescription", coreModule.translate(user,
      coreModule.findTriggerInformation(triggerModule, triggerType)
        .map(TriggerInformation::description).orElse("")));
    information.put("trigger", triggerInformation);
    var actionsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var action : template.actions()) {
      var actionInformation = Maps.<String, Object>newHashMap();
      var actionModule = action.module();
      actionInformation.put("actionIndex", action.actionIndex());
      actionInformation.put("module", action.module());
      actionInformation.put("moduleLogo", coreModule.findModuleInformation(actionModule)
        .map(ModuleInformation::logo).orElse(""));
      var actionType = action.type();
      actionInformation.put("type", action.type());
      actionInformation.put("typeDescription", coreModule.translate(user,
        coreModule.findActionInformation(actionModule, actionType)
          .map(ActionInformation::description).orElse("")));
      actionsInformation.add(actionInformation);
    }
    information.put("actions", actionsInformation);
    return information;
  }
}

