package net.taskwolf.access.template;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.action.ActionInformation;
import net.taskwolf.core.module.ModuleInformation;
import net.taskwolf.core.template.Template;
import net.taskwolf.core.template.TemplateDatabaseTable;
import net.taskwolf.core.trigger.TriggerInformation;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
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
    return findUser(request).thenCompose(user -> findAllTemplates(user.language()));
  }

  @RequestMapping(path = "/team/templates/all/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findAllTemplates(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    return findAllTemplates(body.getString("language"));
  }

  private CompletableFuture<Map<String, Object>> findAllTemplates(String language) {
    return templateDatabaseTable.findAllTemplates()
      .thenApply(templates -> Map.of("templates", templates.stream()
        .map(template -> assemblyTemplateInformation(template, language)).toList()));
  }

  @RequestMapping(path = "/templates/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findModuleTemplates(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var module = body.getString("module");
    return findUser(request)
      .thenCompose(user -> templateDatabaseTable.findTemplatesByModule(module)
        .thenApply(templates -> Map.of("templates", templates.stream()
          .map(template -> assemblyTemplateInformation(template, user)).toList())));
  }

  @RequestMapping(path = "/team/template/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findTemplate(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var templateId = body.getUUID("template");
    return templateDatabaseTable.templateExists(templateId)
      .thenCompose(exists -> findTemplate(templateId,
        body.getString("language"), exists));
  }

  private CompletableFuture<Map<String, Object>> findTemplate(
    UUID templateId, String language, boolean templateExists
  ) {
    if (!templateExists) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return templateDatabaseTable.findTemplate(templateId)
      .thenApply(template -> assemblyTemplateInformation(template, language));
  }

  private Map<String, Object> assemblyTemplateInformation(
    Template template, User user
  ) {
    return assemblyTemplateInformation(template, user.language());
  }

  private Map<String, Object> assemblyTemplateInformation(
    Template template, String language
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", template.id());
    information.put("name", template.translateName(language));
    information.put("description", template.translateDescription(language));
    information.put("accessType", template.accessType());
    information.put("modules", template.modules());
    var triggerInformation = Maps.<String, Object>newHashMap();
    var triggerModule = template.trigger().module();
    triggerInformation.put("module", triggerModule);
    triggerInformation.put("moduleLogo", coreModule.findModuleInformation(triggerModule)
      .map(ModuleInformation::logo).orElse(""));
    var triggerType = template.trigger().type();
    triggerInformation.put("type", triggerType);
    triggerInformation.put("typeDescription", coreModule.translate(language,
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
      actionInformation.put("typeDescription", coreModule.translate(language,
        coreModule.findActionInformation(actionModule, actionType)
          .map(ActionInformation::description).orElse("")));
      actionsInformation.add(actionInformation);
    }
    information.put("actions", actionsInformation);
    return information;
  }
}

