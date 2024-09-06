package net.taskwolf.access.component;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.locale.Translation;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.module.ModuleLoader;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.workflow.component.ComponentInformation;
import net.taskwolf.core.workflow.component.ComponentVariable;
import net.taskwolf.core.workflow.component.input.InputComponentDataType;
import net.taskwolf.core.workflow.component.input.InputComponentVariable;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
public final class ComponentController extends TaskwolfRestController {
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final ModuleLoader moduleLoader;
  private final CoreModule coreModule;
  private final Translation translation;

  private ComponentController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    ModuleLoader moduleLoader, CoreModule coreModule,
    Translation translation
  ) {
    super(secretKey, userDatabaseTable);
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.moduleLoader = moduleLoader;
    this.coreModule = coreModule;
    this.translation = translation;
  }

  @RequestMapping(path = "/components/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findComponents(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var registeredModule = moduleLoader.findRegisteredModuleById(
      body.getString("module"));
    if (registeredModule.isEmpty()) {
      return CompletableFuture.completedFuture(Map.of("components",
        Lists.newArrayList()));
    }
    return findUser(request).thenApply(user ->
      body.getString("componentType").equalsIgnoreCase("trigger") ?
        findTriggerComponents(user.language(), registeredModule.get().module()) :
        findActionComponents(user.language(), registeredModule.get().module()));
  }

  @RequestMapping(path = "/components/find/unauthorized/{language}/",
    method = RequestMethod.POST)
  public Map<String, Object> findComponentsUnauthorized(
    @RequestBody String payload, HttpServletResponse response,
    @PathVariable("language") String language
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var registeredModule = moduleLoader.findRegisteredModuleById(
      body.getString("module"));
      return registeredModule
        .map(module -> body.getString("componentType").equalsIgnoreCase("trigger") ?
          findTriggerComponents(language, module.module()) :
          findActionComponents(language, module.module()))
        .orElseGet(() -> Map.of("components", Lists.newArrayList()));
  }

  private Map<String, Object> findTriggerComponents(String language, Module module) {
    return Map.of("components", module.triggerRepository().allTriggers()
      .stream().map(trigger -> superficialComponentInformation(language,
        trigger.type(), trigger.information())).toList());
  }

  private Map<String, Object> findActionComponents(String language, Module module) {
    return Map.of("components", module.actionRepository().allActions()
      .stream().map(action -> superficialComponentInformation(language,
        action.type(), action.information())).toList());
  }

  @RequestMapping(path = "/component/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findComponent(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var module = body.getString("module");
    var type = body.getString("type");
    return findUser(request).thenApply(user ->
      body.getString("componentType").equalsIgnoreCase("trigger") ?
        findTriggerComponent(user.language(), module, type) :
        findActionComponent(user.language(), module, type));
  }

  private Map<String, Object> findTriggerComponent(
    String language, String module, String type
  ) {
    return coreModule.findTrigger(module, type).map(trigger ->
        detailedComponentInformation(language, trigger.type(), trigger.information()))
      .orElseGet(Maps::newHashMap);
  }

  private Map<String, Object> findActionComponent(
    String language, String module, String type
  ) {
    return coreModule.findAction(module, type).map(action ->
        detailedComponentInformation(language, action.type(), action.information()))
      .orElseGet(Maps::newHashMap);
  }

  private Map<String, Object> superficialComponentInformation(
    String language, String identifier, ComponentInformation component
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("identifier", identifier);
    information.put("name", translation.translate(language, component.name()));
    information.put("description", translation.translate(language,
      component.description()));
    information.put("novelty", component.novelty());
    return information;
  }

  private Map<String, Object> detailedComponentInformation(
    String language, String identifier, ComponentInformation component
  ) {
    var information = superficialComponentInformation(language, identifier, component);
    information.put("inputVariables",
      componentVariablesInformation(language, component.inputVariables()));
    information.put("outputVariables",
      componentVariablesInformation(language, component.outputVariables()));
    return information;
  }

  private <T extends ComponentVariable> List<Map<String, Object>> componentVariablesInformation(
    String language, List<T> variables
  ) {
    var variablesInformation = Lists.<Map<String, Object>>newArrayList();
    for (var variable : variables) {
      var variableInformation = Maps.<String, Object>newHashMap();
      variableInformation.put("identifier", variable.identifier());
      variableInformation.put("name", translation.translate(language,
        variable.displayName()));
      if (variable instanceof InputComponentVariable inputVariable) {
        variableInformation.put("description", translation.translate(language,
          inputVariable.description()));
        variableInformation.put("placeholder", translation.translate(language,
          inputVariable.placeholder()));
        variableInformation.put("type", inputVariable.type());
        variableInformation.put("dataType", inputVariable.dataType());
      }
      variablesInformation.add(variableInformation);
    }
    return variablesInformation;
  }

  @RequestMapping(path = "/component/select/items/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findSelectItems(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var module = body.getString("module");
    var type = body.getString("type");
    var component = body.getString("componentType").equalsIgnoreCase("trigger") ?
      coreModule.findTriggerInformation(module, type) :
      coreModule.findActionInformation(module, type);
    if (component.isEmpty()) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var select = component.get().inputVariables().stream()
      .filter(variable -> variable.dataType().equals(InputComponentDataType.SELECT))
      .filter(variable -> variable.identifier().equals(body.getString("select")))
      .findFirst();
    if (select.isEmpty()) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var previousInputs =body.getObject("previousInputs").raw().toMap()
      .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
        entry -> (String) entry.getValue()));;
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user ->
      userTargetDatabaseTable.findTargetSecured(user.id()).thenAccept(target ->
        select.get().select().compile(user, target, previousInputs)
          .thenApply(items -> items.stream().map(item -> new JSONObject(Map.of(
            "identifier", item.identifier(), "name", item.name())).toString()))
          .thenAccept(items -> futureResponse.complete(Map.of("items", items)))));
    return futureResponse;
  }
}