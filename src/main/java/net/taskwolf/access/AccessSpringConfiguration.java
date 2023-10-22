package net.taskwolf.access;

import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import net.taskwolf.access.verification.VerificationConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;

@Configuration
public class AccessSpringConfiguration {
  private Key secretKey;
  private String proxyToken;
  private ProxyStatus proxyStatus;

  @Bean
  Key provideSecretKey() {
    return secretKey;
  }

  @Bean
  @Qualifier("proxyToken")
  String provideProxyToken() {
    return proxyToken;
  }

  @Bean
  ProxyStatus provideProxyStatus() {
    return proxyStatus;
  }

  @PostConstruct
  private void initializeSecretKey() throws Exception {
    secretKey = new SecretKeySpec(VerificationConfiguration.createAndLoad()
      .verificationSecret().getBytes(StandardCharsets.UTF_8),
      SignatureAlgorithm.HS256.getJcaName());
    var proxyConfiguration = ProxyConfiguration.createAndLoad();
    proxyToken = proxyConfiguration.proxyToken();
    proxyStatus = proxyConfiguration.proxyEnabled() ? ProxyStatus.ENABLED :
      ProxyStatus.DISABLED;
  }
}
