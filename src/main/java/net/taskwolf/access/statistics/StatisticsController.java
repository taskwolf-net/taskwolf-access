package net.taskwolf.access.statistics;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StatisticsController extends TaskwolfRestController {
  private final CoreModule coreModule;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final WorkflowDatabaseTable workflowDatabaseTable;

  private StatisticsController(
    Key secretKey, UserDatabaseTable userDatabaseTable, CoreModule coreModule,
    OrganizationDatabaseTable organizationDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.coreModule = coreModule;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.workflowDatabaseTable = workflowDatabaseTable;
  }

  //TODO: NO LONGER MAKE THESE VALUES STATIC, BUT DEPENDENT ON THE PACKAGE / PRODUCT BOOKED
  private static final int MAX_WORKFLOWS = 100;
  private static final int MAX_ORGANIZATION_MEMBERS = 5;

  @RequestMapping(path = "/statistics/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findStatistics(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var targetId = UUID.fromString((String) input.get("target"));
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> findStatistics(user, targetId)
      .thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findStatistics(User user, UUID targetId) {
    if (!user.id().equals(targetId) && !user.organizations().contains(targetId)) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    if (user.id().equals(targetId)) {
      return findStatistics(user, targetId, 1, 1);
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    organizationDatabaseTable.findOrganization(targetId).thenAccept(organization ->
      findStatistics(user, targetId, organization.members().size() + 1,
        MAX_ORGANIZATION_MEMBERS).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findStatistics(
    User user, UUID targetId, int organizationMembers, int maxOrganizationMembers
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var modules = coreModule.moduleLoader().allRegisteredModules().stream()
      .filter(module -> module.module().accountLink() != null)
      .filter(module -> !module.module().accountLink().registrationUrl("").isEmpty()).toList();
    AsyncIterator.execute(modules.stream().map(module -> module.module().accountLink()).toList(),
      link -> link.accountExists(targetId), modules.size(), existingAccounts ->
        workflowDatabaseTable.findWorkflowsOfOwner(targetId).thenAccept(workflows ->
          futureResponse.complete(assemblyStatistics(workflows.size(), MAX_WORKFLOWS,
            organizationMembers, maxOrganizationMembers, existingAccounts.size(), modules.size()))));
    return futureResponse;
  }

  private Map<String, Object> assemblyStatistics(
    int workflows, int maxWorkflows, int organizationMembers,
    int maxOrganizationMembers, int linkedAccounts, int maxLinkedAccounts
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("workflows", workflows);
    information.put("maxWorkflows", maxWorkflows);
    information.put("organizationMembers", organizationMembers);
    information.put("maxOrganizationMembers", maxOrganizationMembers);
    information.put("linkedAccounts", linkedAccounts);
    information.put("maxLinkedAccounts", maxLinkedAccounts);
    return information;
  }
}
