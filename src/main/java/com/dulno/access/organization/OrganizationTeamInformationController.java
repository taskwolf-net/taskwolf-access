package com.dulno.access.organization;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.organization.Organization;
import com.dulno.core.organization.OrganizationDatabaseTable;
import com.dulno.core.organization.team.Team;
import com.dulno.core.organization.team.TeamDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
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
public final class OrganizationTeamInformationController extends OrganizationTeamController {
  private OrganizationTeamInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    UserTargetDatabaseTable targetDatabaseTable,
    TeamDatabaseTable teamDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, organizationDatabaseTable,
      targetDatabaseTable, teamDatabaseTable);
  }

  @RequestMapping(path = "/organization/teams/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findOrganizationTeams(
    HttpServletRequest request
  ) {
    var userId = findUserId(request);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performOrganizationMemberOperation(userId, organization ->
        gatherOrganizationTeamsInformation(organization, userId)
          .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherOrganizationTeamsInformation(
    Organization organization, UUID applicantId
  ) {
    return teamDatabaseTable().findTeamsByOrganization(organization.id())
      .thenCompose(teams -> findOrganizationMembers(organization)
        .thenApply(members -> assemblyTeamsInformation(organization, teams,
          members, applicantId)));
  }

  private CompletableFuture<List<User>> findOrganizationMembers(
    Organization organization
  ) {
    var memberIds = Lists.newArrayList(organization.members());
    memberIds.add(organization.owner());
    var futureResponse = new CompletableFuture<List<User>>();
    AsyncIterator.execute(memberIds, member -> userDatabaseTable().findUser(member))
      .thenAccept(futureResponse::complete);
    return futureResponse;
  }

  private Map<String, Object> assemblyTeamsInformation(
    Organization organization, List<Team> teams,
    List<User> members, UUID applicantId
  ) {
    var availableOrganizations = Lists.<Team>newArrayList();
    if (organization.owner().equals(applicantId)) {
      availableOrganizations.addAll(teams);
    } else {
      availableOrganizations.addAll(teams.stream().filter(team ->
        team.members().contains(applicantId)).toList());
    }
    availableOrganizations.sort(Comparator.comparing(Team::sequence));
    var teamsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var team : availableOrganizations) {
      teamsInformation.add(assemblyTeamInformation(team, members));
    }
    return Map.of("teams", teamsInformation);
  }

  private Map<String, Object> assemblyTeamInformation(
    Team team, List<User> members
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", team.id());
    information.put("name", team.name());
    var membersInformation = Lists.<Map<String, Object>>newArrayList();
    for (var member : team.members()) {
      var memberUser = members.stream().filter(target -> target.id().equals(member))
        .findFirst().get();
      var memberInformation = Maps.<String, Object>newHashMap();
      memberInformation.put("id", memberUser.id());
      memberInformation.put("name", memberUser.name());
      membersInformation.add(memberInformation);
    }
    information.put("members", membersInformation);
    return information;
  }
}
