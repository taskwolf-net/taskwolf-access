package net.taskwolf.access.setting;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.activity.ActivityType;
import net.taskwolf.core.user.activity.UserActivityDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class ProfileSettingController extends TaskwolfRestController {
  private final UserActivityDatabaseTable activityDatabaseTable;

  private ProfileSettingController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    UserActivityDatabaseTable activityDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.activityDatabaseTable = activityDatabaseTable;
  }

  @RequestMapping(path = "/settings/profile/username/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> username(
    HttpServletRequest request
  ) {
    return findUser(request).thenApply(user -> Map.of("username", user.name()));
  }

  @RequestMapping(path = "/settings/profile/username/change/", method = RequestMethod.POST)
  public void changeUsername(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var userId = findUserId(request);
    userDatabaseTable().changeUserName(userId, body.getString("username"));
    activityDatabaseTable.insertActivity(userId, "activity.setting.username.title",
      "activity.setting.username.description", ActivityType.SETTING);
  }
}
