package com.dulno.access.setting;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class LanguageSettingController extends DulnoRestController {
  private LanguageSettingController(
    Key secretKey, UserDatabaseTable userDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
  }

  @RequestMapping(path = "/settings/language/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> language(
    HttpServletRequest request
  ) {
    return findUser(request).thenApply(user -> Map.of("language", user.language()));
  }

  @RequestMapping(path = "/settings/language/change/", method = RequestMethod.POST)
  public void changeLanguage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    findUser(request).thenAccept(user -> userDatabaseTable().changeUserLanguage(
      user.id(), body.getString("language")));
  }
}
