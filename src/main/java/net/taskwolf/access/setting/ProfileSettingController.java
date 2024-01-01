package net.taskwolf.access.setting;

import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.user.ProfilePictureDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class ProfileSettingController extends TaskwolfRestController {
  private final ProfilePictureDatabaseTable profilePictureDatabaseTable;

  private ProfileSettingController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    ProfilePictureDatabaseTable profilePictureDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.profilePictureDatabaseTable = profilePictureDatabaseTable;
  }

  @RequestMapping(path = "/settings/profile/username/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> username(
    HttpServletRequest request
  ) {
    return findUser(request).thenApply(user -> Map.of("username", user.name()));
  }

  @RequestMapping(path = "/settings/profile/username/change/", method = RequestMethod.POST)
  public void changeUsername(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var newUsername = (String) input.get("username");
    findUser(request).thenAccept(user -> userDatabaseTable().changeUserName(
      user.id(), newUsername));
  }

  @RequestMapping(path = "/settings/profile/picture/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> profilePicture(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> profilePictureDatabaseTable.findProfilePicture(
      user.id()).thenAccept(picture -> futureResponse.complete(Map.of("picture", picture))));
    return futureResponse;
  }

  @RequestMapping(path = "/settings/profile/picture/change/", method = RequestMethod.POST)
  public void changeProfilePicture(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var newPicture = (String) input.get("picture");
    findUser(request).thenAccept(user -> profilePictureDatabaseTable.changeProfilePicture(
      user.id(), newPicture));
  }
}
