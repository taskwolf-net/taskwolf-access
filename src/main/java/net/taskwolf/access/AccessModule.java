package net.taskwolf.access;

import net.taskwolf.core.CoreModule;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.module.ModuleDescription;
import net.taskwolf.core.module.ModuleInformation;
import net.taskwolf.core.module.ModuleLoadPriority;

@ModuleDescription(name = "access", version = "1.0.0-SNAPSHOT",
  priority = ModuleLoadPriority.HIGH)
public final class AccessModule extends Module {
  public AccessModule(CoreModule coreModule) {
    super(coreModule);
  }

  @Override
  public void enable() throws Exception {

  }

  @Override
  public void disable() {

  }

  @Override
  public ModuleInformation moduleInformation() {
    return ModuleInformation.create("Access", "", "",
      ModuleInformation.Type.HIDDEN);
  }
}
