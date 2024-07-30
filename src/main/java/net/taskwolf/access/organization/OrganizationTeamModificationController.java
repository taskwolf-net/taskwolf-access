package net.taskwolf.access.organization;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.bundle.Bundle;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.organization.team.Team;
import net.taskwolf.core.organization.team.TeamDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class OrganizationTeamModificationController extends OrganizationTeamController {
  private final BundleDatabaseTable bundleDatabaseTable;

  private OrganizationTeamModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    UserTargetDatabaseTable targetDatabaseTable,
    TeamDatabaseTable teamDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, organizationDatabaseTable,
      targetDatabaseTable, teamDatabaseTable);
    this.bundleDatabaseTable = bundleDatabaseTable;
  }

  @RequestMapping(path = "/organization/team/add/", method = RequestMethod.POST)
  public CompletableFuture<Void> addTeam(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var name = body.getString("name");
    var futureResponse = new CompletableFuture<Void>();
    performOrganizationOwnerOperation(findUserId(request),
      organization -> bundleDatabaseTable.findBundle(organization.id())
        .thenAccept(bundle ->
          teamDatabaseTable().findTeamsByOrganization(organization.id())
            .thenAccept(teams -> addTeam(organization, name, bundle,
              teams.size(), response)).thenAccept(futureResponse::complete)),
      () -> {});
    return futureResponse;
  }

  private void addTeam(
    Organization organization, String name, Bundle bundle, int teamNumber,
    HttpServletResponse response
  ) {
    if (bundle.organizationTeamLimit() > 0 &&
      teamNumber >= bundle.organizationTeamLimit()
    ) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    teamDatabaseTable().generateAvailableTeamId()
      .thenAccept(id -> teamDatabaseTable().insertTeam(id, organization.id(),
        name, teamNumber, Lists.newArrayList()));
  }

  @RequestMapping(path = "/organization/team/member/add/", method = RequestMethod.POST)
  public void addTeamMember(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var teamId = body.getUUID("team");
    var targetId = body.getUUID("target");
    performOrganizationTeamOperation(findUserId(request), teamId,
      team -> organizationDatabaseTable().findOrganization(team.organizationId())
        .thenAccept(organization -> addTeamMember(organization, team, targetId)),
      () -> {});
  }

  private void addTeamMember(
    Organization organization, Team team, UUID targetId
  ) {
    if (!organization.owner().equals(targetId) &&
      !organization.members().contains(targetId)
    ) {
      return;
    }
    if (team.members().contains(targetId)) {
      return;
    }
    teamDatabaseTable().addTeamMember(team, targetId);
  }

  @RequestMapping(path = "/organization/team/member/remove/", method = RequestMethod.POST)
  public void removeTeamMember(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var teamId = body.getUUID("team");
    var targetId = body.getUUID("target");
    performOrganizationTeamOperation(findUserId(request), teamId,
      team -> organizationDatabaseTable().findOrganization(team.organizationId())
        .thenAccept(organization -> removeTeamMember(organization, team, targetId)),
      () -> {});
  }

  private void removeTeamMember(
    Organization organization, Team team, UUID targetId
  ) {
    if (!organization.owner().equals(targetId) &&
      !organization.members().contains(targetId)
    ) {
      return;
    }
    if (!team.members().contains(targetId)) {
      return;
    }
    teamDatabaseTable().removeTeamMember(team, targetId);
  }

  @RequestMapping(path = "/organization/team/move/", method = RequestMethod.POST)
  public void moveTeam(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var teamId = body.getUUID("team");
    var upwards = body.getBoolean("upwards");
    performOrganizationTeamOperation(findUserId(request), teamId, team ->
      teamDatabaseTable().findTeamsByOrganization(team.organizationId())
        .thenAccept(teams -> moveTeam(team, teams, upwards)), () -> {});
  }

  private void moveTeam(Team team, List<Team> allTeams, boolean upwards) {
    if (upwards && team.sequence() + 1 >= allTeams.size()) {
      return;
    }
    if (!upwards && team.sequence() - 1 < 0) {
      return;
    }
    if (upwards) {
      var swapPartner = allTeams.stream()
        .filter(targetTeam -> targetTeam.sequence() == team.sequence() + 1)
        .findFirst().get();
      teamDatabaseTable().changeTeamSequence(swapPartner, swapPartner.sequence() - 1);
      teamDatabaseTable().changeTeamSequence(team, team.sequence() + 1);
    } else {
      var swapPartner = allTeams.stream()
        .filter(targetTeam -> targetTeam.sequence() == team.sequence() - 1)
        .findFirst().get();
      teamDatabaseTable().changeTeamSequence(swapPartner, swapPartner.sequence() + 1);
      teamDatabaseTable().changeTeamSequence(team, team.sequence() - 1);
    }
  }

  @RequestMapping(path = "/organization/team/rename/", method = RequestMethod.POST)
  public void renameTeam(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var teamId = body.getUUID("team");
    var name = body.getString("name");
    performOrganizationTeamOperation(findUserId(request), teamId,
      team -> teamDatabaseTable().renameTeam(team, name), () -> {});
  }

  @RequestMapping(path = "/organization/team/remove/", method = RequestMethod.POST)
  public void removeTeam(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var teamId = body.getUUID("team");
    performOrganizationTeamOperation(findUserId(request), teamId,
      this::removeTeam, () -> {});
  }

  private void removeTeam(Team team) {
    teamDatabaseTable().deleteTeam(team.id());
    teamDatabaseTable().findTeamsByOrganization(team.organizationId())
      .thenAccept(teams -> maintainTeamSequence(team, teams));
  }

  private void maintainTeamSequence(Team removedTeam, List<Team> allTeams) {
    for (var team : allTeams) {
      if (team.sequence() > removedTeam.sequence()) {
        teamDatabaseTable().changeTeamSequence(team, team.sequence() - 1);
      }
    }
  }
}
