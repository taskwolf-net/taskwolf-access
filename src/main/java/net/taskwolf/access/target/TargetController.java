package net.taskwolf.access.target;

import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TargetController extends TaskwolfRestController {
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final OrganizationDatabaseTable organizationDatabaseTable;

  private TargetController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.organizationDatabaseTable = organizationDatabaseTable;
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
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var target = UUID.fromString((String) input.get("target"));
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

