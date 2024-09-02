package net.taskwolf.access;

import lombok.RequiredArgsConstructor;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.database.DatabaseConnection;
import net.taskwolf.core.database.DatabaseKeyspace;
import net.taskwolf.core.log.Log;
import net.taskwolf.core.mail.MailFactory;
import net.taskwolf.core.module.ModuleLoader;
import net.taskwolf.core.worker.WorkerDistribution;
import net.taskwolf.core.worker.client.WorkerProxyClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.security.Key;

@RequiredArgsConstructor(staticName = "create")
public class AccessContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final Log log;
  private final Key homeKey;
  private final Key productKey;
  private final Key refreshKey;
  private final ModuleLoader moduleLoader;
  private final DatabaseConnection databaseConnection;
  private final DatabaseKeyspace databaseKeyspace;
  private final WorkerDistribution distribution;
  private final WorkerProxyClient workerProxyClient;
  private final CoreModule coreModule;
  private final MailFactory mailFactory;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("accessLog", log);
    beanFactory.registerSingleton("moduleLoader", moduleLoader);
    beanFactory.registerSingleton("databaseConnection", databaseConnection);
    beanFactory.registerSingleton("databaseKeyspace", databaseKeyspace);
    beanFactory.registerSingleton("distribution", distribution);
    beanFactory.registerSingleton("workerProxyClient", workerProxyClient);
    beanFactory.registerSingleton("coreModule", coreModule);
    beanFactory.registerSingleton("mailFactory", mailFactory);
    applicationContext.addBeanFactoryPostProcessor(
      new KeyPostProcessor(homeKey, productKey, refreshKey));
  }

  private final class KeyPostProcessor implements BeanDefinitionRegistryPostProcessor {
    private final Key homeKey;
    private final Key productKey;
    private final Key refreshKey;

    private KeyPostProcessor(Key homeKey, Key productKey, Key refreshKey) {
      this.homeKey = homeKey;
      this.productKey = productKey;
      this.refreshKey = refreshKey;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(
      BeanDefinitionRegistry registry
    ) throws BeansException {
      registerKey(registry, "homeKey", homeKey, false);
      registerKey(registry, "productKey", productKey, true);
      registerKey(registry, "refreshKey", refreshKey, false);
    }

    private void registerKey(
      BeanDefinitionRegistry registry, String name, Key key, boolean isPrimary
    ) {
      var definition = BeanDefinitionBuilder.rootBeanDefinition(Key.class)
        .getBeanDefinition();
      definition.setInstanceSupplier(() -> key);
      definition.setPrimary(isPrimary);
      registry.registerBeanDefinition(name, definition);
    }
  }
}
