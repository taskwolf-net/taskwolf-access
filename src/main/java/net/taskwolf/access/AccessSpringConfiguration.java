package net.taskwolf.access;

import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import net.taskwolf.access.verification.VerificationConfiguration;
import net.taskwolf.core.distribution.DistributionConfiguration;
import net.taskwolf.core.distribution.Node;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.Key;

@Configuration
public class AccessSpringConfiguration {
  private Key secretKey;
  private String verificationMailHost;
  private String verificationMail;
  private String verificationMailPassword;
  private String proxyToken;
  private ProxyStatus proxyStatus;
  private HttpClient httpClient;
  private Node self;

  @Bean
  Key provideSecretKey() {
    return secretKey;
  }

  @Bean
  @Qualifier("verificationMailHost")
  String provideVerificationMailHost() {
    return verificationMailHost;
  }

  @Bean
  @Qualifier("verificationMail")
  String provideVerificationMail() {
    return verificationMail;
  }

  @Bean
  @Qualifier("verificationMailPassword")
  String provideVerificationMailPassword() {
    return verificationMailPassword;
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

  @Bean
  HttpClient provideHttpClient() {
    return httpClient;
  }

  @Bean
  Node provideSelf() {
    return self;
  }

  @PostConstruct
  private void initializeSecretKey() throws Exception {
    var verificationConfiguration = VerificationConfiguration.createAndLoad();
    secretKey = new SecretKeySpec(verificationConfiguration.verificationSecret()
      .getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
    verificationMailHost = verificationConfiguration.verificationMailHost();
    verificationMail = verificationConfiguration.verificationMail();
    verificationMailPassword = verificationConfiguration.verificationMailPassword();
    var proxyConfiguration = ProxyConfiguration.createAndLoad();
    proxyToken = proxyConfiguration.proxyToken();
    proxyStatus = proxyConfiguration.proxyEnabled() ? ProxyStatus.ENABLED :
      ProxyStatus.DISABLED;
    httpClient = HttpClient.newHttpClient();
    self = DistributionConfiguration.createAndLoad().self();
  }
}
