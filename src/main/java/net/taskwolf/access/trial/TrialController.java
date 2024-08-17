package net.taskwolf.access.trial;

import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfHomeRestController;
import net.taskwolf.core.bundle.*;
import net.taskwolf.core.trial.TrialDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.workflow.operation.OperationDatabaseTable;
import net.taskwolf.core.workflow.throttle.WorkflowThrottleDatabaseTable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TrialController extends TaskwolfHomeRestController {
  private final TrialDatabaseTable trialDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final BundlePresetRepository bundlePresetRepository;
  private final OperationDatabaseTable operationDatabaseTable;
  private final WorkflowThrottleDatabaseTable workflowThrottleDatabaseTable;

  private TrialController(
    @Qualifier("homeKey") Key secretKey, UserDatabaseTable userDatabaseTable,
    TrialDatabaseTable trialDatabaseTable, BundleDatabaseTable bundleDatabaseTable,
    BundlePresetRepository bundlePresetRepository,
    OperationDatabaseTable operationDatabaseTable,
    WorkflowThrottleDatabaseTable workflowThrottleDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.trialDatabaseTable = trialDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.bundlePresetRepository = bundlePresetRepository;
    this.operationDatabaseTable = operationDatabaseTable;
    this.workflowThrottleDatabaseTable = workflowThrottleDatabaseTable;
  }

  @RequestMapping(path = "/trial/use/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> useTrial(
    HttpServletRequest request
  ) {
    return findUser(request)
      .thenCompose(user -> trialDatabaseTable.trialExists(user.email())
        .thenCompose(trialExists -> bundleDatabaseTable.bundleExists(user.id())
          .thenApply(bundleExists -> useTrial(user, trialExists, trialExists))));
  }

  private Map<String, Object> useTrial(
    User user, boolean trialExists, boolean bundleExists
  ) {
    if (trialExists) {
      return Map.of("success", false, "error", 1000);
    }
    if (bundleExists) {
      return Map.of("success", false, "error", 1001);
    }
    trialDatabaseTable.insertTrial(user.email());
    bundleDatabaseTable.insertBundle(Bundle.of(user.id(),
      bundlePresetRepository.findPreset(BundleType.TRIAL).get(),
      BundleRuntime.WEEKLY));
    operationDatabaseTable.operationsExists(user.id())
      .thenCompose(exists -> !exists ?
        operationDatabaseTable.insertOperations(user.id()) :
        operationDatabaseTable.resetExpiration(user.id()));
    workflowThrottleDatabaseTable.throttleExists(user.id())
      .thenCompose(exists -> !exists ?
        workflowThrottleDatabaseTable.insertThrottle(user.id()) :
        workflowThrottleDatabaseTable.setThrottle(user.id(), 0, 0));
    return Map.of("success", true);
  }
}
