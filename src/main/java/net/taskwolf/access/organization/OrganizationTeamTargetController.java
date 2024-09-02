package net.taskwolf.access.organization;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.locale.Translation;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.organization.team.Team;
import net.taskwolf.core.organization.team.TeamDatabaseTable;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
public final class OrganizationTeamTargetController extends TaskwolfRestController {
  private final TeamTargetDatabaseTable teamTargetDatabaseTable;
  private final TeamDatabaseTable teamDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final Translation translation;

  private OrganizationTeamTargetController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    TeamDatabaseTable teamDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    Translation translation
  ) {
    super(secretKey, userDatabaseTable);
    this.teamTargetDatabaseTable = teamTargetDatabaseTable;
    this.teamDatabaseTable = teamDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.translation = translation;
  }

  @RequestMapping(path = "/organization/team/targets/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findTargets(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var organization = body.getUUID("organization");
    return findUser(request)
      .thenCompose(user -> organizationDatabaseTable.organizationExists(organization)
        .thenCompose(exists -> checkOrganizationExistence(user, organization, exists)));
  }

  @RequestMapping(path = "/organization/team/targets/selected/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findSelectedTargets(
    HttpServletRequest request
  ) {
    return findUser(request)
      .thenCompose(user -> userTargetDatabaseTable.findTargetSecured(user.id())
        .thenCompose(target -> organizationDatabaseTable.organizationExists(target)
          .thenCompose(exists -> checkOrganizationExistence(user, target, exists))));
  }

  private CompletableFuture<Map<String, Object>> checkOrganizationExistence(
    User user, UUID userTarget, boolean organizationExists
  ) {
    if (!organizationExists) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return organizationDatabaseTable.findOrganization(userTarget)
      .thenCompose(organization -> checkOrganizationPermission(user, organization));
  }

  private CompletableFuture<Map<String, Object>> checkOrganizationPermission(
    User user, Organization organization
  ) {
    if (!organization.owner().equals(user.id()) &&
      !organization.members().contains(user.id())
    ) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return collectTargetsInformation(user, organization);
  }

  private CompletableFuture<Map<String, Object>> collectTargetsInformation(
    User user, Organization organization
  ) {
    return teamDatabaseTable.findTeamsByOrganization(organization.id())
      .thenApply(teams -> teams.stream()
        .filter(team -> team.members().contains(user.id()))
        .sorted(Comparator.comparing(Team::sequence))
        .map(team -> Map.<String, Object>of("id", team.id(),
          "name", team.name(), "type", "TEAM"))
        .toList())
      .thenCompose(teams -> teamTargetDatabaseTable.findTargetSecured(user.id())
        .thenApply(currentTarget -> finishTargetsInformation(user, teams,
          currentTarget)));
  }

  private Map<String, Object> finishTargetsInformation(
    User user, List<Map<String, Object>> teams, Optional<UUID> currentTarget
  ) {
    var targets = Lists.<Map<String, Object>>newArrayList();
    targets.add(Map.of("id", "", "name", translation.translate(user,
      "organization.team.target.global"), "type", "GLOBAL"));
    targets.addAll(teams);
    return Map.of("targets", targets, "currentTarget", currentTarget.isEmpty() ?
      "" : currentTarget.get());
  }

  @RequestMapping(path = "/organization/team/target/change/", method = RequestMethod.POST)
  public void changeTarget(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var isGlobal = body.getBoolean("isGlobal");
    var userId = findUserId(request);
    if (isGlobal) {
      teamTargetDatabaseTable.deleteTarget(userId);
      return;
    }
    var target = body.getUUID("target");
    checkTargetValidity(userId, target).thenAccept(accepted ->
      changeTarget(userId, target, accepted));
  }

  private void changeTarget(UUID userId, UUID target, boolean accepted) {
    if (!accepted) {
      return;
    }
    teamTargetDatabaseTable.changeTarget(userId, target);
  }

  private CompletableFuture<Boolean> checkTargetValidity(UUID userId, UUID target) {
    return teamDatabaseTable.teamExists(target).thenCompose(
      exists -> checkTargetTeamExistence(userId, target, exists));
  }

  private CompletableFuture<Boolean> checkTargetTeamExistence(
    UUID userId, UUID target, boolean teamExists
  ) {
    if (!teamExists) {
      return CompletableFuture.completedFuture(false);
    }
    return teamDatabaseTable.findTeam(target).thenApply(
      team -> team.members().contains(userId));
  }
}