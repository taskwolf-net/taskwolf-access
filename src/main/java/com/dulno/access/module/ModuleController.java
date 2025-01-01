package com.dulno.access.module;

import com.dulno.workflow.integration.Integration;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.locale.Translation;
import com.dulno.core.module.ModuleLoader;
import com.dulno.core.module.RegisteredModule;
import com.dulno.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class ModuleController extends DulnoRestController {
  private final ModuleLoader moduleLoader;
  private final Translation translation;

  private ModuleController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    ModuleLoader moduleLoader, Translation translation
  ) {
    super(secretKey, userDatabaseTable);
    this.moduleLoader = moduleLoader;
    this.translation = translation;
  }

  @RequestMapping(path = "/module/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findActions(
    HttpServletRequest request, @RequestBody String payload, HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return findUser(request).thenApply(user ->
      moduleLoader.findRegisteredModuleById(body.getString("module"))
        .map(module -> moduleInformation(user.language(), module))
        .orElseGet(Maps::newHashMap));
  }

  @RequestMapping(path = "/modules/available/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findAllModules(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var componentType = body.getString("componentType");
    return findUser(request).thenApply(user ->
      Map.of("modules", moduleLoader.allRegisteredModules().stream()
        .filter(module -> module.module().moduleInformation().type().isPublic())
        .filter(module -> module.module() instanceof Integration)
        .filter(module -> moduleFitsComponentType((Integration) module.module(),
          componentType))
        .map(module -> moduleInformation(user.language(), module)).toList()));
  }

  private boolean moduleFitsComponentType(Integration module, String componentType) {
    if (componentType.equalsIgnoreCase("trigger")) {
      return !module.triggerRepository().isEmpty();
    } else if(componentType.equalsIgnoreCase("action")) {
      return !module.actionRepository().isEmpty();
    }
    return false;
  }

  @RequestMapping(path = "/modules/trigger/hot/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findHotTriggerModules(
    HttpServletRequest request
  ) {
    var modules = Lists.<RegisteredModule>newArrayList();
    modules.add(moduleLoader.findRegisteredModuleById("manual").get());
    modules.add(moduleLoader.findRegisteredModuleById("scheduler").get());
    modules.add(moduleLoader.findRegisteredModuleById("webhook").get());
    modules.add(moduleLoader.findRegisteredModuleById("sub-workflow").get());
    modules.add(moduleLoader.findRegisteredModuleById("device").get());
    modules.add(moduleLoader.findRegisteredModuleById("table").get());
    return findUser(request).thenApply(user -> Map.of("modules", modules.stream()
      .map(module -> moduleInformation(user.language(), module)).toList()));
  }

  @RequestMapping(path = "/modules/action/hot/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findHotActionModules(
    HttpServletRequest request
  ) {
    var modules = Lists.<RegisteredModule>newArrayList();
    modules.add(moduleLoader.findRegisteredModuleById("table").get());
    modules.add(moduleLoader.findRegisteredModuleById("webhook").get());
    modules.add(moduleLoader.findRegisteredModuleById("json").get());
    modules.add(moduleLoader.findRegisteredModuleById("device").get());
    modules.add(moduleLoader.findRegisteredModuleById("mail").get());
    modules.add(moduleLoader.findRegisteredModuleById("sub-workflow").get());
    return findUser(request).thenApply(user -> Map.of("modules", modules.stream()
      .map(module -> moduleInformation(user.language(), module)).toList()));
  }

  @RequestMapping(path = "/modules/all/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findAllModules(
    HttpServletRequest request
  ) {
    return findUser(request).thenApply(user ->
      Map.of("modules", moduleLoader.allRegisteredModules().stream()
        .filter(module -> module.module().moduleInformation().type().isPublic())
        .map(module -> moduleInformation(user.language(), module)).toList()));
  }

  @RequestMapping(path = "/modules/all/unauthorized/{language}/",
    method = RequestMethod.GET)
  public Map<String, Object> findAllModulesUnauthorized(
    @PathVariable("language") String language
  ) {
    return Map.of("modules", moduleLoader.allRegisteredModules().stream()
        .filter(module -> module.module().moduleInformation().type().isPublic())
        .map(module -> moduleInformation(language, module)).toList());
  }

  private Map<String, Object> moduleInformation(
    String language, RegisteredModule module
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", module.name());
    information.put("name", translation.translate(language,
      module.module().moduleInformation().name()));
    information.put("description", translation.translate(language,
      module.module().moduleInformation().description()));
    information.put("novelty", module.module().moduleInformation().novelty());
    information.put("logo", module.module().moduleInformation().logo());
    return information;
  }
}
