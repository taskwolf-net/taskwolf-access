package net.taskwolf.access;

import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import net.taskwolf.core.distribution.DistributionConfiguration;
import net.taskwolf.core.distribution.Node;
import net.taskwolf.core.mail.TaskwolfMail;
import net.taskwolf.core.mail.TaskwolfMailConfiguration;
import net.taskwolf.core.user.ProfilePictureConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.Key;

@Configuration
public class AccessSpringConfiguration {
  private TaskwolfMail verificationMail;
  private TaskwolfMail changeMail;
  private String proxyToken;
  private ProxyStatus proxyStatus;
  private HttpClient httpClient;
  private Node self;
  private String defaultProfilePicture;

  @Bean
  @Qualifier("verificationMail")
  TaskwolfMail provideVerificationMail() {
    return verificationMail;
  }

  @Bean
  @Qualifier("changeMail")
  TaskwolfMail provideChangeMail() {
    return changeMail;
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

  @Bean
  @Qualifier("defaultProfilePicture")
  String provideDefaultProfilePicture() {
    return defaultProfilePicture;
  }

  @PostConstruct
  private void initializeVerificationMail() throws Exception {
    var mailConfiguration = TaskwolfMailConfiguration.createAndLoad("verification");
    verificationMail = TaskwolfMail.create(mailConfiguration.mail(),
      mailConfiguration.smtpMailHost(), mailConfiguration.smtpMailPort(),
      mailConfiguration.imapMailHost(), mailConfiguration.imapMailPort(),
      mailConfiguration.mailUser(), mailConfiguration.mailPassword());
  }

  @PostConstruct
  private void initializePasswordMail() throws Exception {
    var mailConfiguration = TaskwolfMailConfiguration.createAndLoad("change");
    changeMail = TaskwolfMail.create(mailConfiguration.mail(),
      mailConfiguration.smtpMailHost(), mailConfiguration.smtpMailPort(),
      mailConfiguration.imapMailHost(), mailConfiguration.imapMailPort(),
      mailConfiguration.mailUser(), mailConfiguration.mailPassword());
  }

  @PostConstruct
  private void initializeDistribution() throws Exception {
    var proxyConfiguration = ProxyConfiguration.createAndLoad();
    proxyToken = proxyConfiguration.proxyToken();
    proxyStatus = proxyConfiguration.proxyEnabled() ? ProxyStatus.ENABLED :
      ProxyStatus.DISABLED;
    httpClient = HttpClient.newHttpClient();
    self = DistributionConfiguration.createAndLoad().self();
  }

  @PostConstruct
  private void initializeDefaultProfilePicture() throws Exception {
    defaultProfilePicture = ProfilePictureConfiguration.createAndLoad()
      .defaultProfilePicture();
  }
}
