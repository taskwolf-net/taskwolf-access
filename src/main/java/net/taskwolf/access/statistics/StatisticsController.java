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
    findUser(request).thenAccept(user -> findOrganizationsData(user, targetId,
      futureResponse));
    return futureResponse;
  }

  private void findOrganizationsData(
    User user, UUID targetId, CompletableFuture<Map<String, Object>> response
  ) {
    if (!user.id().equals(targetId) && !user.organizations().contains(targetId)) {
      response.complete(Maps.newHashMap());
      return;
    }
    if (user.id().equals(targetId)) {
      findLinkedAccountsData(targetId, 1, 1, response);
    }
    organizationDatabaseTable.findOrganization(targetId).thenAccept(organization ->
      findLinkedAccountsData(targetId, organization.members().size() + 1,
        MAX_ORGANIZATION_MEMBERS, response));
  }

  private void findLinkedAccountsData(
    UUID targetId, int organizationMembers, int maxOrganizationMembers,
    CompletableFuture<Map<String, Object>> response
  ) {
    var modules = coreModule.moduleLoader().allRegisteredModules().stream()
      .filter(module -> module.module().accountLink() != null)
      .filter(module -> !module.module().accountLink().registrationUrl("").isEmpty()).toList();
    AsyncIterator.execute(modules.stream().map(module -> module.module().accountLink()).toList(),
      link -> link.accountExists(targetId), modules.size(), existingAccounts ->
        findWorkflowsData(targetId, organizationMembers, maxOrganizationMembers,
          existingAccounts.size(), modules.size(), response));
  }

  private void findWorkflowsData(
    UUID targetId, int organizationMembers, int maxOrganizationMembers,
    int linkedAccounts, int maxLinkedAccounts,
    CompletableFuture<Map<String, Object>> response
  ) {
    workflowDatabaseTable.findWorkflowsOfOwner(targetId).thenAccept(workflows ->
      response.complete(assemblyStatistics(workflows.size(), MAX_WORKFLOWS,
        organizationMembers, maxOrganizationMembers, linkedAccounts, maxLinkedAccounts)));
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
