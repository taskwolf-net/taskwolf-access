package net.taskwolf.access.target;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.locale.Translation;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TargetController extends TaskwolfRestController {
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final BundleDatabaseTable bundleDatabaseTable;
  private final TeamTargetDatabaseTable teamTargetDatabaseTable;
  private final Translation translation;

  private TargetController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    Translation translation
  ) {
    super(secretKey, userDatabaseTable);
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.teamTargetDatabaseTable = teamTargetDatabaseTable;
    this.translation = translation;
  }

  @RequestMapping(path = "/targets/all/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findAllTargets(
    HttpServletRequest request
  ) {
    return findUser(request)
      .thenCompose(user -> userTargetDatabaseTable.findTargetSecured(user.id())
        .thenCompose(target -> collectTargetsInformation(user,
          user.organizations(), user.id(), target)));
  }

  private CompletableFuture<Map<String, Object>> collectTargetsInformation(
    User user, List<UUID> organizations, UUID applicantId, UUID currentTarget
  ) {
    return AsyncIterator.execute(organizations, this::gatherTargetInformation)
      .thenCompose(information -> bundleDatabaseTable.bundleExists(user.id())
        .thenApply(bundleExists -> finishTargetsInformation(user, information,
          applicantId, currentTarget, bundleExists)));
  }

  private CompletableFuture<Map<String, Object>> gatherTargetInformation(
    UUID organizationId
  ) {
    return organizationDatabaseTable.findOrganization(organizationId).thenApply(
      organization -> Map.of("id", organization.id(),
        "name", organization.name(), "type", "ORGANIZATION"));
  }

  private Map<String, Object> finishTargetsInformation(
    User user, List<Map<String, Object>> organizations, UUID applicantId,
    UUID currentTarget, boolean personalBundleExists
  ) {
    var targets = Lists.<Map<String, Object>>newArrayList();
    if (personalBundleExists) {
      targets.add(Map.of("id", applicantId, "name", translation.translate(user,
        "target.you"), "type", "PERSONAL"));
    }
    organizations.sort(Comparator.comparing(firstOrganization ->
      ((String) firstOrganization.get("name"))));
    targets.addAll(organizations);
    return Map.of("targets", targets, "currentTarget", currentTarget);
  }

  @RequestMapping(path = "/target/find/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findTarget(
    HttpServletRequest request
  ) {
    var userId = findUserId(request);
    return userTargetDatabaseTable.findTargetSecured(userId)
      .thenApply(target -> Map.of("target", target));
  }

  @RequestMapping(path = "/target/change/", method = RequestMethod.POST)
  public void changeTarget(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var target = body.getUUID("target");
    var userId = findUserId(request);
    checkTargetValidity(userId, target).thenAccept(accepted ->
      changeTarget(userId, target, accepted));
  }

  private void changeTarget(UUID userId, UUID target, boolean accepted) {
    if (!accepted) {
      return;
    }
    userTargetDatabaseTable.changeTarget(userId, target);
    teamTargetDatabaseTable.deleteTarget(userId);
  }

  private CompletableFuture<Boolean> checkTargetValidity(UUID userId, UUID target) {
    if (userId.equals(target)) {
      return bundleDatabaseTable.bundleExists(userId);
    }
    return organizationDatabaseTable.organizationExists(target).thenCompose(
      exists -> checkTargetOrganizationExistence(userId, target, exists));
  }

  private CompletableFuture<Boolean> checkTargetOrganizationExistence(
    UUID userId, UUID target, boolean organizationExists
  ) {
    if (!organizationExists) {
      return CompletableFuture.completedFuture(false);
    }
    return organizationDatabaseTable.findOrganization(target).thenApply(
      organization -> organization.owner().equals(userId) ||
        organization.members().contains(userId));
  }
}

