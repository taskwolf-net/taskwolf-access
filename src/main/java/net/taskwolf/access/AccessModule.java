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
    System.setProperty("jdk.httpclient.allowRestrictedHeaders",
      "host,connection,content-length,upgrade");
    coreModule().springApplication().addInitializers(AccessContextInitializer.create(
      coreModule().databaseConnection(), coreModule().databaseKeyspace(),
      coreModule().userDatabaseTable(), coreModule().userVerificationDatabaseTable(),
      coreModule().userPasswordResetDatabaseTable(),
      coreModule().profilePictureDatabaseTable(),
      coreModule().organizationDatabaseTable(), coreModule().triggerDatabaseTable(),
      coreModule().actionDatabaseTable(), coreModule().conditionDatabaseTable(),
      coreModule().workflowDatabaseTable(), coreModule().workflowExecutionDatabaseTable(),
      coreModule().templateDatabaseTable(), coreModule().timelineDatabaseTable(),
      coreModule().distribution(), coreModule().conditionRepository(),
      coreModule().timelineFactory(), coreModule()));
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
