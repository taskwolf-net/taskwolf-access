package com.dulno.access.bundle;

import com.dulno.access.organization.OrganizationModificationController;
import com.dulno.access.setting.AccountSettingController;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.worker.WorkerDistribution;
import lombok.RequiredArgsConstructor;
import com.dulno.core.bundle.Bundle;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.organization.OrganizationDatabaseTable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

@Component
@RequiredArgsConstructor
public final class BundleResetSchedule {
  private final BundleDatabaseTable bundleDatabaseTable;
  private final AccountSettingController accountSettingController;
  private final OrganizationModificationController organizationModificationController;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final WorkerDistribution distribution;
  private final ScheduledExecutorService executorService =
    Executors.newScheduledThreadPool(1);
  private ScheduledFuture<?> scheduler;

  private static final long CHECK_INTERVAL = 1000L * 60 * 60 * 24;
  private static final TimeUnit CHECK_TIME_UNIT = TimeUnit.MILLISECONDS;

  public void start() {
    scheduler = executorService.scheduleAtFixedRate(this::execute,
      calculateInitialDelay(), CHECK_INTERVAL, CHECK_TIME_UNIT);
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
    AsyncIterator.execute(distribution.findAssignedUsers("access"), owner ->
        bundleDatabaseTable.bundleExists(owner).thenCompose(exists -> exists ?
          bundleDatabaseTable.findBundle(owner) :
          CompletableFuture.completedFuture(null)))
      .thenAcceptAsync(bundles -> findBundlesToReset(bundles.stream()
        .filter(Objects::nonNull).toList()));
  }

  private void findBundlesToReset(List<Bundle> bundles) {
    var current = ZonedDateTime.now();
    var next = current.minusMonths(6);
    if (next.getDayOfMonth() != current.getDayOfMonth()) {
      next = next.withDayOfMonth(next.getMonth().length(
        next.toLocalDate().isLeapYear()));
    }
    var maximumValue = next.toInstant().toEpochMilli();
    for (var bundle : bundles) {
      if (bundle.expiration() > 0 && bundle.expiration() <= maximumValue) {
        resetBundle(bundle);
      }
    }
  }

  private void resetBundle(Bundle bundle) {
    if (bundle.bundleType().isTrial() || bundle.bundleType().isIndividual()) {
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
