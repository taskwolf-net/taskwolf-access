package net.taskwolf.access.target;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TargetController extends TaskwolfRestController {
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final CoreModule coreModule;

  private TargetController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable, CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/targets/all/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findAllTargets(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user ->
      userTargetDatabaseTable.findTargetSecured(user.id()).thenAccept(target ->
        collectTargetsInformation(user, user.organizations(), user.id(), target)
          .thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> collectTargetsInformation(
    User user, List<UUID> organizations, UUID applicantId, UUID currentTarget
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(organizations, this::gatherTargetInformation,
      organizations.size(), information -> futureResponse.complete(
        finishTargetsInformation(user, information, applicantId, currentTarget)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherTargetInformation(
    UUID organizationId
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    organizationDatabaseTable.findOrganization(organizationId).thenAccept(
      organization -> futureResponse.complete(Map.of("id", organization.id(),
        "name", organization.name())));
    return futureResponse;
  }

  private Map<String, Object> finishTargetsInformation(
    User user, List<Map<String, Object>> organizations, UUID applicantId,
    UUID currentTarget
  ) {
    var targets = Lists.<Map<String, Object>>newArrayList();
    targets.add(Map.of("id", applicantId, "name", coreModule.translate(user,
      "target.you")));
    targets.addAll(organizations);
    return Map.of("targets", targets, "currentTarget", currentTarget);
  }

  @RequestMapping(path = "/target/find/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findTarget(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var userId = findUserId(request);
    userTargetDatabaseTable.findTargetSecured(userId).thenAccept(target ->
      futureResponse.complete(Map.of("target", target)));
    return futureResponse;
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
      userTargetDatabaseTable.changeTarget(userId, target));
  }

  private CompletableFuture<Boolean> checkTargetValidity(UUID userId, UUID target) {
    if (userId.equals(target)) {
      return CompletableFuture.completedFuture(false);
    }
    var futureResponse = new CompletableFuture<Boolean>();
    organizationDatabaseTable.organizationExists(target).thenAccept(exists ->
      checkTargetOrganizationExistence(userId, target, exists)
        .thenAccept(futureResponse::complete));
    return futureResponse;
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

