package net.taskwolf.access.statistics;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.iterator.AsyncListIterator;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.WorkflowEntry;
import net.taskwolf.core.workflow.WorkflowExecutionDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
public final class StatisticsController extends TaskwolfRestController {
  private final CoreModule coreModule;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final WorkflowExecutionDatabaseTable workflowExecutionDatabaseTable;
  private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy");

  private StatisticsController(
    Key secretKey, UserDatabaseTable userDatabaseTable, CoreModule coreModule,
    OrganizationDatabaseTable organizationDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable,
    WorkflowExecutionDatabaseTable workflowExecutionDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.coreModule = coreModule;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.workflowExecutionDatabaseTable = workflowExecutionDatabaseTable;
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
          (int) existingAccounts.stream().filter(exists -> exists).count(),
          modules.size(), response));
  }

  private void findWorkflowsData(
    UUID targetId, int organizationMembers, int maxOrganizationMembers,
    int linkedAccounts, int maxLinkedAccounts,
    CompletableFuture<Map<String, Object>> response
  ) {
    workflowDatabaseTable.findWorkflowsOfOwner(targetId).thenAccept(workflows ->
      findModuleUsageData(workflows.size(), MAX_WORKFLOWS, organizationMembers,
        maxOrganizationMembers, linkedAccounts, maxLinkedAccounts, workflows, response));
  }

  private void findModuleUsageData(
    int workflows, int maxWorkflows, int organizationMembers,
    int maxOrganizationMembers, int linkedAccounts, int maxLinkedAccounts,
    List<WorkflowEntry> workflowEntries, CompletableFuture<Map<String, Object>> response
  ) {
    findStaffModuleUsages(workflowEntries).thenAccept(staffModuleUsages ->
      findWorkflowTimeDependentData(workflows, maxWorkflows, organizationMembers,
        maxOrganizationMembers, linkedAccounts, maxLinkedAccounts,
        findModuleUsage(workflowEntries), staffModuleUsages, workflowEntries, response));
  }

  private CompletableFuture<Map<String, Object>> findStaffModuleUsages(
    List<WorkflowEntry> workflows
  ) {
    var moduleUsages = assignModuleUsagesToCreators(workflows);
    var staffs = Lists.newArrayList(moduleUsages.keySet());
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(staffs, staff -> userDatabaseTable().findUser(staff)
        .thenApply(user -> new AbstractMap.SimpleEntry<>(staff, user.name())),
      staffs.size(), staffNames -> futureResponse.complete(
        assemblyStaffModuleUsages(staffNames, moduleUsages)));
    return futureResponse;
  }

  private Map<UUID, Map<String, Long>> assignModuleUsagesToCreators(
    List<WorkflowEntry> workflows
  ) {
    var creatorWorkflows = ArrayListMultimap.<UUID, WorkflowEntry>create();
    for (var workflow : workflows) {
      creatorWorkflows.put(workflow.creatorId(), workflow);
    }
    var moduleUsages = Maps.<UUID, Map<String, Long>>newHashMap();
    for (var staff : creatorWorkflows.keySet()) {
      moduleUsages.put(staff, findModuleUsage(creatorWorkflows.get(staff)));
    }
    return moduleUsages;
  }

  private Map<String, Object> assemblyStaffModuleUsages(
    List<AbstractMap.SimpleEntry<UUID, String>> staffNames,
    Map<UUID, Map<String, Long>> staffModuleUsage
  ) {
    var moduleUsages = Maps.<String, Object>newHashMap();
    for (var entry : staffNames) {
      moduleUsages.put(entry.getValue(), staffModuleUsage.get(entry.getKey()));
    }
    return moduleUsages;
  }

  private Map<String, Long> findModuleUsage(List<WorkflowEntry> workflows) {
    var modules = Lists.<String>newArrayList();
    for (var workflow : workflows) {
      modules.addAll(workflow.modules());
    }
    var moduleUsage = modules.stream().collect(Collectors.groupingBy(Function.identity(),
      Collectors.counting()));
    var result = Maps.<String, Long>newHashMapWithExpectedSize(moduleUsage.size());
    for (var entry : moduleUsage.entrySet()) {
      result.put(coreModule.findModuleInformation(entry.getKey()).get().name(),
        entry.getValue());
    }
    return result;
  }

  private void findWorkflowTimeDependentData(
    int workflows, int maxWorkflows, int organizationMembers,
    int maxOrganizationMembers, int linkedAccounts, int maxLinkedAccounts,
    Map<String, Long> moduleUsage, Map<String, Object> staffModuleUsages,
    List<WorkflowEntry> workflowEntries, CompletableFuture<Map<String, Object>> response
  ) {
    AsyncListIterator.execute(workflowEntries, workflow ->
        workflowExecutionDatabaseTable.findWorkflowExecutions(workflow.id()),
      workflowEntries.size(), executionDates -> response.complete(assemblyStatistics(
        workflows, maxWorkflows, organizationMembers, maxOrganizationMembers,
        linkedAccounts, maxLinkedAccounts, moduleUsage, staffModuleUsages,
        classifyWorkflowNumbers(workflowEntries),
        classifyWorkflowNumbersGrowth(workflowEntries),
        classifyWorkflowExecutions(executionDates))));
  }

  private Map<Integer, Integer> classifyWorkflowNumbers(List<WorkflowEntry> workflowEntries) {
    return classifyDatesInMonthlyOccurrence(workflowEntries.stream()
      .map(WorkflowEntry::created).toList(), true);
  }

  private Map<Integer, Integer> classifyWorkflowNumbersGrowth(List<WorkflowEntry> workflowEntries) {
    return classifyDatesInMonthlyOccurrence(workflowEntries.stream()
      .map(WorkflowEntry::created).toList(), false);
  }

  private Map<Integer, Integer> classifyWorkflowExecutions(List<Long> executionDates) {
    return classifyDatesInMonthlyOccurrence(executionDates.stream()
      .map(this::timeMillisecondsToDate).toList(), false);
  }

  private Map<Integer, Integer> classifyDatesInMonthlyOccurrence(
    List<String> dates, boolean totalValues
  ) {
    var currentYear = String.valueOf(Year.now().getValue());
    var monthlyNumber = Maps.<Integer, Integer>newHashMapWithExpectedSize(12);
    for (var i = 0; i < 12; i++) {
      monthlyNumber.put(i, 0);
    }
    var previousDates = 0;
    for (var date : dates) {
      if (!date.contains(currentYear)) {
        previousDates++;
        continue;
      }
      var entryMonth = Integer.valueOf(date.split("\\.")[1]) - 1;
      monthlyNumber.put(entryMonth, monthlyNumber.get(entryMonth) + 1);
    }
    return totalValues ? calculateTotalOccurrenceValues(monthlyNumber, previousDates) :
      monthlyNumber;
  }

  private Map<Integer, Integer> calculateTotalOccurrenceValues(
    Map<Integer, Integer> monthlyNumber, int previousDates
  ) {
    for (var i = 1; i < 12; i++) {
      monthlyNumber.put(i, monthlyNumber.get(i) + monthlyNumber.get(i - 1));
    }
    for (var i = 1; i < 12; i++) {
      monthlyNumber.put(i, monthlyNumber.get(i) + previousDates);
    }
    return monthlyNumber;
  }

  private String timeMillisecondsToDate(long milliseconds) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(milliseconds);
    return simpleDateFormat.format(calendar.getTime());
  }

  private Map<String, Object> assemblyStatistics(
    int workflows, int maxWorkflows, int organizationMembers,
    int maxOrganizationMembers, int linkedAccounts, int maxLinkedAccounts,
    Map<String, Long> moduleUsage, Map<String, Object> staffModuleUsages,
    Map<Integer, Integer> workflowNumberOccurrence,
    Map<Integer, Integer> workflowNumberGrowth,
    Map<Integer, Integer> workflowExecutionsOccurrence
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("workflows", workflows);
    information.put("maxWorkflows", maxWorkflows);
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
