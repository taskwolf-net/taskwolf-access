package com.dulno.access.bundle;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.bundle.BundlePresetRepository;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class BundleContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final BundleDatabaseTable bundleDatabaseTable;
  private final BundlePresetRepository bundlePresetRepository;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("bundleDatabaseTable", bundleDatabaseTable);
    beanFactory.registerSingleton("bundlePresetRepository", bundlePresetRepository);
  }
}
