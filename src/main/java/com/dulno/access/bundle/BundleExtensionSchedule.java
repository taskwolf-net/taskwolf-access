package com.dulno.access.bundle;

import com.dulno.core.bundle.Bundle;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.locale.Translation;
import com.dulno.core.mail.Mail;
import com.dulno.core.organization.OrganizationDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.worker.WorkerDistribution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

@Component
public final class BundleExtensionSchedule {
  private final BundleDatabaseTable bundleDatabaseTable;
  private final UserDatabaseTable userDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final WorkerDistribution distribution;
  private final Mail notificationMail;
  private final Translation translation;
  private final ScheduledExecutorService executorService =
    Executors.newScheduledThreadPool(1);
  private ScheduledFuture<?> scheduler;

  public BundleExtensionSchedule(
    BundleDatabaseTable bundleDatabaseTable, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    WorkerDistribution distribution,
    @Qualifier("notificationMail") Mail notificationMail, Translation translation
  ) {
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.userDatabaseTable = userDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.distribution = distribution;
    this.notificationMail = notificationMail;
    this.translation = translation;
  }

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
      .thenAcceptAsync(bundles -> findExtendingBundles(bundles.stream()
        .filter(Objects::nonNull).toList()));
  }

  private static final long NOTIFICATION_THRESHOLD = 1000L * 60 * 60 * 24 * 7 * 2;

  private void findExtendingBundles(List<Bundle> bundles) {
    var estimation = System.currentTimeMillis() + NOTIFICATION_THRESHOLD;
    for (var bundle : bundles) {
      if (estimation > bundle.expiration() &&
        estimation - (1000L * 60 * 60 * 24) < bundle.expiration()
      ) {
        sendExtensionNotification(bundle);
      }
    }
  }

  private void sendExtensionNotification(Bundle bundle) {
    if (bundle.bundleType().isTrial() || bundle.bundleType().isIndividual()) {
      sendExtensionNotification(bundle.ownerId());
      return;
    }
    organizationDatabaseTable.findOrganization(bundle.ownerId())
      .thenAccept(organization -> sendExtensionNotification(organization.owner()));
  }

  private void sendExtensionNotification(UUID receiverId) {
    userDatabaseTable.findUser(receiverId)
      .thenAccept(this::sendExtensionNotification);
  }

  private void sendExtensionNotification(User receiver) {
    var title = translation.translate(receiver, "bundle.extension.email.title");
    var body = String.format(translation.translate(receiver,
      "bundle.extension.email.body"), receiver.name());
    notificationMail.send(receiver.email(), title, body);
  }

  public void stop() {
    scheduler.cancel(false);
  }
}