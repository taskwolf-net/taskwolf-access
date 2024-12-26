package com.dulno.access.activity;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.locale.Translation;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.activity.Activity;
import com.dulno.core.user.activity.UserActivityDatabaseTable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class ActivityController extends DulnoRestController {
  private final UserActivityDatabaseTable activityDatabaseTable;
  private final Translation translation;

  private ActivityController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    UserActivityDatabaseTable activityDatabaseTable, Translation translation
  ) {
    super(secretKey, userDatabaseTable);
    this.activityDatabaseTable = activityDatabaseTable;
    this.translation = translation;
  }

  @RequestMapping(path = "/activities/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findActivities(
    HttpServletRequest request
  ) {
    return findUser(request).thenCompose(user ->
      activityDatabaseTable.findActivitiesOfUser(user.id()).thenApply(activities ->
        Map.of("activities", activities.stream()
          .sorted(Comparator.comparing(Activity::time).reversed()).limit(3)
          .map(activity -> assemblyActivityInformation(user, activity)).toList())));
  }

  private Map<String, Object> assemblyActivityInformation(
    User user, Activity activity
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("title", translation.translate(user, activity.title()));
    information.put("description", translation.translate(user, activity.description()));
    information.put("time", formatTime(activity.time()));
    information.put("type", activity.type().toString());
    return information;
  }

  private String formatTime(long time) {
    var calendar = Calendar.getInstance();
    calendar.setTimeInMillis(time);
    return new SimpleDateFormat("dd.MM.yyyy").format(calendar.getTime());
  }
}
