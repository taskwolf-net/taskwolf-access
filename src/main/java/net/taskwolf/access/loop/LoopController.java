package net.taskwolf.access.loop;

import net.taskwolf.access.component.ComponentController;
import net.taskwolf.workflow.loop.LoopInformation;
import net.taskwolf.workflow.loop.LoopInformationRepository;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.locale.Translation;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
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
public final class LoopController extends TaskwolfRestController {
  private final Translation translation;
  private final LoopInformationRepository loopRepository;
  private final ComponentController componentController;

  private LoopController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    Translation translation, LoopInformationRepository loopRepository,
    ComponentController componentController
  ) {
    super(secretKey, userDatabaseTable);
    this.translation = translation;
    this.loopRepository = loopRepository;
    this.componentController = componentController;
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
    var body = TaskwolfRequestBody.of(payload, response);
    var loop = loopRepository.findByIdentifier(body.getString("identifier"));
    return findUser(request).thenCompose(user -> loop.map(value ->
      detailedLoopInformation(user, value, body.getObject("currentContent").raw(),
        body.getObjectList("previousComponents").stream()
          .map(TaskwolfRequestBody::raw).toList()))
      .orElseGet(() -> CompletableFuture.completedFuture(Maps.newHashMap())));
  }

  private CompletableFuture<Map<String, Object>> detailedLoopInformation(
    User user, LoopInformation loop,
    JSONObject currentContent, List<JSONObject> previousComponents
  ) {
    return componentController.componentVariablesInformation(user.language(),
        loop.inputVariables(), currentContent, previousComponents)
      .thenCompose(inputInformation ->
        componentController.componentVariablesInformation(user.language(),
            loop.outputVariables(), currentContent, previousComponents)
          .thenApply(outputInformation -> assemblyDetailedLoopInformation(
            user, loop, inputInformation, outputInformation)));
  }

  private Map<String, Object> assemblyDetailedLoopInformation(
    User user, LoopInformation loop,
    List<Map<String, Object>> inputInformation,
    List<Map<String, Object>> outputInformation
  ) {
    var information = superficialLoopInformation(user, loop);
    information.put("inputVariables", inputInformation);
    information.put("outputVariables", outputInformation);
    return information;
  }
}
