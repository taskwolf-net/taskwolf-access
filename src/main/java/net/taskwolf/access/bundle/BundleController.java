package net.taskwolf.access.bundle;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.bundle.*;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.organization.team.TeamDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.operation.OperationDatabaseTable;
import net.taskwolf.table.structure.TableDatabaseTable;
import net.taskwolf.table.structure.TableEntry;
import net.taskwolf.webhook.structure.WebhookDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public final class BundleController extends TaskwolfRestController {
  private final BundleDatabaseTable bundleDatabaseTable;
  private final BundlePresetRepository bundlePresetRepository;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final TeamDatabaseTable teamDatabaseTable;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final OperationDatabaseTable operationDatabaseTable;
  private final TableDatabaseTable tableDatabaseTable;
  private final WebhookDatabaseTable webhookDatabaseTable;

  private BundleController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable,
    BundlePresetRepository bundlePresetRepository,
    UserTargetDatabaseTable userTargetDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    TeamDatabaseTable teamDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable,
    OperationDatabaseTable operationDatabaseTable,
    TableDatabaseTable tableDatabaseTable,
    WebhookDatabaseTable webhookDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.bundlePresetRepository = bundlePresetRepository;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.teamDatabaseTable = teamDatabaseTable;
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.operationDatabaseTable = operationDatabaseTable;
    this.tableDatabaseTable = tableDatabaseTable;
    this.webhookDatabaseTable = webhookDatabaseTable;
  }

  @RequestMapping(path = "/bundle/preset/", method = RequestMethod.POST)
  public Map<String, Object> findBundlePreset(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var bundleType = BundleType.valueOf(body.getString("type"));
    var bundleClass = BundleClass.valueOf(body.getString("class"));
    var bundleRuntime = BundleRuntime.valueOf(body.getString("runtime"));
    if (bundleType.isTrial()) {
      return assemblyBundlePresetInformation(
        bundlePresetRepository.findPreset(BundleType.TRIAL).get(),
        BundleRuntime.WEEKLY);
    }
    if (bundleType.isEnterprise()) {
      return assemblyBundlePresetInformation(
        bundlePresetRepository.findPreset(BundleType.ENTERPRISE).get(),
        bundleRuntime);
    }
    return assemblyBundlePresetInformation(
      bundlePresetRepository.findPreset(bundleType, bundleClass).get(),
      bundleRuntime);
  }

  private Map<String, Object> assemblyBundlePresetInformation(
    BundlePreset preset, BundleRuntime runtime
  ) {
    var information = Maps.<String, Object>newHashMap();
    if (preset.bundleType().isTrial()) {
      information.put("price", 0);
    } else {
      information.put("price", preset.hasPrice() ? (runtime.isMonthly() ?
        preset.monthlyPrice() : preset.yearlyPrice()) : "NEGOTIABLE");
    }
    information.put("workflowAccess", preset.workflowAccess());
    information.put("workflowNumberLimit", preset.workflowNumberLimit());
    information.put("workflowOperationLimit", preset.hasWorkflowOperationLimit() ?
      preset.workflowOperationLimit() : "NEGOTIABLE");
    information.put("workflowTemplateAccess", preset.workflowTemplateAccess());
    information.put("databaseAccess", preset.databaseAccess());
    information.put("databaseNumberLimit", preset.databaseNumberLimit());
    information.put("databaseDataLimit", preset.hasDatabaseDataLimit() ?
      new DecimalFormat("#.#").format(preset.databaseDataLimit()) : "NEGOTIABLE");
    information.put("webhookAccess", preset.webhookAccess());
    information.put("webhookNumberLimit", preset.webhookNumberLimit());
    information.put("organizationAccess", preset.organizationAccess());
    information.put("organizationMemberLimit", preset.hasOrganizationLimits() ?
      preset.organizationMemberLimit() : "NEGOTIABLE");
    information.put("organizationTeamLimit", preset.hasOrganizationLimits() ?
      preset.organizationTeamLimit() : "NEGOTIABLE");
    information.put("deviceAccess", preset.deviceAccess());
    information.put("accountsAccess", preset.accountsAccess());
    information.put("accountsNumberLimit", preset.accountsNumberLimit());
    return information;
  }

  @RequestMapping(path = "/bundle/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findBundle(
    HttpServletRequest request
  ) {
    var userId = findUserId(request);
    return userTargetDatabaseTable.findTargetSecured(userId)
      .thenCompose(target -> checkUserPermission(userId, target)
        .thenCompose(hasPermission -> findBundle(target, hasPermission)));
  }

  private CompletableFuture<Map<String, Object>> findBundle(
    UUID targetId, boolean hasPermission
  ) {
    if (!hasPermission) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return bundleDatabaseTable.findBundle(targetId)
      .thenApply(this::assemblyBundleInformation);
  }

  private Map<String, Object> assemblyBundleInformation(Bundle bundle) {
    var information = Maps.<String, Object>newHashMap();
    information.put("type", bundle.bundleType().toString());
    information.put("class", bundle.bundleClass().toString());
    information.put("runtime", bundle.bundleRuntime().toString());
    information.put("price", bundle.price());
    information.put("expiration", bundle.expiration());
    information.put("workflowAccess", bundle.workflowAccess());
    information.put("workflowNumberLimit", bundle.workflowNumberLimit());
    information.put("workflowOperationLimit", bundle.workflowOperationLimit());
    information.put("workflowTemplateAccess", bundle.workflowTemplateAccess());
    information.put("databaseAccess", bundle.databaseAccess());
    information.put("databaseNumberLimit", bundle.databaseNumberLimit());
    information.put("databaseDataLimit", new DecimalFormat("#.#").format(
      bundle.databaseDataLimit()));
    information.put("webhookAccess", bundle.webhookAccess());
    information.put("webhookNumberLimit", bundle.webhookNumberLimit());
    information.put("organizationAccess", bundle.organizationAccess());
    information.put("organizationMemberLimit", bundle.organizationMemberLimit());
    information.put("organizationTeamLimit", bundle.organizationTeamLimit());
    information.put("deviceAccess", bundle.deviceAccess());
    information.put("accountsAccess", bundle.accountsAccess());
    information.put("accountsNumberLimit", bundle.accountsNumberLimit());
    return information;
  }

  @RequestMapping(path = "/bundle/usage/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findBundleUsage(
    HttpServletRequest request
  ) {
    var userId = findUserId(request);
    return userTargetDatabaseTable.findTargetSecured(findUserId(request))
      .thenCompose(target -> checkUserPermission(userId, target)
        .thenCompose(hasPermission -> findBundleUsage(target, hasPermission)));
  }

  private CompletableFuture<Map<String, Object>> findBundleUsage(
    UUID targetId, boolean hasPermission
  ) {
    if (!hasPermission) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return bundleDatabaseTable.findBundle(targetId)
      .thenCompose(bundle -> findWorkflowUsage(targetId)
        .thenCompose(workflowUsage -> findDatabaseUsage(targetId)
          .thenCompose(databaseUsage -> findWebhookUsage(targetId)
            .thenCompose(webhookUsage -> findOrganizationUsage(targetId, bundle)
              .thenApply(organizationUsage -> Stream.of(workflowUsage.entrySet(),
                  databaseUsage.entrySet(), webhookUsage.entrySet(),
                  organizationUsage.entrySet()).flatMap(Set::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))))));
  }

  private CompletableFuture<Map<String, Object>> findWorkflowUsage(UUID targetId) {
    return workflowDatabaseTable.findWorkflowsOfOwner(targetId).thenCompose(
      workflows -> operationDatabaseTable.findOperations(targetId).thenApply(
        operations -> Map.of("workflowNumberUsage", workflows.size(),
          "workflowOperationUsage", operations.operations())));
  }

  private CompletableFuture<Map<String, Object>> findDatabaseUsage(UUID targetId) {
    return tableDatabaseTable.findTablesOfOwner(targetId).thenApply(tables ->
      Map.of("databaseNumberUsage", tables.size(), "databaseDataUsage",
        new DecimalFormat("#.###").format(tables.stream()
          .mapToLong(TableEntry::size).sum() * Math.pow(10, -9))));
  }

  private CompletableFuture<Map<String, Object>> findWebhookUsage(UUID targetId) {
    return webhookDatabaseTable.findWebhooksByOwner(targetId).thenApply(
      webhooks -> Map.of("webhookNumberUsage", webhooks.size()));
  }

  private CompletableFuture<Map<String, Object>> findOrganizationUsage(
    UUID targetId, Bundle bundle
  ) {
    if (bundle.bundleType().isTrial() || bundle.bundleType().isProfessional()) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return organizationDatabaseTable.findOrganization(targetId).thenCompose(
      organization -> teamDatabaseTable.findTeamsByOrganization(organization.id())
        .thenApply(teams -> Map.of("organizationMemberUsage",
          organization.members().size(), "organizationTeamUsage", teams.size())));
  }

  private CompletableFuture<Boolean> checkUserPermission(
    UUID userId, UUID targetId
  ) {
    if (userId.equals(targetId)) {
      return CompletableFuture.completedFuture(true);
    }
    return organizationDatabaseTable.findOrganization(targetId)
      .thenApply(organization -> organization.owner().equals(userId));
  }
}
