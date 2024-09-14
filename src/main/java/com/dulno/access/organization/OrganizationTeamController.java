package com.dulno.access.organization;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import com.dulno.core.organization.Organization;
import com.dulno.core.organization.OrganizationDatabaseTable;
import com.dulno.core.organization.team.Team;
import com.dulno.core.organization.team.TeamDatabaseTable;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;

import java.security.Key;
import java.util.UUID;
import java.util.function.Consumer;

@Accessors(fluent = true)
public class OrganizationTeamController extends OrganizationController {
  @Getter(AccessLevel.PROTECTED)
  private final TeamDatabaseTable teamDatabaseTable;

  protected OrganizationTeamController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    UserTargetDatabaseTable targetDatabaseTable,
    TeamDatabaseTable teamDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, organizationDatabaseTable,
      targetDatabaseTable);
    this.teamDatabaseTable = teamDatabaseTable;
  }

  protected void performOrganizationTeamOperation(
    UUID userId, UUID teamId, Consumer<Team> operation,
    Runnable failResponse
  ) {
    performOrganizationOwnerOperation(userId,
      organization -> teamDatabaseTable.teamExists(teamId)
        .thenAccept(exists -> performOrganizationTeamOperation(organization,
          teamId, exists, operation, failResponse)),
      failResponse);
  }

  protected void performOrganizationTeamOperation(
    Organization organization, UUID teamId, boolean teamExists,
    Consumer<Team> operation, Runnable failResponse
  ) {
    if (!teamExists) {
      failResponse.run();
      return;
    }
    teamDatabaseTable.findTeam(teamId)
      .thenAccept(team -> performOrganizationTeamOperation(organization,
        team, operation, failResponse));
  }

  private void performOrganizationTeamOperation(
    Organization organization, Team team,
    Consumer<Team> operation, Runnable failResponse
  ) {
    if (!team.organizationId().equals(organization.id())) {
      failResponse.run();
      return;
    }
    operation.accept(team);
  }
}
