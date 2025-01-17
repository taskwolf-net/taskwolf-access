package com.dulno.access.trial;

import com.dulno.access.verification.Verification;
import com.dulno.access.verification.VerificationLoginController;
import com.dulno.workflow.operation.OperationDatabaseTable;
import com.dulno.workflow.throttle.WorkflowThrottleDatabaseTable;
import jakarta.servlet.http.HttpServletRequest;
import com.dulno.core.access.DulnoHomeRestController;
import com.dulno.core.bundle.*;
import com.dulno.core.trial.TrialDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TrialController extends DulnoHomeRestController {
  private final Key productKey;
  private final Key refreshKey;
  private final TrialDatabaseTable trialDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final BundlePresetRepository bundlePresetRepository;
  private final OperationDatabaseTable operationDatabaseTable;
  private final WorkflowThrottleDatabaseTable workflowThrottleDatabaseTable;
  private final VerificationLoginController verificationLoginController;

  private TrialController(
    @Qualifier("homeKey") Key homeKey, @Qualifier("productKey") Key productKey,
    @Qualifier("refreshKey") Key refreshKey, UserDatabaseTable userDatabaseTable,
    TrialDatabaseTable trialDatabaseTable, BundleDatabaseTable bundleDatabaseTable,
    BundlePresetRepository bundlePresetRepository,
    OperationDatabaseTable operationDatabaseTable,
    WorkflowThrottleDatabaseTable workflowThrottleDatabaseTable,
    VerificationLoginController verificationLoginController
  ) {
    super(homeKey, userDatabaseTable);
    this.productKey = productKey;
    this.refreshKey = refreshKey;
    this.trialDatabaseTable = trialDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.bundlePresetRepository = bundlePresetRepository;
    this.operationDatabaseTable = operationDatabaseTable;
    this.workflowThrottleDatabaseTable = workflowThrottleDatabaseTable;
    this.verificationLoginController = verificationLoginController;
  }

  @RequestMapping(path = "/trial/use/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> useTrial(
    HttpServletRequest request
  ) {
    return findUser(request).thenCompose(user -> useTrial(request, user));
  }

  public CompletableFuture<Map<String, Object>> useTrial(
    HttpServletRequest request, User user
  ) {
    return trialDatabaseTable.trialExists(user.email())
      .thenCompose(trialExists -> bundleDatabaseTable.bundleExists(user.id())
        .thenCompose(bundleExists -> useTrial(request, user, trialExists,
          trialExists)));
  }

  private CompletableFuture<Map<String, Object>> useTrial(
    HttpServletRequest request, User user, boolean trialExists,
    boolean bundleExists
  ) {
    if (trialExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1000));
    }
    if (bundleExists) {
      return CompletableFuture.completedFuture(Map.of("success", false,
        "error", 1001));
    }
    return finishTrialUsage(request, user);
  }

  private CompletableFuture<Map<String, Object>> finishTrialUsage(
    HttpServletRequest request, User user
  ) {
    trialDatabaseTable.insertTrial(user.email());
    operationDatabaseTable.operationsExists(user.id())
      .thenCompose(exists -> !exists ?
        operationDatabaseTable.insertOperations(user.id()) :
        operationDatabaseTable.resetExpiration(user.id()));
    workflowThrottleDatabaseTable.throttleExists(user.id())
      .thenCompose(exists -> !exists ?
        workflowThrottleDatabaseTable.insertThrottle(user.id()) :
        workflowThrottleDatabaseTable.setThrottle(user.id(), 0, 0));
    return bundleDatabaseTable.insertBundle(Bundle.of(user.id(),
        bundlePresetRepository.findPreset(BundleType.TRIAL).get(),
        BundleRuntime.INFINITE))
      .thenCompose(value -> loginUser(request, user));
  }

  private CompletableFuture<Map<String, Object>> loginUser(
    HttpServletRequest request, User user
  ) {
    var verification = Verification.create(userDatabaseTable(), secretKey(),
      productKey, refreshKey, user.email(), "");
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    verificationLoginController.processAuthorizedLogin(request, verification,
      futureResponse);
    return futureResponse;
  }
}
