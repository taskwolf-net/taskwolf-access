package net.taskwolf.access.maintenance;

import com.google.common.collect.Maps;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.maintenance.Maintenance;
import net.taskwolf.core.maintenance.MaintenanceDatabaseTable;
import net.taskwolf.core.maintenance.MaintenanceStatus;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
public final class MaintenanceController extends TaskwolfRestController {
  private final MaintenanceDatabaseTable maintenanceDatabaseTable;

  private MaintenanceController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    MaintenanceDatabaseTable maintenanceDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.maintenanceDatabaseTable = maintenanceDatabaseTable;
  }

  @RequestMapping(path = "/maintenance/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findMaintenanceInformation() {
    return findMaintenance(MaintenanceStatus.SCHEDULED).thenCompose(
      scheduledMaintenance -> findMaintenance(MaintenanceStatus.RUNNING)
        .thenApply(runningMaintenance -> assemblyMaintenanceInformation(
          scheduledMaintenance, runningMaintenance)));
  }

  private CompletableFuture<List<Maintenance>> findMaintenance(
    MaintenanceStatus status
  ) {
    return maintenanceDatabaseTable.findMaintenanceByStatus(status);
  }

  private Map<String, Object> assemblyMaintenanceInformation(
    List<Maintenance> scheduledMaintenance, List<Maintenance> runningMaintenance
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("scheduled", assemblyMaintenanceInformation(scheduledMaintenance));
    information.put("running", assemblyMaintenanceInformation(runningMaintenance));
    return information;
  }

  private Map<String, Object> assemblyMaintenanceInformation(
    List<Maintenance> maintenanceList
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("found", !maintenanceList.isEmpty());
    if (maintenanceList.isEmpty()) {
      return information;
    }
    maintenanceList.sort(Comparator.comparingLong(Maintenance::startTime));
    var maintenance = maintenanceList.get(0);
    information.put("id", maintenance.id());
    information.put("startTime", maintenance.startTime());
    information.put("duration", maintenance.duration());
    return information;
  }
}
