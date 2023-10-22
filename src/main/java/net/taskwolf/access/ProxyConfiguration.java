package net.taskwolf.access;


import lombok.Getter;
import lombok.experimental.Accessors;
import net.taskwolf.core.configuration.Configuration;
import org.json.JSONObject;

@Getter
@Accessors(fluent = true)
public final class ProxyConfiguration extends Configuration {
  private static final String CONFIGURATION_PATH = "/configurations/proxy/proxy.json";

  public static ProxyConfiguration createAndLoad() throws Exception {
    var configuration = new ProxyConfiguration(CONFIGURATION_PATH);
    configuration.load();
    return configuration;
  }

  private String proxyToken;
  private boolean proxyEnabled;

  private ProxyConfiguration(String path) {
    super(path);
  }

  @Override
  protected void deserialize(JSONObject json) {
    proxyToken = json.getString("proxyToken");
    proxyEnabled = json.getBoolean("proxyEnabled");
  }
}
