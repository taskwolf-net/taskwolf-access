package net.taskwolf.access;

import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.database.DatabaseConnection;
import net.taskwolf.core.database.DatabaseKeyspace;
import net.taskwolf.core.log.Log;
import net.taskwolf.core.module.ModuleLoader;
import net.taskwolf.core.worker.WorkerDistribution;
import net.taskwolf.core.worker.client.WorkerProxyClient;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.security.Key;

@Singleton
@RequiredArgsConstructor(staticName = "create")
public class AccessContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final Log log;
  private final Key key;
  private final ModuleLoader moduleLoader;
  private final DatabaseConnection databaseConnection;
  private final DatabaseKeyspace databaseKeyspace;
  private final WorkerDistribution distribution;
  private final WorkerProxyClient workerProxyClient;
  private final CoreModule coreModule;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("accessLog", log);
    beanFactory.registerSingleton("key", key);
    beanFactory.registerSingleton("moduleLoader", moduleLoader);
    beanFactory.registerSingleton("databaseConnection", databaseConnection);
    beanFactory.registerSingleton("databaseKeyspace", databaseKeyspace);
    beanFactory.registerSingleton("distribution", distribution);
    beanFactory.registerSingleton("workerProxyClient", workerProxyClient);
    beanFactory.registerSingleton("coreModule", coreModule);
  }
}
