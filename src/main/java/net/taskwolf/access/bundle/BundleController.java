package net.taskwolf.access.bundle;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.bundle.Bundle;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class BundleController extends TaskwolfRestController {
  private final BundleDatabaseTable bundleDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;

  private BundleController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
  }

  @RequestMapping(path = "/bundle/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> fundBundle(
    HttpServletRequest request
  ) {
    return findUser(request)
      .thenCompose(user -> userTargetDatabaseTable.findTargetSecured(user.id())
        .thenCompose(target -> bundleDatabaseTable.findBundle(target)
          .thenApply(this::assemblyBundleInformation)));
  }

  private Map<String, Object> assemblyBundleInformation(Bundle bundle) {
    var information = Maps.<String, Object>newHashMap();
    information.put("type", bundle.type().toString());
    information.put("expiration", bundle.expiration());
    information.put("workflowAccess", bundle.workflowAccess());
    information.put("workflowNumberLimit", bundle.workflowNumberLimit());
    information.put("workflowExecutionLimit", bundle.workflowExecutionLimit());
    information.put("workflowTemplateAccess", bundle.workflowTemplateAccess());
    information.put("databaseAccess", bundle.databaseAccess());
    information.put("databaseNumberLimit", bundle.databaseNumberLimit());
    information.put("databaseDataLimit", bundle.databaseDataLimit());
    information.put("webhookAccess", bundle.webhookAccess());
    information.put("webhookNumberLimit", bundle.webhookNumberLimit());
    information.put("organizationAccess", bundle.organizationAccess());
    information.put("organizationMemberLimit", bundle.organizationMemberLimit());
    information.put("deviceAccess", bundle.deviceAccess());
    information.put("accountsAccess", bundle.accountsAccess());
    information.put("accountsNumberLimit", bundle.accountsNumberLimit());
    return information;
  }
}
