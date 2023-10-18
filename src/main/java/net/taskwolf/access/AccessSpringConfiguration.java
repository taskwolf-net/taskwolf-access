package net.taskwolf.access;

import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import net.taskwolf.access.verification.VerificationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;

@Configuration
public class AccessSpringConfiguration {
  private Key secretKey;

  @Bean
  Key provideSecretKey() {
    return secretKey;
  }

  @PostConstruct
  private void initializeSecretKey() throws Exception {
    secretKey = new SecretKeySpec(VerificationConfiguration.createAndLoad()
      .verificationSecret().getBytes(StandardCharsets.UTF_8),
      SignatureAlgorithm.HS256.getJcaName());
  }
}
