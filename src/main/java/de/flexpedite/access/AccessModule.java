package de.flexpedite.access;

import com.google.common.collect.Lists;
import de.flexpedite.core.CoreModule;
import de.flexpedite.core.action.ActionFactory;
import de.flexpedite.core.action.ActionInformation;
import de.flexpedite.core.module.Module;
import de.flexpedite.core.module.ModuleDescription;
import de.flexpedite.core.module.ModuleLoadPriority;
import de.flexpedite.core.trigger.TriggerFactory;
import de.flexpedite.core.trigger.TriggerInformation;

import java.util.List;

@ModuleDescription(name = "access", version = "1.0.0-SNAPSHOT",
  priority = ModuleLoadPriority.HIGH)
public final class AccessModule extends Module {
  public AccessModule(CoreModule coreModule) {
    super(coreModule);
  }

  @Override
  public void enable() {

  }

  @Override
  public void disable() {

  }

  @Override
  public TriggerFactory triggerFactory() {
    return null;
  }

  @Override
  public ActionFactory actionFactory() {
    return null;
  }

  @Override
  public List<TriggerInformation> triggerInformation() {
    return Lists.newArrayList();
  }

  @Override
  public List<ActionInformation> actionInformation() {
    return Lists.newArrayList();
  }
}
