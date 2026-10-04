package net.taskwolf.access.verification;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VerificationSpringConfiguration {
  @Autowired
  private SessionCloseSchedule sessionCloseSchedule;

  @PostConstruct
  private void startSessionCloseSchedule() throws Exception {
    sessionCloseSchedule.start();
  }
}