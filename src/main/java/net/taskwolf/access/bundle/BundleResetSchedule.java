package net.taskwolf.access.bundle;

import lombok.RequiredArgsConstructor;
import net.taskwolf.access.organization.OrganizationModificationController;
import net.taskwolf.access.setting.AccountSettingController;
import net.taskwolf.core.bundle.Bundle;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public final class BundleResetSchedule {
  private final BundleDatabaseTable bundleDatabaseTable;
  private final AccountSettingController accountSettingController;
  private final OrganizationModificationController organizationModificationController;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final ScheduledExecutorService executorService =
    Executors.newScheduledThreadPool(1);
  private ScheduledFuture<?> scheduler;

  private static final int RESET_INTERVAL = 1000 * 60 * 60 * 24;
  private static final TimeUnit RESET_TIME_UNIT = TimeUnit.MILLISECONDS;

  public void start() {
    scheduler = executorService.scheduleAtFixedRate(this::execute,
      calculateInitialDelay(), RESET_INTERVAL, RESET_TIME_UNIT);
  }

  private long calculateInitialDelay() {
    var calendar = Calendar.getInstance();
    calendar.add(Calendar.DAY_OF_MONTH, 1);
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTimeInMillis() - System.currentTimeMillis();
  }

  private void execute() {
    bundleDatabaseTable.findAllBundles().thenAcceptAsync(this::findBundlesToReset);
  }

  private static final long RESET_THRESHOLD = 1000L * 60 * 60 * 24 * 30 * 6;

  private void findBundlesToReset(List<Bundle> bundles) {
    var maximumValue = System.currentTimeMillis() - RESET_THRESHOLD;
    for (var bundle : bundles) {
      if (bundle.expiration() <= maximumValue) {
        resetBundle(bundle);
      }
    }
  }

  private void resetBundle(Bundle bundle) {
    if (bundle.bundleType().isTrial() || bundle.bundleType().isProfessional()) {
      accountSettingController.deleteAccountServices(bundle.ownerId());
      return;
    }
    organizationDatabaseTable.findOrganization(bundle.ownerId())
      .thenAccept(organizationModificationController::deleteOrganization);
  }

  public void stop() {
    scheduler.cancel(false);
  }
}
