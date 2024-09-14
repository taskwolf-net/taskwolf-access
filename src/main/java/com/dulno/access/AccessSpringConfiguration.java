package com.dulno.access;

import jakarta.annotation.PostConstruct;
import com.dulno.core.mail.Mail;
import com.dulno.core.mail.MailConfiguration;
import com.dulno.core.mail.MailFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccessSpringConfiguration {
  private @Autowired MailFactory mailFactory;
  private Mail verificationMail;
  private Mail orderMail;
  private Mail changeMail;

  @Bean
  @Qualifier("verificationMail")
  Mail provideVerificationMail() {
    return verificationMail;
  }

  @Bean
  @Qualifier("orderMail")
  Mail provideOrderMail() {
    return orderMail;
  }

  @Bean
  @Qualifier("changeMail")
  Mail provideChangeMail() {
    return changeMail;
  }


  @PostConstruct
  private void initializeVerificationMail() throws Exception {
    var mailConfiguration = MailConfiguration.createAndLoad("verification");
    verificationMail = mailFactory.create(mailConfiguration.mail(),
      mailConfiguration.smtpMailHost(), mailConfiguration.smtpMailPort(),
      mailConfiguration.imapMailHost(), mailConfiguration.imapMailPort(),
      mailConfiguration.mailUser(), mailConfiguration.mailPassword());
  }

  @PostConstruct
  private void initializeOrderMail() throws Exception {
    var mailConfiguration = MailConfiguration.createAndLoad("order");
    orderMail = mailFactory.create(mailConfiguration.mail(),
      mailConfiguration.smtpMailHost(), mailConfiguration.smtpMailPort(),
      mailConfiguration.imapMailHost(), mailConfiguration.imapMailPort(),
      mailConfiguration.mailUser(), mailConfiguration.mailPassword());
  }

  @PostConstruct
  private void initializeChangeMail() throws Exception {
    var mailConfiguration = MailConfiguration.createAndLoad("change");
    changeMail = mailFactory.create(mailConfiguration.mail(),
      mailConfiguration.smtpMailHost(), mailConfiguration.smtpMailPort(),
      mailConfiguration.imapMailHost(), mailConfiguration.imapMailPort(),
      mailConfiguration.mailUser(), mailConfiguration.mailPassword());
  }
}
