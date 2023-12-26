package net.taskwolf.access.verification;

import lombok.Getter;
import lombok.experimental.Accessors;
import net.taskwolf.core.configuration.Configuration;
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
  @Getter
  private String verificationMailHost;
  @Getter
  private String verificationMail;
  @Getter
  private String verificationMailPassword;

  private VerificationConfiguration(String path) {
    super(path);
  }

  @Override
  protected void deserialize(JSONObject json) {
    verificationSecret = json.getString("verificationSecret");
    verificationMailHost = json.getString("verificationMailHost");
    verificationMail = json.getString("verificationMail");
    verificationMailPassword = json.getString("verificationMailPassword");
  }
}
