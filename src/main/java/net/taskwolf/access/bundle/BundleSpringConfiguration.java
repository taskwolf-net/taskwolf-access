package net.taskwolf.access.bundle;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BundleSpringConfiguration {
  @Autowired
  private BundleResetSchedule bundleResetSchedule;
  @Autowired
  private BundleExtensionSchedule bundleExpirationSchedule;

  @PostConstruct
  private void startBundleResetSchedule() throws Exception {
    bundleResetSchedule.start();
  }

  @PostConstruct
  private void startBundleExpirationSchedule() throws Exception {
    bundleExpirationSchedule.start();
  }
}
