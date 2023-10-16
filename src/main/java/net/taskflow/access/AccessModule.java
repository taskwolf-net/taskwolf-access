package net.taskflow.access;

import net.taskflow.access.verification.VerificationConfiguration;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.module.ModuleDescription;
import net.taskwolf.core.module.ModuleLoadPriority;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@ModuleDescription(name = "access", version = "1.0.0-SNAPSHOT",
  priority = ModuleLoadPriority.HIGH)
public final class AccessModule extends Module {
  public AccessModule(CoreModule coreModule) {
    super(coreModule);
  }

  @Override
  public void enable() throws Exception {
    var secretKey = new SecretKeySpec(VerificationConfiguration.createAndLoad()
      .verificationSecret().getBytes(StandardCharsets.UTF_8),
      SignatureAlgorithm.HS256.getJcaName());
    springApplication().addInitializers(AccessContextInitializer.create(secretKey,
      userDatabaseTable(), triggerDatabaseTable(), actionDatabaseTable(),
      workflowDatabaseTable()));
  }

  @Override
  public void disable() {

  }
}
