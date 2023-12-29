package net.taskwolf.access.component;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.user.UserDatabaseTable;
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

@RestController
public final class ComponentController extends TaskwolfRestController {
  private final CoreModule coreModule;

  private ComponentController(
    Key secretKey, UserDatabaseTable userDatabaseTable, CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/components/find/", method = RequestMethod.POST)
  public Map<String, Object> findComponents(
    @RequestBody Map<String, Object> input
  ) {
    var componentType = (String) input.get("componentType");
    var module = (String) input.get("module");
    var registeredModule = coreModule.moduleLoader().findModule(module);
    if (registeredModule.isEmpty()) {
      return Map.of("components", Lists.newArrayList());
    }
    var information = componentType.equalsIgnoreCase("trigger") ?
      registeredModule.get().triggerInformation() :
      registeredModule.get().actionInformation();
    var components = Lists.<Map<String, Object>>newArrayList();
    for (var component : information) {
      components.add(superficialComponentInformation(component));
    }
    return Map.of("components", components);
  }

  @RequestMapping(path = "/component/find/", method = RequestMethod.POST)
  public Map<String, Object> findComponent(
    @RequestBody Map<String, Object> input
  ) {
    var componentType = (String) input.get("componentType");
    var module = (String) input.get("module");
    var type = (String) input.get("type");
    var component = componentType.equalsIgnoreCase("trigger") ?
      coreModule.findTriggerInformation(module, type) :
      coreModule.findActionInformation(module, type);
    return component.map(this::detailedComponentInformation)
      .orElseGet(Maps::newHashMap);
  }

  private Map<String, Object> superficialComponentInformation(
    ComponentInformation component
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("identifier", component.identifier());
    information.put("name", component.name());
    information.put("description", component.description());
    return information;
  }

  private Map<String, Object> detailedComponentInformation(
    ComponentInformation component
  ) {
    var information = superficialComponentInformation(component);
    information.put("inputVariables",
      componentVariablesInformation(component.inputVariables()));
    information.put("outputVariables",
      componentVariablesInformation(component.outputVariables()));
    return information;
  }

  private <T extends ComponentVariable> List<Map<String, Object>> componentVariablesInformation(
    List<T> variables
  ) {
    var variablesInformation = Lists.<Map<String, Object>>newArrayList();
    for (var variable : variables) {
      var variableInformation = Maps.<String, Object>newHashMap();
      variableInformation.put("identifier", variable.identifier());
      variableInformation.put("name", variable.displayName());
      if (variable instanceof InputComponentVariable inputVariable) {
        variableInformation.put("description", inputVariable.description());
        variableInformation.put("type", inputVariable.type());
        variableInformation.put("dataType", inputVariable.dataType());
      }
      variablesInformation.add(variableInformation);
    }
    return variablesInformation;
  }

  @RequestMapping(path = "/component/select/items/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findSelectItems(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var componentType = (String) input.get("componentType");
    var module = (String) input.get("module");
    var type = (String) input.get("type");
    var previousInputs = (Map<String, String>) input.get("previousInputs");
    var selectIdentifier = (String) input.get("select");
    var component = componentType.equalsIgnoreCase("trigger") ?
      coreModule.findTriggerInformation(module, type) :
      coreModule.findActionInformation(module, type);
    if (component.isEmpty()) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var select = component.get().inputVariables().stream()
      .filter(variable -> variable.dataType().equals(InputComponentDataType.SELECT))
      .filter(variable -> variable.identifier().equals(selectIdentifier))
      .findFirst();
    if (select.isEmpty()) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return select.get().select().compile(findUserId(request), previousInputs)
      .thenApply(items -> Map.of("items", items));
  }
}