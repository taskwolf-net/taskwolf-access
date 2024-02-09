package net.taskwolf.access;

import com.google.common.collect.Lists;
import com.google.inject.Injector;
import net.taskwolf.core.log.Log;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.module.ModuleDescription;
import net.taskwolf.core.module.ModuleInformation;
import net.taskwolf.core.module.ModuleLoadPriority;
import org.springframework.boot.SpringApplication;

@ModuleDescription(name = "access", version = "1.0.0-SNAPSHOT",
  priority = ModuleLoadPriority.HIGH)
public final class AccessModule extends Module {
  private Log log;
  private SpringApplication springApplication;
  private AccessContextInitializer contextInitializer;

  public AccessModule(Injector injector) {
    super(injector);
  }

  @Override
  public void enable() {
    System.setProperty("jdk.httpclient.allowRestrictedHeaders",
      "host,connection,content-length,upgrade");
    log = injector().getInstance(Log.class).subLog("Access");
    springApplication = injector().getInstance(SpringApplication.class);
    contextInitializer = injector().getInstance(AccessContextInitializer.class);
    springApplication.addInitializers(contextInitializer);
  }

  @Override
  public void disable() {
    var initializers = Lists.newArrayList(springApplication.getInitializers());
    initializers.remove(contextInitializer);
    springApplication.setInitializers(initializers);
  }

  @Override
  public ModuleInformation moduleInformation() {
    return ModuleInformation.create("Access", "", "",
      ModuleInformation.Type.HIDDEN);
  }
}
