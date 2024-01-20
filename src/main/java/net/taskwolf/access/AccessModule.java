package net.taskwolf.access;

import com.google.inject.Injector;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.module.ModuleDescription;
import net.taskwolf.core.module.ModuleInformation;
import net.taskwolf.core.module.ModuleLoadPriority;
import org.springframework.boot.SpringApplication;

@ModuleDescription(name = "access", version = "1.0.0-SNAPSHOT",
  priority = ModuleLoadPriority.HIGH)
public final class AccessModule extends Module {
  public AccessModule(Injector injector) {
    super(injector);
  }

  @Override
  public void enable() {
    System.setProperty("jdk.httpclient.allowRestrictedHeaders",
      "host,connection,content-length,upgrade");
   injector().getInstance(SpringApplication.class).addInitializers(
    injector().getInstance(AccessContextInitializer.class));
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
