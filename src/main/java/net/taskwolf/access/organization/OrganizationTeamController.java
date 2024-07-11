package net.taskwolf.access.organization;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.organization.OrganizationTeam;
import net.taskwolf.core.organization.OrganizationTeamDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;

import java.security.Key;
import java.util.UUID;
import java.util.function.Consumer;

@Accessors(fluent = true)
public class OrganizationTeamController extends OrganizationController {
  @Getter(AccessLevel.PROTECTED)
  private final OrganizationTeamDatabaseTable teamDatabaseTable;

  protected OrganizationTeamController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    UserTargetDatabaseTable targetDatabaseTable,
    OrganizationTeamDatabaseTable teamDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, organizationDatabaseTable,
      targetDatabaseTable);
    this.teamDatabaseTable = teamDatabaseTable;
  }

  protected void performOrganizationTeamOperation(
    UUID userId, UUID teamId, Consumer<OrganizationTeam> operation,
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
    Consumer<OrganizationTeam> operation, Runnable failResponse
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
    Organization organization, OrganizationTeam team,
    Consumer<OrganizationTeam> operation, Runnable failResponse
  ) {
    if (!team.organizationId().equals(organization.id())) {
      failResponse.run();
      return;
    }
    operation.accept(team);
  }
}
