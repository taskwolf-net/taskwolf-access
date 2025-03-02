package com.dulno.access;

import com.dulno.core.environment.DulnoEnvironment;
import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.worker.WorkerConfiguration;
import com.dulno.workflow.WorkflowModule;
import lombok.RequiredArgsConstructor;
import com.dulno.core.database.DatabaseConnection;
import com.dulno.core.database.DatabaseKeyspace;
import com.dulno.core.locale.Translation;
import com.dulno.core.log.Log;
import com.dulno.core.mail.MailFactory;
import com.dulno.core.module.ModuleLoader;
import com.dulno.core.worker.WorkerDistribution;
import com.dulno.core.worker.client.WorkerProxyClient;
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
  private final WorkerConfiguration workerConfiguration;
  private final WorkflowModule workflowModule;
  private final Translation translation;
  private final ErrorRepository errorRepository;
  private final MailFactory mailFactory;
  private final Hashing hashing;
  private final DulnoEnvironment environment;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("accessLog", log);
    beanFactory.registerSingleton("moduleLoader", moduleLoader);
    beanFactory.registerSingleton("databaseConnection", databaseConnection);
    beanFactory.registerSingleton("databaseKeyspace", databaseKeyspace);
    beanFactory.registerSingleton("distribution", distribution);
    beanFactory.registerSingleton("workerProxyClient", workerProxyClient);
    beanFactory.registerSingleton("workerConfiguration", workerConfiguration);
    beanFactory.registerSingleton("workflowModule", workflowModule);
    beanFactory.registerSingleton("translation", translation);
    beanFactory.registerSingleton("errorRepository", errorRepository);
    beanFactory.registerSingleton("mailFactory", mailFactory);
    beanFactory.registerSingleton("hashing", hashing);
    beanFactory.registerSingleton("environment", environment);
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
