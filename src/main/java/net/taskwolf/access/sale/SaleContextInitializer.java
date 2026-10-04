package net.taskwolf.access.sale;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.sale.SaleDatabaseTable;
import net.taskwolf.core.sale.SaleMessageDatabaseTable;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class SaleContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final SaleDatabaseTable saleDatabaseTable;
  private final SaleMessageDatabaseTable saleMessageDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("saleDatabaseTable", saleDatabaseTable);
    beanFactory.registerSingleton("saleMessageDatabaseTable",
      saleMessageDatabaseTable);
  }
}
