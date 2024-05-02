package net.taskwolf.access.component;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.module.ModuleLoader;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.workflow.component.ComponentInformation;
import net.taskwolf.core.workflow.component.ComponentVariable;
import net.taskwolf.core.workflow.component.input.InputComponentDataType;
import net.taskwolf.core.workflow.component.input.InputComponentVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

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

  private ComponentController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable, ModuleLoader moduleLoader,
    CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.moduleLoader = moduleLoader;
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/components/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findComponents(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var registeredModule = moduleLoader.findModule(body.getString("module"));
    if (registeredModule.isEmpty()) {
      return CompletableFuture.completedFuture(Map.of("components", Lists.newArrayList()));
    }
    return findUser(request).thenApply(user ->
      body.getString("componentType").equalsIgnoreCase("trigger") ?
        findTriggerComponents(user, registeredModule.get()) :
        findActionComponents(user, registeredModule.get()));
  }

  private Map<String, Object> findTriggerComponents(User user, Module module) {
    return Map.of("components", module.triggerRepository().allTriggers()
      .stream().map(trigger -> superficialComponentInformation(user,
        trigger.type(), trigger.information())).toList());
  }

  private Map<String, Object> findActionComponents(User user, Module module) {
    return Map.of("components", module.actionRepository().allActions()
      .stream().map(action -> superficialComponentInformation(user,
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
        findTriggerComponent(user, module, type) :
        findActionComponent(user, module, type));
  }

  private Map<String, Object> findTriggerComponent(
    User user, String module, String type
  ) {
    return coreModule.findTrigger(module, type).map(trigger ->
        detailedComponentInformation(user, trigger.type(), trigger.information()))
      .orElseGet(Maps::newHashMap);
  }

  private Map<String, Object> findActionComponent(
    User user, String module, String type
  ) {
    return coreModule.findAction(module, type).map(action ->
        detailedComponentInformation(user, action.type(), action.information()))
      .orElseGet(Maps::newHashMap);
  }

  private Map<String, Object> superficialComponentInformation(
    User user, String identifier, ComponentInformation component
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("identifier", identifier);
    information.put("name", coreModule.translate(user, component.name()));
    information.put("description", coreModule.translate(user,
      component.description()));
    information.put("novelty", component.novelty());
    return information;
  }

  private Map<String, Object> detailedComponentInformation(
    User user, String identifier, ComponentInformation component
  ) {
    var information = superficialComponentInformation(user, identifier, component);
    information.put("inputVariables",
      componentVariablesInformation(user, component.inputVariables()));
    information.put("outputVariables",
      componentVariablesInformation(user, component.outputVariables()));
    return information;
  }

  private <T extends ComponentVariable> List<Map<String, Object>> componentVariablesInformation(
    User user, List<T> variables
  ) {
    var variablesInformation = Lists.<Map<String, Object>>newArrayList();
    for (var variable : variables) {
      var variableInformation = Maps.<String, Object>newHashMap();
      variableInformation.put("identifier", variable.identifier());
      variableInformation.put("name", coreModule.translate(user, variable.displayName()));
      if (variable instanceof InputComponentVariable inputVariable) {
        variableInformation.put("description", coreModule.translate(user,
          inputVariable.description()));
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
    userTargetDatabaseTable.findTargetSecured(findUserId(request)).thenAccept(
      target -> select.get().select().compile(target, previousInputs)
        .thenAccept(items -> futureResponse.complete(Map.of("items", items))));
    return futureResponse;
  }
}