package net.taskwolf.access.module;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.module.ModuleLoader;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;

@RestController
public final class ModuleController extends TaskwolfRestController {
  private final ModuleLoader moduleLoader;

  private ModuleController(
    Key secretKey, UserDatabaseTable userDatabaseTable, ModuleLoader moduleLoader
  ) {
    super(secretKey, userDatabaseTable);
    this.moduleLoader = moduleLoader;
  }

  @RequestMapping(path = "/module/find/", method = RequestMethod.POST)
  public Map<String, Object> findActions(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    return moduleLoader.findModule(body.getString("module"))
      .map(this::moduleInformation).orElseGet(Maps::newHashMap);
  }

  @RequestMapping(path = "/modules/available/", method = RequestMethod.POST)
  public Map<String, Object> findAllModules(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var componentType = body.getString("componentType");
    return Map.of("modules", moduleLoader.allModules().stream()
      .filter(module -> module.moduleInformation().type().isPublic())
      .filter(module -> moduleFitsComponentType(module, componentType))
      .map(this::moduleInformation).toList());
  }

  private boolean moduleFitsComponentType(Module module, String componentType) {
    if (componentType.equalsIgnoreCase("trigger")) {
      return !module.triggerInformation().isEmpty();
    } else if(componentType.equalsIgnoreCase("action")) {
      return !module.actionInformation().isEmpty();
    }
    return false;
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
