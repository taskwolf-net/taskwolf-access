package net.taskwolf.access.module;

import com.google.common.collect.Maps;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.module.ModuleLoader;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;

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
  public Map<String, Object> findActions(
    @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    return moduleLoader.findModule(module).map(this::moduleInformation)
      .orElseGet(Maps::newHashMap);
  }

  @RequestMapping(path = "/modules/all/", method = RequestMethod.GET)
  public Map<String, Object> findAllModules() {
    return Map.of("modules", moduleLoader.allModules().stream()
      .filter(module -> module.moduleInformation().type().isPublic())
      .map(this::moduleInformation).toList());
  }

  private Map<String, Object> moduleInformation(Module module) {
    var information = Maps.<String, Object>newHashMap();
    information.put("name", module.moduleInformation().name());
    information.put("description", module.moduleInformation().description());
    information.put("logo", module.moduleInformation().logo());
    return information;
  }
}
