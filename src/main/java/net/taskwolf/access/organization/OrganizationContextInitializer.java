package net.taskwolf.access.organization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.organization.team.TeamDatabaseTable;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class OrganizationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final TeamDatabaseTable teamDatabaseTable;
  private final TeamTargetDatabaseTable teamTargetDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("organizationDatabaseTable", organizationDatabaseTable);
    beanFactory.registerSingleton("organizationTeamDatabaseTable", teamDatabaseTable);
    beanFactory.registerSingleton("organizationTeamTargetDatabaseTable", teamTargetDatabaseTable);
  }
}
