package net.taskwolf.access.maintenance;

import com.google.common.collect.Lists;
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

  @RequestMapping(path = "/maintenance/scheduled/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findScheduledMaintenance() {
    return maintenanceDatabaseTable.findMaintenanceByStatus(
      MaintenanceStatus.SCHEDULED).thenApply(this::assemblyMaintenanceInformation);
  }

  @RequestMapping(path = "/maintenance/running/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findRunningMaintenanceInformation() {
    return maintenanceDatabaseTable.findMaintenanceByStatus(
      MaintenanceStatus.RUNNING).thenApply(this::assemblyMaintenanceInformation);
  }

  private Map<String, Object> assemblyMaintenanceInformation(
    List<Maintenance> maintenanceList
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("found", !maintenanceList.isEmpty());
    if (maintenanceList.isEmpty()) {
      return information;
    }
    var maintenance = maintenanceList.stream()
      .sorted(Comparator.comparingLong(Maintenance::startTime)).findFirst().get();
    information.put("id", maintenance.id());
    information.put("startTime", maintenance.startTime());
    information.put("duration", maintenance.duration());
    return information;
  }
}
