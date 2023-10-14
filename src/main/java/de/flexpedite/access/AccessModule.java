package de.flexpedite.access;

import de.flexpedite.access.verification.VerificationConfiguration;
import de.flexpedite.core.CoreModule;
import de.flexpedite.core.module.Module;
import de.flexpedite.core.module.ModuleDescription;
import de.flexpedite.core.module.ModuleLoadPriority;
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
      userDatabaseTable()));
  }

  @Override
  public void disable() {

  }
}
