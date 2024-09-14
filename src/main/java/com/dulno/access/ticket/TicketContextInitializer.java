package com.dulno.access.ticket;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import com.dulno.core.ticket.TicketDatabaseTable;
import com.dulno.core.ticket.TicketMessageDatabaseTable;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public class TicketContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final TicketDatabaseTable ticketDatabaseTable;
  private final TicketMessageDatabaseTable ticketMessageDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("ticketDatabaseTable", ticketDatabaseTable);
    beanFactory.registerSingleton("ticketMessageDatabaseTable", ticketMessageDatabaseTable);
  }
}
