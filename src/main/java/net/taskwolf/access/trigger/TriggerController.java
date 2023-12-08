package net.taskwolf.access.trigger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.trigger.TriggerInformation;
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
public final class TriggerController extends TaskwolfRestController {
  private final CoreModule coreModule;

  private TriggerController(
    Key secretKey, UserDatabaseTable userDatabaseTable, CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/triggers/find/", method = RequestMethod.POST)
  public Map<String, Object> findTriggers(
    @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var registeredModule = coreModule.moduleLoader().findModule(module);
    if (registeredModule.isEmpty()) {
      return Map.of("triggers", Lists.newArrayList());
    }
    var triggers = Lists.<Map<String, Object>>newArrayList();
    for (var trigger : registeredModule.get().triggerInformation()) {
      triggers.add(superficialTriggerInformation(trigger));
    }
    return Map.of("triggers", triggers);
  }

  @RequestMapping(path = "/trigger/find/", method = RequestMethod.POST)
  public Map<String, Object> findTrigger(
    @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var type = (String) input.get("type");
    var trigger = coreModule.findTriggerInformation(module, type);
    return trigger.map(this::detailedTriggerInformation)
      .orElseGet(Maps::newHashMap);
  }

  private Map<String, Object> superficialTriggerInformation(
    TriggerInformation trigger
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("identifier", trigger.identifier());
    information.put("name", trigger.name());
    information.put("description", trigger.description());
    return information;
  }

  private Map<String, Object> detailedTriggerInformation(
    TriggerInformation trigger
  ) {
    var information = superficialTriggerInformation(trigger);
    information.put("inputVariables",
      componentVariablesInformation(trigger.inputVariables()));
    information.put("outputVariables",
      componentVariablesInformation(trigger.outputVariables()));
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

  @RequestMapping(path = "/trigger/select/items/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findSelectItems(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var type = (String) input.get("type");
    var previousInputs = (Map<String, String>) input.get("previousInputs");
    var selectIdentifier = (String) input.get("select");
    var trigger = coreModule.findTriggerInformation(module, type);
    if (trigger.isEmpty()) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var select = trigger.get().inputVariables().stream()
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
