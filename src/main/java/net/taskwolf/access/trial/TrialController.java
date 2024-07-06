package net.taskwolf.access.trial;

import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.bundle.*;
import net.taskwolf.core.trial.TrialDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.workflow.operation.OperationDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TrialController extends TaskwolfRestController {
  private final TrialDatabaseTable trialDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final BundlePresetRepository bundlePresetRepository;
  private final OperationDatabaseTable operationDatabaseTable;

  private TrialController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    TrialDatabaseTable trialDatabaseTable, BundleDatabaseTable bundleDatabaseTable,
    BundlePresetRepository bundlePresetRepository,
    OperationDatabaseTable operationDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.trialDatabaseTable = trialDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.bundlePresetRepository = bundlePresetRepository;
    this.operationDatabaseTable = operationDatabaseTable;
  }

  @RequestMapping(path = "/trial/use/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> useTrial(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var email = body.getString("email");
    return userDatabaseTable().userExists(email).thenCompose(userExists ->
      trialDatabaseTable.trialExists(email).thenCompose(trialExists ->
        useTrial(email, userExists, trialExists)));
  }

  private CompletableFuture<Map<String, Object>> useTrial(
    String email, boolean userExists, boolean trialExists
  ) {
    if (!userExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    if (trialExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    return userDatabaseTable().findUser(email).thenCompose(user ->
      bundleDatabaseTable.bundleExists(user.id()).thenApply(bundleExists ->
        useTrial(user, bundleExists)));
  }

  private Map<String, Object> useTrial(
    User user, boolean bundleExists
  ) {
    if (bundleExists) {
      return Map.of("success", false, "error", 1002);
    }
    trialDatabaseTable.insertTrial(user.email());
    bundleDatabaseTable.insertBundle(Bundle.of(user.id(),
      bundlePresetRepository.findPreset(BundleType.TRIAL).get(),
      BundleRuntime.WEEKLY));
    operationDatabaseTable.insertOperations(user.id());
    return Map.of("success", true);
  }
}
