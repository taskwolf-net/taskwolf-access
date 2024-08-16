package net.taskwolf.access.module;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.module.ModuleLoader;
import net.taskwolf.core.module.RegisteredModule;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class ModuleController extends TaskwolfRestController {
  private final ModuleLoader moduleLoader;
  private final CoreModule coreModule;

  private ModuleController(
    Key secretKey, UserDatabaseTable userDatabaseTable, ModuleLoader moduleLoader,
    CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.moduleLoader = moduleLoader;
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/module/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findActions(
    HttpServletRequest request, @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
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
    var body = TaskwolfRequestBody.of(payload, response);
    var componentType = body.getString("componentType");
    return findUser(request).thenApply(user ->
      Map.of("modules", moduleLoader.allRegisteredModules().stream()
        .filter(module -> module.module().moduleInformation().type().isPublic())
        .filter(module -> moduleFitsComponentType(module, componentType))
        .map(module -> moduleInformation(user.language(), module)).toList()));
  }

  private boolean moduleFitsComponentType(RegisteredModule module, String componentType) {
    if (componentType.equalsIgnoreCase("trigger")) {
      return !module.module().triggerRepository().isEmpty();
    } else if(componentType.equalsIgnoreCase("action")) {
      return !module.module().actionRepository().isEmpty();
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
    modules.add(moduleLoader.findRegisteredModuleById("process").get());
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
    modules.add(moduleLoader.findRegisteredModuleById("discord").get());
    modules.add(moduleLoader.findRegisteredModuleById("gmail").get());
    modules.add(moduleLoader.findRegisteredModuleById("device").get());
    modules.add(moduleLoader.findRegisteredModuleById("google-calendar").get());
    modules.add(moduleLoader.findRegisteredModuleById("google-drive").get());
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
    information.put("name", coreModule.translate(language,
      module.module().moduleInformation().name()));
    information.put("description", coreModule.translate(language,
      module.module().moduleInformation().description()));
    information.put("novelty", module.module().moduleInformation().novelty());
    information.put("logo", module.module().moduleInformation().logo());
    return information;
  }
}
