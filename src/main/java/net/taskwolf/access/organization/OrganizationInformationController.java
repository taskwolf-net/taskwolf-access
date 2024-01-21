package net.taskwolf.access.organization;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.organization.Organization;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.user.ProfilePictureDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class OrganizationInformationController extends TaskwolfRestController {
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final ProfilePictureDatabaseTable profilePictureDatabaseTable;

  private OrganizationInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
     ProfilePictureDatabaseTable profilePictureDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.profilePictureDatabaseTable = profilePictureDatabaseTable;
  }

  @RequestMapping(path = "/organization/selected/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findSelectedOrganization(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var id = UUID.fromString((String) input.get("id"));
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> gatherOrganizationInformation(
      id, user.id()).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  @RequestMapping(path = "/organizations/all/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findAllOrganizations(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> collectOrganizationsInformation(
      user.organizations(), user.id()).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> collectOrganizationsInformation(
    List<UUID> organizations, UUID applicantId
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(organizations, organization ->
        gatherOrganizationInformation(organization, applicantId),
      organizations.size(), information -> futureResponse.complete(
        Map.of("organizations", information)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherOrganizationInformation(
    UUID organizationId, UUID applicantId
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    organizationDatabaseTable.findOrganization(organizationId).thenAccept(organization ->
      userDatabaseTable().findUser(organization.owner()).thenAccept(owner ->
        findOrganizationMembers(organization.members()).thenAccept(members ->
          profilePictureDatabaseTable.findProfilePicture(owner.id()).thenAccept(
            ownerPicture -> findProfilePictures(organization.members()).thenAccept(
              pictures -> futureResponse.complete(assemblyOrganizationInformation(
                organization, owner, ownerPicture, members, pictures, applicantId)))))));
    return futureResponse;
  }

  private CompletableFuture<List<User>> findOrganizationMembers(
    List<UUID> memberIds
  ) {
    var futureResponse = new CompletableFuture<List<User>>();
    AsyncIterator.execute(memberIds, member -> userDatabaseTable().findUser(member),
      memberIds.size(), futureResponse::complete);
    return futureResponse;
  }

  private CompletableFuture<List<Map.Entry<UUID, String>>> findProfilePictures(
    List<UUID> memberIds
  ) {
    var futureResponse = new CompletableFuture<List<Map.Entry<UUID, String>>>();
    AsyncIterator.execute(memberIds, member -> profilePictureDatabaseTable
        .findProfilePicture(member).thenApply(picture ->
          new AbstractMap.SimpleEntry<>(member, picture)),
      memberIds.size(), futureResponse::complete);
    return futureResponse;
  }

  private Map<String, Object> assemblyOrganizationInformation(
    Organization organization, User owner, String ownerProfilePicture,
    List<User> members, List<Map.Entry<UUID, String>> memberProfilePictures,
    UUID applicantId
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", organization.id());
    information.put("name", organization.name());
    information.put("owner", owner.name());
    information.put("ownerProfilePicture", ownerProfilePicture);
    information.put("isOwner", applicantId.equals(owner.id()));
    var membersInformation = Lists.<Map<String, Object>>newArrayList();
    for (var member : members) {
      var memberInformation = Maps.<String, Object>newHashMap();
      memberInformation.put("id", member.id());
      memberInformation.put("name", member.name());
      memberInformation.put("profilePicture", memberProfilePictures.stream().filter(
        entry -> entry.getKey().equals(member.id())).findFirst().get().getValue());
      membersInformation.add(memberInformation);
    }
    information.put("members", membersInformation);
    return information;
  }

  @RequestMapping(path = "/organization/link/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findLink(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var organizationId = UUID.fromString((String) input.get("organization"));
    findUser(request).thenAccept(user ->
      organizationDatabaseTable.organizationExists(organizationId).thenAccept(
        exists -> findLink(user, organizationId, exists)
          .thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findLink(
    User user, UUID organizationId, boolean organizationExists
  ) {
    if (!organizationExists) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    if (!user.organizations().contains(organizationId)) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    organizationDatabaseTable.findOrganization(organizationId).thenAccept(
      organization -> futureResponse.complete(findLink(user, organization)));
    return futureResponse;
  }

  private static final String LINK_FORMAT = "https://taskwolf.net/organization/join/%s/%s/";

  private Map<String, Object> findLink(User user, Organization organization) {
    if (!organization.owner().equals(user.id())) {
      return Maps.newHashMap();
    }
    return Map.of("link", String.format(LINK_FORMAT, organization.id(),
      organization.invitationToken()));
  }
}
