package net.taskwolf.access.component;

import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.workflow.WorkflowModule;
import net.taskwolf.workflow.component.output.DynamicOutputComponentVariable;
import net.taskwolf.workflow.integration.Integration;
import net.taskwolf.workflow.trigger.TriggerInformation;
import net.taskwolf.workflow.component.input.DynamicInputComponentVariable;
import net.taskwolf.workflow.component.input.SelectableInputComponentVariable;
import net.taskwolf.workflow.component.output.OutputComponentVariable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.locale.Translation;
import net.taskwolf.core.module.ModuleLoader;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.workflow.component.ComponentInformation;
import net.taskwolf.workflow.component.ComponentVariable;
import net.taskwolf.workflow.component.input.InputComponentDataType;
import net.taskwolf.workflow.component.input.InputComponentVariable;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RestController
public final class ComponentController extends TaskwolfRestController {
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final TeamTargetDatabaseTable teamTargetDatabaseTable;
  private final ModuleLoader moduleLoader;
  private final WorkflowModule workflowModule;
  private final Translation translation;

  private ComponentController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable, ModuleLoader moduleLoader,
    WorkflowModule workflowModule, Translation translation
  ) {
    super(secretKey, userDatabaseTable);
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.teamTargetDatabaseTable = teamTargetDatabaseTable;
    this.moduleLoader = moduleLoader;
    this.workflowModule = workflowModule;
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
    if (registeredModule.get().module() instanceof Integration integration) {
      return findUser(request).thenApply(user ->
        body.getString("componentType").equalsIgnoreCase("trigger") ?
          findTriggerComponents(user.language(), integration) :
          findActionComponents(user.language(), integration));
    }
    return CompletableFuture.completedFuture(Map.of("components",
      Lists.newArrayList()));
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
        .filter(module -> module.module() instanceof Integration)
        .map(module -> (Integration) module.module())
        .map(module -> body.getString("componentType").equalsIgnoreCase("trigger") ?
          findTriggerComponents(language, module) :
          findActionComponents(language, module))
        .orElseGet(() -> Map.of("components", Lists.newArrayList()));
  }

  private Map<String, Object> findTriggerComponents(String language, Integration module) {
    return Map.of("components", module.triggerRepository().allTriggers()
      .stream().map(trigger -> superficialComponentInformation(language,
        trigger.type(), trigger.information())).toList());
  }

  private Map<String, Object> findActionComponents(String language, Integration module) {
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
    var currentContent = body.getObject("currentContent").raw();
    var previousComponents = body.getObjectList("previousComponents").stream()
      .map(TaskwolfRequestBody::raw).toList();
    return findUser(request).thenCompose(user ->
      body.getString("componentType").equalsIgnoreCase("trigger") ?
        findTriggerComponent(user.language(), module, type, currentContent,
          previousComponents) :
        findActionComponent(user.language(), module, type, currentContent,
          previousComponents));
  }

  private CompletableFuture<Map<String, Object>> findTriggerComponent(
    String language, String module, String type,
    JSONObject currentContent, List<JSONObject> previousComponents
  ) {
    return workflowModule.findTrigger(module, type).map(trigger ->
        detailedComponentInformation(language, trigger.type(),
          trigger.information(), currentContent, previousComponents))
      .orElseGet(() -> CompletableFuture.completedFuture(Maps.newHashMap()));
  }

  private CompletableFuture<Map<String, Object>> findActionComponent(
    String language, String module, String type,
    JSONObject currentContent, List<JSONObject> previousComponents
  ) {
    return workflowModule.findAction(module, type).map(action ->
        detailedComponentInformation(language, action.type(),
          action.information(), currentContent, previousComponents))
      .orElseGet(() -> CompletableFuture.completedFuture(Maps.newHashMap()));
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

  private CompletableFuture<Map<String, Object>> detailedComponentInformation(
    String language, String identifier, ComponentInformation component,
    JSONObject currentContent, List<JSONObject> previousComponents
  ) {
    var outputVariables = Lists.newArrayList(component.outputVariables());
    if (component instanceof TriggerInformation) {
      outputVariables.addAll(collectTriggerOutputVariables());
    }
    return componentVariablesInformation(language,
      Lists.newArrayList(component.inputVariables()), currentContent,
      previousComponents)
      .thenCompose(inputInformation -> componentVariablesInformation(language,
        outputVariables, currentContent, previousComponents)
        .thenApply(outputInformation -> assemblyDetailedComponentInformation(
          language, identifier, component, inputInformation, outputInformation)));
  }

  private Map<String, Object> assemblyDetailedComponentInformation(
    String language, String identifier, ComponentInformation component,
    List<Map<String, Object>> inputInformation,
    List<Map<String, Object>> outputInformation
    ) {
    var information = superficialComponentInformation(language, identifier, component);
    information.put("inputVariables", inputInformation);
    information.put("outputVariables", outputInformation);
    return information;
  }

  private List<OutputComponentVariable> collectTriggerOutputVariables() {
    var outputVariables = Lists.<OutputComponentVariable>newArrayList();
    outputVariables.add(OutputComponentVariable.create(
      "access.trigger.formatted.time", "formattedTime"));
    outputVariables.add(OutputComponentVariable.create(
      "access.trigger.formatted.date", "formattedDate"));
    outputVariables.add(OutputComponentVariable.create(
      "access.trigger.unix.time", "unixTime"));
    return outputVariables;
  }

  public <T extends ComponentVariable> CompletableFuture<List<Map<String, Object>>>
  componentVariablesInformation(
    String language, List<T> variables,
    JSONObject currentContent, List<JSONObject> previousComponents
  ) {
    var indexedVariables = IntStream.range(0, variables.size())
      .mapToObj(i -> Pair.of(i, variables.get(i))).toList();
    return AsyncIterator.execute(indexedVariables,
        entry -> assembleVariableInformation(language, entry.getRight(),
          currentContent, previousComponents)
          .thenApply(result -> Pair.of(entry.getLeft(), result)))
      .thenApply(result -> result.stream()
        .sorted(Comparator.comparingInt(Pair::getLeft))
        .flatMap(pair -> pair.getRight().stream()).toList());
  }

  private <T extends ComponentVariable> CompletableFuture<List<Map<String, Object>>>
  assembleVariableInformation(
    String language, T variable,
    JSONObject currentContent, List<JSONObject> previousComponents
  ) {
    if (variable instanceof DynamicOutputComponentVariable dynamicOutputVariable) {
      return dynamicOutputVariable.variableFunction()
        .compile(currentContent, previousComponents)
        .thenCompose(outputs -> componentVariablesInformation(language, outputs,
          currentContent, previousComponents));
    }
    return CompletableFuture.completedFuture(Lists.newArrayList(
      assembleVariableInformation(language, variable)));
  }

  private <T extends ComponentVariable> Map<String, Object> assembleVariableInformation(
    String language, T variable
  ) {
    var variableInformation = Maps.<String, Object>newHashMap();
    variableInformation.put("identifier", variable.identifier());
    if (variable instanceof DynamicInputComponentVariable inputVariable) {
      variableInformation.putAll(dynamicInputVariableInformation(inputVariable));
      return variableInformation;
    }
    variableInformation.put("name", translation.translate(language,
      variable.displayName()));
    if (variable instanceof InputComponentVariable inputVariable) {
      variableInformation.putAll(inputVariableInformation(language, inputVariable));
    }
    return variableInformation;
  }

  private Map<String, Object> inputVariableInformation(
    String language, InputComponentVariable variable
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("description", translation.translate(language,
      variable.description()));
    information.put("placeholder", translation.translate(language,
      variable.placeholder()));
    information.put("type", variable.type());
    information.put("dataType", variable.dataType());
    return information;
  }

  private Map<String, Object> dynamicInputVariableInformation(
    DynamicInputComponentVariable variable
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("dataType", variable.dataType());
    information.put("requiredPredecessors", variable.requiredPredecessors());
    return information;
  }

  @RequestMapping(path = "/component/select/items/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findSelectItems(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var component = findSelectableInputComponentVariable(body.getString("module"),
      body.getString("type"), body.getString("componentType"),
      body.getString("select"));
    if (component.isEmpty()) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var previousInputs = body.getObject("previousInputs").raw().toMap()
      .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
        entry -> (String) entry.getValue()));;
    return findUser(request).thenCompose(user ->
      findSelectItemsTarget(user.id()).thenCompose(target -> component.get()
        .select().compile(user, target, previousInputs)
        .thenApply(items -> items.stream().map(item -> new JSONObject(Map.of(
          "identifier", item.identifier(), "name", item.name())).toString()))
        .thenApply(items -> Map.of("items", items))));
  }

  private Optional<SelectableInputComponentVariable> findSelectableInputComponentVariable(
    String module, String type, String componentType, String componentIdentifier
  ) {
    return this.findInputComponentVariable(module, type, componentType,
      componentIdentifier, InputComponentDataType.SELECT);
  }

  @RequestMapping(path = "/component/dynamic/inputs/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findDynamicInputs(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var component = findDynamicInputComponentVariable(body.getString("module"),
      body.getString("type"), body.getString("componentType"),
      body.getString("dynamic"));
    if (component.isEmpty()) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var content = body.getObject("content").raw();
    if (!checkDynamicContentCompleteness(component.get(), content)) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return findUser(request)
      .thenCompose(user -> findSelectItemsTarget(user.id())
        .thenCompose(target -> component.get().variableFunction()
          .compile(user, target, content)
          .thenApply(inputs -> Map.of("dynamicInputs", inputs.stream()
            .map(input -> assembleVariableInformation(user.language(), input))
            .toList()))));
  }

  private Optional<DynamicInputComponentVariable> findDynamicInputComponentVariable(
    String module, String type, String componentType, String componentIdentifier
  ) {
    return this.findInputComponentVariable(module, type, componentType,
      componentIdentifier, InputComponentDataType.DYNAMIC);
  }

  private boolean checkDynamicContentCompleteness(
    DynamicInputComponentVariable component, JSONObject content
  ) {
    for (var predecessor : component.requiredPredecessors()) {
      if (!content.has(predecessor)) {
        return false;
      }
    }
    return true;
  }

  private <T extends InputComponentVariable> Optional<T> findInputComponentVariable(
    String module, String type, String componentType, String componentIdentifier,
    InputComponentDataType dataType
  ) {
    var component = componentType.equalsIgnoreCase("trigger") ?
      workflowModule.findTriggerInformation(module, type) :
      workflowModule.findActionInformation(module, type);
    return component.flatMap(componentInformation ->
      componentInformation.inputVariables().stream()
        .filter(variable -> variable.dataType().equals(dataType))
        .filter(variable -> variable.identifier().equals(componentIdentifier))
        .map(entry -> (T) entry)
        .findFirst());
  }

  private CompletableFuture<UUID> findSelectItemsTarget(UUID userId) {
    return userTargetDatabaseTable.findTargetSecured(userId)
      .thenCompose(target -> findSelectItemsTarget(userId, target));
  }

  private CompletableFuture<UUID> findSelectItemsTarget(UUID userId, UUID target) {
    return userId.equals(target) ? CompletableFuture.completedFuture(target) :
      teamTargetDatabaseTable.findTargetSecured(userId)
        .thenApply(team -> team.orElse(target));
  }
}