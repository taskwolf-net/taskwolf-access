package com.dulno.access.target;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.locale.Translation;
import com.dulno.core.organization.OrganizationDatabaseTable;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TargetController extends DulnoRestController {
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
        .thenCompose(target -> filterUsableTargets(user.organizations())
          .thenCompose(organizations -> collectTargetsInformation(user,
            organizations, user.id(), target))));
  }

  private CompletableFuture<List<UUID>> filterUsableTargets(
    List<UUID> organizations
  ) {
    return AsyncIterator.execute(organizations,
        organization -> checkTargetUsability(organization)
          .thenApply(usable -> new AbstractMap.SimpleEntry<>(organization, usable)))
      .thenApply(result -> result.stream().filter(AbstractMap.SimpleEntry::getValue)
        .map(AbstractMap.SimpleEntry::getKey).toList());
  }

  private CompletableFuture<Map<String, Object>> collectTargetsInformation(
    User user, List<UUID> organizations, UUID applicantId, UUID currentTarget
  ) {
    return AsyncIterator.execute(organizations, this::gatherTargetInformation)
      .thenCompose(information -> checkTargetUsability(user.id())
        .thenApply(personalBundleUsable -> finishTargetsInformation(user,
          information, applicantId, personalBundleUsable, currentTarget)));
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
    boolean personalBundleUsable, UUID currentTarget
  ) {
    var targets = Lists.<Map<String, Object>>newArrayList();
    if (personalBundleUsable) {
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
    var body = DulnoRequestBody.of(payload, response);
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
    return checkTargetUsability(target)
      .thenCompose(usable -> checkTargetValidity(userId, target, usable));
  }

  private CompletableFuture<Boolean> checkTargetValidity(
    UUID userId, UUID target, boolean isUsable
  ) {
    if (!isUsable) {
      return CompletableFuture.completedFuture(false);
    }
    if (userId.equals(target)) {
      return CompletableFuture.completedFuture(true);
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

  private CompletableFuture<Boolean> checkTargetUsability(UUID target) {
    return bundleDatabaseTable.bundleExists(target)
      .thenCompose(exists -> !exists ? CompletableFuture.completedFuture(false) :
        bundleDatabaseTable.findBundle(target).thenApply(bundle ->
          bundle.expiration() > System.currentTimeMillis()));
  }
}

