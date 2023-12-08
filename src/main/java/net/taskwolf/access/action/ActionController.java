package net.taskwolf.access.action;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.action.ActionInformation;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.workflow.component.ComponentVariable;
import net.taskwolf.core.workflow.component.input.InputComponentDataType;
import net.taskwolf.core.workflow.component.input.InputComponentVariable;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class ActionController extends TaskwolfRestController {
  private final CoreModule coreModule;

  private ActionController(
    Key secretKey, UserDatabaseTable userDatabaseTable, CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/actions/find/", method = RequestMethod.POST)
  public Map<String, Object> findActions(
    @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var registeredModule = coreModule.moduleLoader().findModule(module);
    if (registeredModule.isEmpty()) {
      return Map.of("actions", Lists.newArrayList());
    }
    var actions = Lists.<Map<String, Object>>newArrayList();
    for (var action : registeredModule.get().actionInformation()) {
      actions.add(superficialActionInformation(action));
    }
    return Map.of("actions", actions);
  }

  @RequestMapping(path = "/action/find/", method = RequestMethod.POST)
  public Map<String, Object> findAction(
    @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var type = (String) input.get("type");
    var action = coreModule.findActionInformation(module, type);
    return action.map(this::detailedActionInformation)
      .orElseGet(Maps::newHashMap);
  }

  private Map<String, Object> superficialActionInformation(
    ActionInformation action
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("identifier", action.identifier());
    information.put("name", action.name());
    information.put("description", action.description());
    return information;
  }

  private Map<String, Object> detailedActionInformation(
    ActionInformation action
  ) {
    var information = superficialActionInformation(action);
    information.put("inputVariables",
      componentVariablesInformation(action.inputVariables()));
    information.put("outputVariables",
      componentVariablesInformation(action.outputVariables()));
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
        variablesInformation.add(variableInformation);
      }
    }
    return variablesInformation;
  }

  @RequestMapping(path = "/action/select/items/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findSelectItems(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var type = (String) input.get("type");
    var previousInputs = (Map<String, String>) input.get("previousInputs");
    var selectIdentifier = (String) input.get("select");
    var action = coreModule.findActionInformation(module, type);
    if (action.isEmpty()) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var select = action.get().inputVariables().stream()
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
