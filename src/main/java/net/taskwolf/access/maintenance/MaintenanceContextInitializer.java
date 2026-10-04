package net.taskwolf.access.maintenance;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.maintenance.MaintenanceDatabaseTable;
import net.taskwolf.core.maintenance.MaintenanceSchedule;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class MaintenanceContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final MaintenanceDatabaseTable maintenanceDatabaseTable;
  private final MaintenanceSchedule maintenanceSchedule;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("maintenanceDatabaseTable",
      maintenanceDatabaseTable);
    beanFactory.registerSingleton("maintenanceSchedule", maintenanceSchedule);
  }
}
