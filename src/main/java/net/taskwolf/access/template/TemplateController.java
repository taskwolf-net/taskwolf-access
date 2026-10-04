package net.taskwolf.access.template;

import net.taskwolf.core.module.ModuleLoader;
import net.taskwolf.workflow.WorkflowModule;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.workflow.action.ActionInformation;
import net.taskwolf.core.locale.Translation;
import net.taskwolf.core.module.ModuleInformation;
import net.taskwolf.core.template.Template;
import net.taskwolf.core.template.TemplateDatabaseTable;
import net.taskwolf.workflow.trigger.TriggerInformation;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TemplateController extends TaskwolfRestController {
  private final ModuleLoader moduleLoader;
  private final WorkflowModule workflowModule;
  private final Translation translation;
  private final TemplateDatabaseTable templateDatabaseTable;

  private TemplateController(
    Key secretKey, UserDatabaseTable userDatabaseTable, ModuleLoader moduleLoader,
    WorkflowModule workflowModule, Translation translation,
    TemplateDatabaseTable templateDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.moduleLoader = moduleLoader;
    this.workflowModule = workflowModule;
    this.translation = translation;
    this.templateDatabaseTable = templateDatabaseTable;
  }

  @RequestMapping(path = "/templates/load/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findAllTemplates(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    return findUser(request).thenCompose(user ->
      loadMoreTemplatesTemplates(user.language(), body.getString("pageState"),
        body.getString("search")));
  }

  @RequestMapping(path = "/templates/load/unauthorized/{language}/",
    method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findAllTemplatesUnauthorized(
    @RequestBody String payload, HttpServletResponse response,
    @PathVariable("language") String language
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    return loadMoreTemplatesTemplates(language, body.getString("pageState"),
      body.getString("search"));
  }

  private CompletableFuture<Map<String, Object>> loadMoreTemplatesTemplates(
    String language, String pageState, String search
  ) {
    return templateDatabaseTable.loadNextTemplatePage(pageState, search, language)
      .thenApply(page -> Map.of("templates", page.content().stream()
        .map(template -> assemblyTemplateInformation(template, language)).toList(),
        "page", page.pageState(), "pageNumber", page.pageNumber()));
  }

  @RequestMapping(path = "/template/find/unauthorized/{language}/",
    method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findTemplateUnauthorized(
    @RequestBody String payload, HttpServletResponse response,
    @PathVariable("language") String language
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var templateId = body.getUUID("template");
    return templateDatabaseTable.templateExists(templateId)
      .thenCompose(exists -> findTemplate(templateId, language, exists));
  }

  private CompletableFuture<Map<String, Object>> findTemplate(
    UUID templateId, String language, boolean templateExists
  ) {
    if (!templateExists) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return templateDatabaseTable.findTemplate(templateId)
      .thenApply(template -> assemblyAllTemplateContent(template,
        assemblyTemplateInformation(template, language)));
  }

  private Map<String, Object> assemblyAllTemplateContent(
    Template template, Map<String, Object> information
  ) {
    information.put("englishName", template.englishName());
    information.put("englishDescription", template.englishDescription());
    information.put("germanName", template.germanName());
    information.put("germanDescription", template.germanDescription());
    return information;
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
    triggerInformation.put("moduleLogo", moduleLoader.findModuleInformation(triggerModule)
      .map(ModuleInformation::logo).orElse(""));
    var triggerType = template.trigger().type();
    triggerInformation.put("type", triggerType);
    triggerInformation.put("typeDescription", translation.translate(language,
      workflowModule.findTriggerInformation(triggerModule, triggerType)
        .map(TriggerInformation::description).orElse("")));
    information.put("trigger", triggerInformation);
    var actionsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var action : template.actions()) {
      var actionInformation = Maps.<String, Object>newHashMap();
      var actionModule = action.module();
      actionInformation.put("actionIndex", action.actionIndex());
      actionInformation.put("module", action.module());
      actionInformation.put("moduleLogo", moduleLoader.findModuleInformation(actionModule)
        .map(ModuleInformation::logo).orElse(""));
      var actionType = action.type();
      actionInformation.put("type", action.type());
      actionInformation.put("typeDescription", translation.translate(language,
        workflowModule.findActionInformation(actionModule, actionType)
          .map(ActionInformation::description).orElse("")));
      actionsInformation.add(actionInformation);
    }
    information.put("actions", actionsInformation);
    return information;
  }
}

