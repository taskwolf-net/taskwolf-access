package net.taskwolf.access.statistic;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.iterator.AsyncListIterator;
import net.taskwolf.core.module.ModuleLoader;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.WorkflowEntry;
import net.taskwolf.core.workflow.WorkflowExecutionDatabaseTable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
public final class StatisticsController extends TaskwolfRestController {
  private final ModuleLoader moduleLoader;
  private final CoreModule coreModule;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final WorkflowExecutionDatabaseTable workflowExecutionDatabaseTable;

  private StatisticsController(
    Key secretKey, UserDatabaseTable userDatabaseTable, ModuleLoader moduleLoader,
    CoreModule coreModule, OrganizationDatabaseTable organizationDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable,
    WorkflowExecutionDatabaseTable workflowExecutionDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.moduleLoader = moduleLoader;
    this.coreModule = coreModule;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.workflowExecutionDatabaseTable = workflowExecutionDatabaseTable;
  }

  //TODO: NO LONGER MAKE THESE VALUES STATIC, BUT DEPENDENT ON THE PACKAGE / PRODUCT BOOKED
  private static final int MAX_WORKFLOWS = 100;
  private static final int MAX_ORGANIZATION_MEMBERS = 5;

  @RequestMapping(path = "/statistics/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findStatistics(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> userTargetDatabaseTable
      .findTargetSecured(user.id()).thenAccept(target ->
        findOrganizationsData(user, target, futureResponse)));
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
      findLinkedAccountsData(user, targetId, 1, 1, response);
    }
    organizationDatabaseTable.findOrganization(targetId).thenAccept(organization ->
      findLinkedAccountsData(user, targetId, organization.members().size() + 1,
        MAX_ORGANIZATION_MEMBERS, response));
  }

  private void findLinkedAccountsData(
    User user, UUID targetId, int organizationMembers, int maxOrganizationMembers,
    CompletableFuture<Map<String, Object>> response
  ) {
    var modules = moduleLoader.allRegisteredModules().stream()
      .filter(module -> module.module().accountLink() != null)
      .filter(module -> !module.module().accountLink().registrationUrl(targetId, "").isEmpty()).toList();
    AsyncIterator.execute(modules.stream().map(module -> module.module().accountLink()).toList(),
      link -> link.accountExists(targetId)).thenAccept(existingAccounts ->
        findWorkflowsData(user, targetId, organizationMembers, maxOrganizationMembers,
          (int) existingAccounts.stream().filter(exists -> exists).count(),
          modules.size(), response));
  }

  private void findWorkflowsData(
    User user, UUID targetId, int organizationMembers, int maxOrganizationMembers,
    int linkedAccounts, int maxLinkedAccounts,
    CompletableFuture<Map<String, Object>> response
  ) {
    workflowDatabaseTable.findWorkflowsOfOwner(targetId).thenAccept(workflows ->
      findModuleUsageData(user, workflows.size(), MAX_WORKFLOWS, (int) workflows.stream()
          .filter(workflow -> workflow.state().isFailing()).count(), organizationMembers,
        maxOrganizationMembers, linkedAccounts, maxLinkedAccounts, workflows, response));
  }

  private void findModuleUsageData(
    User user, int workflows, int maxWorkflows, int failingWorkflows,
    int organizationMembers, int maxOrganizationMembers, int linkedAccounts,
    int maxLinkedAccounts, List<WorkflowEntry> workflowEntries,
    CompletableFuture<Map<String, Object>> response
  ) {
    findStaffModuleUsages(workflowEntries).thenAccept(staffModuleUsages ->
      findWorkflowTimeDependentData(workflows, maxWorkflows, failingWorkflows,
        organizationMembers, maxOrganizationMembers, linkedAccounts, maxLinkedAccounts,
        findModuleUsage(user, workflowEntries), staffModuleUsages, workflowEntries,
        response));
  }

  private CompletableFuture<Map<String, Object>> findStaffModuleUsages(
    List<WorkflowEntry> workflows
  ) {
    var moduleUsages = assignModuleUsagesToCreators(workflows);
    var staffs = Lists.newArrayList(moduleUsages.keySet());
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(staffs, staff -> userDatabaseTable().findUserIfExists(staff))
      .thenAccept(staffUsers -> futureResponse.complete(
        assemblyStaffModuleUsages(staffUsers.stream().filter(staff ->
          !staff.email().equalsIgnoreCase("Unknown")).toList(), moduleUsages)));
    return futureResponse;
  }

  private Map<UUID, Integer> assignModuleUsagesToCreators(
    List<WorkflowEntry> workflows
  ) {
    var creatorWorkflows = ArrayListMultimap.<UUID, WorkflowEntry>create();
    for (var workflow : workflows) {
      creatorWorkflows.put(workflow.creatorId(), workflow);
    }
    var moduleUsages = Maps.<UUID, Integer>newHashMap();
    for (var staff : creatorWorkflows.keySet()) {
      moduleUsages.put(staff, creatorWorkflows.get(staff).size());
    }
    return moduleUsages;
  }

  private Map<String, Object> assemblyStaffModuleUsages(
    List<User> staffs, Map<UUID, Integer> staffModuleUsage
  ) {
    var moduleUsages = Maps.<String, Object>newHashMap();
    for (var staff : staffs) {
      moduleUsages.put(staff.name(), staffModuleUsage.get(staff.id()));
    }
    return moduleUsages;
  }

  private Map<String, Long> findModuleUsage(User user, List<WorkflowEntry> workflows) {
    var modules = Lists.<String>newArrayList();
    for (var workflow : workflows) {
      modules.addAll(workflow.modules());
    }
    var moduleUsage = modules.stream().collect(Collectors.groupingBy(Function.identity(),
      Collectors.counting()));
    var result = Maps.<String, Long>newHashMapWithExpectedSize(moduleUsage.size());
    for (var entry : moduleUsage.entrySet()) {
      result.put(coreModule.translate(user,
          coreModule.findModuleInformation(entry.getKey()).get().name()),
        entry.getValue());
    }
    return result;
  }

  private void findWorkflowTimeDependentData(
    int workflows, int maxWorkflows, int failingWorkflows, int organizationMembers,
    int maxOrganizationMembers, int linkedAccounts, int maxLinkedAccounts,
    Map<String, Long> moduleUsage, Map<String, Object> staffModuleUsages,
    List<WorkflowEntry> workflowEntries, CompletableFuture<Map<String, Object>> response
  ) {
    AsyncListIterator.execute(workflowEntries, workflow ->
        workflowExecutionDatabaseTable.findWorkflowExecutions(workflow.id()))
      .thenAccept(executionDates -> response.complete(assemblyStatistics(
        workflows, maxWorkflows, failingWorkflows, organizationMembers,
        maxOrganizationMembers, linkedAccounts, maxLinkedAccounts, moduleUsage,
        staffModuleUsages, classifyWorkflowNumbers(workflowEntries),
        classifyWorkflowNumbersGrowth(workflowEntries),
        assemblyTimeSeriesData(executionDates))));
  }

  private List<Map<String, Object>> classifyWorkflowNumbers(
    List<WorkflowEntry> workflowEntries
  ) {
    return assemblyTimeSeriesData(workflowEntries.stream()
      .map(WorkflowEntry::created).toList());
  }

  private List<Map<String, Object>> classifyWorkflowNumbersGrowth(
    List<WorkflowEntry> workflowEntries
  ) {
    return assemblyTimeSeriesData(workflowEntries.stream()
      .map(WorkflowEntry::created).toList());
  }

  private List<Map<String, Object>> assemblyTimeSeriesData(List<Long> times) {
    times = times.stream().sorted().toList();
    var result = Lists.<Map<String, Object>>newArrayList();
    for (var i = 0; i < times.size(); i++) {
      result.add(Map.of("timestamp", times.get(i), "value", i + 1));
    }
    return result;
  }

  private Map<String, Object> assemblyStatistics(
    int workflows, int maxWorkflows, int failingWorkflows, int organizationMembers,
    int maxOrganizationMembers, int linkedAccounts, int maxLinkedAccounts,
    Map<String, Long> moduleUsage, Map<String, Object> staffModuleUsages,
    List<Map<String, Object>> workflowNumberOccurrence,
    List<Map<String, Object>> workflowNumberGrowth,
    List<Map<String, Object>> workflowExecutionsOccurrence
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("workflows", workflows);
    information.put("maxWorkflows", maxWorkflows);
    information.put("failingWorkflows", failingWorkflows);
    information.put("organizationMembers", organizationMembers);
    information.put("maxOrganizationMembers", maxOrganizationMembers);
    information.put("linkedAccounts", linkedAccounts);
    information.put("maxLinkedAccounts", maxLinkedAccounts);
    information.put("moduleUsage", moduleUsage);
    information.put("staffModuleUsages", staffModuleUsages);
    information.put("workflowNumberOccurrence", workflowNumberOccurrence);
    information.put("workflowNumberGrowth", workflowNumberGrowth);
    information.put("workflowExecutionsOccurrence", workflowExecutionsOccurrence);
    return information;
  }
}
