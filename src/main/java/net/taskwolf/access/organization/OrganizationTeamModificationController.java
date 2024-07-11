package net.taskwolf.access.organization;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.organization.OrganizationTeam;
import net.taskwolf.core.organization.OrganizationTeamDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.UUID;

@RestController
public final class OrganizationTeamModificationController extends OrganizationTeamController {
  private OrganizationTeamModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    UserTargetDatabaseTable targetDatabaseTable,
    OrganizationTeamDatabaseTable teamDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, organizationDatabaseTable,
      targetDatabaseTable, teamDatabaseTable);
  }

  @RequestMapping(path = "/organization/team/add/", method = RequestMethod.POST)
  public void addTeam(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var name = body.getString("name");
    performOrganizationOwnerOperation(findUserId(request), organization ->
      addTeam(organization, name), () -> {});
  }

  private void addTeam(Organization organization, String name) {
    teamDatabaseTable().generateAvailableTeamId()
      .thenAccept(id -> teamDatabaseTable().insertTeam(id, organization.id(),
        name, Lists.newArrayList()));
  }

  @RequestMapping(path = "/organization/team/member/add/", method = RequestMethod.POST)
  public void addTeamMember(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var teamId = body.getUUID("team");
    var targetId = body.getUUID("targetId");
    performOrganizationTeamOperation(findUserId(request), teamId,
      team -> organizationDatabaseTable().findOrganization(team.organizationId())
        .thenAccept(organization -> addTeamMember(organization, team, targetId)),
      () -> {});
  }

  private void addTeamMember(
    Organization organization, OrganizationTeam team, UUID targetId
  ) {
    if (!organization.members().contains(targetId)) {
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
    var targetId = body.getUUID("targetId");
    performOrganizationTeamOperation(findUserId(request), teamId,
      team -> organizationDatabaseTable().findOrganization(team.organizationId())
        .thenAccept(organization -> removeTeamMember(organization, team, targetId)),
      () -> {});
  }

  private void removeTeamMember(
    Organization organization, OrganizationTeam team, UUID targetId
  ) {
    if (!organization.members().contains(targetId)) {
      return;
    }
    if (!team.members().contains(targetId)) {
      return;
    }
    teamDatabaseTable().removeTeamMember(team, targetId);
  }

  @RequestMapping(path = "/organization/team/remove/", method = RequestMethod.POST)
  public void removeTeam(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var teamId = body.getUUID("team");
    performOrganizationTeamOperation(findUserId(request), teamId,
      team -> teamDatabaseTable().deleteTeam(team.id()), () -> {});
  }
}
