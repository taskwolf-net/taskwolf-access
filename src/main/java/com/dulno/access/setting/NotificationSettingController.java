package com.dulno.access.setting;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.notification.NotificationDatabaseTable;
import com.dulno.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class NotificationSettingController extends DulnoRestController {
  private final NotificationDatabaseTable notificationDatabaseTable;

  private NotificationSettingController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    NotificationDatabaseTable notificationDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.notificationDatabaseTable = notificationDatabaseTable;
  }

  @RequestMapping(path = "/settings/notification/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> notificationSettings(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var userId = findUserId(request);
    notificationDatabaseTable.findNotificationSettings(userId).thenAccept(setting ->
      futureResponse.complete(Map.of("general", setting.general(), "workflowFail",
        setting.workflowFail())));
    return futureResponse;
  }

  @RequestMapping(path = "/settings/notification/change/", method = RequestMethod.POST)
  public void changeNotificationSettings(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var userId = findUserId(request);
    notificationDatabaseTable.changeNotificationSettings(userId,
      body.getBoolean("general"), body.getBoolean("workflowFail"));
  }
}
