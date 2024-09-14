package com.dulno.access.setting;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.activity.ActivityType;
import com.dulno.core.user.activity.UserActivityDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class ProfileSettingController extends DulnoRestController {
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
    var body = DulnoRequestBody.of(payload, response);
    var userId = findUserId(request);
    userDatabaseTable().changeUserName(userId, body.getString("username"));
    activityDatabaseTable.insertActivity(userId, "activity.setting.username.title",
      "activity.setting.username.description", ActivityType.SETTING);
  }
}
