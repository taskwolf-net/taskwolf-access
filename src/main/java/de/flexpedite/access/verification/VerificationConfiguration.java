package de.flexpedite.access.verification;

import de.flexpedite.core.configuration.Configuration;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.json.JSONObject;

@Accessors(fluent = true)
public final class VerificationConfiguration extends Configuration {
  private static final String CONFIGURATION_PATH = "/configurations/verification/verification.json";

  public static VerificationConfiguration createAndLoad() throws Exception {
    var configuration = new VerificationConfiguration(CONFIGURATION_PATH);
    configuration.load();
    return configuration;
  }

  @Getter
  private String verificationSecret;

  private VerificationConfiguration(String path) {
    super(path);
  }

  @Override
  protected void deserialize(JSONObject json) {
    verificationSecret = json.getString("verificationSecret");
  }
}
