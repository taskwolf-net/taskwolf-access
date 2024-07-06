package net.taskwolf.access;

import jakarta.annotation.PostConstruct;
import net.taskwolf.core.mail.TaskwolfMail;
import net.taskwolf.core.mail.TaskwolfMailConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccessSpringConfiguration {
  private TaskwolfMail verificationMail;
  private TaskwolfMail orderMail;
  private TaskwolfMail changeMail;

  @Bean
  @Qualifier("verificationMail")
  TaskwolfMail provideVerificationMail() {
    return verificationMail;
  }

  @Bean
  @Qualifier("orderMail")
  TaskwolfMail provideOrderMail() {
    return orderMail;
  }

  @Bean
  @Qualifier("changeMail")
  TaskwolfMail provideChangeMail() {
    return changeMail;
  }


  @PostConstruct
  private void initializeVerificationMail() throws Exception {
    var mailConfiguration = TaskwolfMailConfiguration.createAndLoad("verification");
    verificationMail = TaskwolfMail.create(mailConfiguration.mail(),
      mailConfiguration.smtpMailHost(), mailConfiguration.smtpMailPort(),
      mailConfiguration.imapMailHost(), mailConfiguration.imapMailPort(),
      mailConfiguration.mailUser(), mailConfiguration.mailPassword());
  }

  @PostConstruct
  private void initializeOrderMail() throws Exception {
    var mailConfiguration = TaskwolfMailConfiguration.createAndLoad("order");
    orderMail = TaskwolfMail.create(mailConfiguration.mail(),
      mailConfiguration.smtpMailHost(), mailConfiguration.smtpMailPort(),
      mailConfiguration.imapMailHost(), mailConfiguration.imapMailPort(),
      mailConfiguration.mailUser(), mailConfiguration.mailPassword());
  }

  @PostConstruct
  private void initializeChangeMail() throws Exception {
    var mailConfiguration = TaskwolfMailConfiguration.createAndLoad("change");
    changeMail = TaskwolfMail.create(mailConfiguration.mail(),
      mailConfiguration.smtpMailHost(), mailConfiguration.smtpMailPort(),
      mailConfiguration.imapMailHost(), mailConfiguration.imapMailPort(),
      mailConfiguration.mailUser(), mailConfiguration.mailPassword());
  }
}
