package net.taskwolf.access.module;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
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

  @RequestMapping(path = "/modules/all/", method = RequestMethod.GET)
  public Map<String, Object> findAllModules() {
    var modules = coreModule.moduleLoader().allModules().stream()
      .filter(module -> module.moduleInformation().type().isPublic()).toList();
    var modulesInformation = Lists.<Map<String, Object>>newArrayList();
    for (var module : modules) {
      var information = Maps.<String, Object>newHashMap();
      information.put("name", module.moduleInformation().name());
      information.put("description", module.moduleInformation().description());
      information.put("logo", module.moduleInformation().logo());
      modulesInformation.add(information);
    }
    return Map.of("modules", modulesInformation);
  }
}
