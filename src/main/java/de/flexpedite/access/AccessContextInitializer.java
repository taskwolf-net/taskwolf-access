package de.flexpedite.access;

import de.flexpedite.core.user.UserDatabaseTable;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

import java.security.Key;

@RequiredArgsConstructor(staticName = "create")
public class AccessContextInitializer implements ApplicationContextInitializer<GenericApplicationContext> {
  private final Key secretKey;
  private final UserDatabaseTable userDatabaseTable;

  @Override
  public void initialize(GenericApplicationContext context) {
    context.getBeanFactory().registerSingleton("secretKey", secretKey);
    context.getBeanFactory().registerSingleton("userDatabaseTable",
      userDatabaseTable);
  }
}