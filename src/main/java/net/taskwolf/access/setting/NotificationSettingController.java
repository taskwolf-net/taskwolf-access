package net.taskwolf.access.setting;

import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.notification.NotificationDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class NotificationSettingController extends TaskwolfRestController {
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
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var general = (boolean) input.get("general");
    var workflowFail = (boolean) input.get("workflowFail");
    var userId = findUserId(request);
    notificationDatabaseTable.changeNotificationSettings(userId, general,
      workflowFail);
  }
}
