package net.taskwolf.access.module;

import com.google.common.collect.Maps;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.Map;

@RestController
public final class ModuleController extends TaskwolfRestController {
  private final CoreModule coreModule;

  private ModuleController(
    Key secretKey, UserDatabaseTable userDatabaseTable, CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/module/find/", method = RequestMethod.POST)
  public Map<String, Object> findActions(
    @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    return coreModule.moduleLoader().findModule(module)
      .map(this::moduleInformation).orElseGet(Maps::newHashMap);
  }

  @RequestMapping(path = "/modules/all/", method = RequestMethod.GET)
  public Map<String, Object> findAllModules() {
    return Map.of("modules", coreModule.moduleLoader().allModules().stream()
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
