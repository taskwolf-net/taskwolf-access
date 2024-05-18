package net.taskwolf.access;

import jakarta.annotation.PostConstruct;
import net.taskwolf.core.mail.TaskwolfMail;
import net.taskwolf.core.mail.TaskwolfMailConfiguration;
import net.taskwolf.core.user.ProfilePictureConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccessSpringConfiguration {
  private TaskwolfMail verificationMail;
  private TaskwolfMail changeMail;
  private String defaultProfilePicture;

  @Bean
  @Qualifier("verificationMail")
  TaskwolfMail provideVerificationMail() {
    return verificationMail;
  }

  @Bean
  @Qualifier("changeMail")
  TaskwolfMail provideChangeMail() {
    return changeMail;
  }

  @Bean
  @Qualifier("defaultProfilePicture")
  String provideDefaultProfilePicture() {
    return defaultProfilePicture;
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
  private void initializePasswordMail() throws Exception {
    var mailConfiguration = TaskwolfMailConfiguration.createAndLoad("change");
    changeMail = TaskwolfMail.create(mailConfiguration.mail(),
      mailConfiguration.smtpMailHost(), mailConfiguration.smtpMailPort(),
      mailConfiguration.imapMailHost(), mailConfiguration.imapMailPort(),
      mailConfiguration.mailUser(), mailConfiguration.mailPassword());
  }

  @PostConstruct
  private void initializeDefaultProfilePicture() throws Exception {
    defaultProfilePicture = ProfilePictureConfiguration.createAndLoad()
      .defaultProfilePicture();
  }
}
