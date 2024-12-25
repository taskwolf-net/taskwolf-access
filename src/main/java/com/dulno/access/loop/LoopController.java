package com.dulno.access.loop;

import com.dulno.core.loop.LoopInformation;
import com.dulno.core.loop.LoopInformationRepository;
import com.dulno.core.workflow.component.ComponentVariable;
import com.dulno.core.workflow.component.input.InputComponentVariable;
import com.dulno.core.workflow.component.output.DynamicOutputComponentVariable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.locale.Translation;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class LoopController extends DulnoRestController {
  private final Translation translation;
  private final LoopInformationRepository loopRepository;

  private LoopController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    Translation translation, LoopInformationRepository loopRepository
  ) {
    super(secretKey, userDatabaseTable);
    this.translation = translation;
    this.loopRepository = loopRepository;
  }

  @RequestMapping(path = "/loops/find/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findLoops(
    HttpServletRequest request
  ) {
    var loops = loopRepository.findAll();
    return findUser(request).thenApply(user -> Map.of("loops",
      loops.stream().map(loop -> superficialLoopInformation(user, loop))));
  }

  private Map<String, Object> superficialLoopInformation(
    User user, LoopInformation loop
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("identifier", loop.identifier());
    information.put("name", translation.translate(user, loop.name()));
    information.put("description", translation.translate(user, loop.description()));
    return information;
  }

  @RequestMapping(path = "/loop/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findLoop(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var loop = loopRepository.findByIdentifier(body.getString("identifier"));
    return findUser(request).thenApply(user -> loop.map(value ->
      detailedLoopInformation(user, value, body.getObject("currentContent").raw(),
        body.getObjectList("previousComponents").stream()
          .map(DulnoRequestBody::raw).toList()))
      .orElseGet(Maps::newHashMap));
  }

  private Map<String, Object> detailedLoopInformation(
    User user, LoopInformation loop,
    JSONObject currentContent, List<JSONObject> previousComponents
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.putAll(superficialLoopInformation(user, loop));
    information.put("inputVariables",
      componentVariablesInformation(user.language(), loop.inputVariables(),
        currentContent, previousComponents));
    information.put("outputVariables",
      componentVariablesInformation(user.language(), loop.outputVariables(),
        currentContent, previousComponents));
    return information;
  }

  public <T extends ComponentVariable> List<Map<String, Object>> componentVariablesInformation(
    String language, List<T> variables, JSONObject currentContent,
    List<JSONObject> previousComponents
  ) {
    var variablesInformation = Lists.<Map<String, Object>>newArrayList();
    for (var variable : variables) {
      if (variable instanceof DynamicOutputComponentVariable dynamicVariable) {
        variablesInformation.addAll(dynamicVariable.variableFunction()
          .compile(currentContent, previousComponents).stream()
          .map(entry -> assembleVariableInformation(entry, language)).toList());
        continue;
      }
      variablesInformation.add(assembleVariableInformation(variable, language));
    }
    return variablesInformation;
  }

  private <T extends ComponentVariable> Map<String, Object> assembleVariableInformation(
    T variable, String language
  ) {
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
    return variableInformation;
  }
}
