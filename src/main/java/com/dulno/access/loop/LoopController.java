package com.dulno.access.loop;

import com.dulno.access.component.ComponentController;
import com.dulno.core.loop.LoopInformation;
import com.dulno.core.loop.LoopInformationRepository;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.locale.Translation;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class LoopController extends DulnoRestController {
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
      loops.stream().map(loop -> findLoopInformation(user, loop))));
  }

  @RequestMapping(path = "/loop/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findLoop(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var loop = loopRepository.findByIdentifier(body.getString("identifier"));
    return findUser(request).thenApply(user -> loop.map(value ->
      findLoopInformation(user, value)).orElseGet(Maps::newHashMap));
  }

  private Map<String, Object> findLoopInformation(
    User user, LoopInformation loop
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("identifier", loop.identifier());
    information.put("name", translation.translate(user, loop.name()));
    information.put("description", translation.translate(user, loop.description()));
    information.put("inputVariables",
      componentController.componentVariablesInformation(user.language(),
        loop.inputVariables()));
    information.put("outputVariables",
      componentController.componentVariablesInformation(user.language(),
        loop.outputVariables()));
    return information;
  }
}
