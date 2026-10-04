package net.taskwolf.access.setting;

import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.Accessors;

import java.security.Key;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Accessors(fluent = true)
public class SettingController extends TaskwolfRestController {
  private final Key productKey;
  private final Key homeKey;

  protected SettingController(
    Key productKey, Key homeKey, UserDatabaseTable userDatabaseTable
  ) {
    super(productKey, userDatabaseTable);
    this.productKey = productKey;
    this.homeKey = homeKey;
  }

  protected UUID findUserId(HttpServletRequest request) {
    if (request.getHeader("Authorization") != null) {
      return findUserId(findProductApiKey(request), productKey);
    }
    return findUserId(findHomeApiKey(request), homeKey);
  }

  private UUID findUserId(String apiKey, Key key) {
    return UUID.fromString(Jwts.parser().setSigningKey(key).build()
      .parseClaimsJws(apiKey).getPayload().get("id", String.class));
  }

  protected UUID findSessionId(HttpServletRequest request) {
    if (request.getHeader("Authorization") != null) {
      return findSessionId(findProductApiKey(request), productKey);
    }
    return findSessionId(findHomeApiKey(request), homeKey);
  }

  private UUID findSessionId(String apiKey, Key key) {
    return UUID.fromString(Jwts.parser().setSigningKey(key).build()
      .parseClaimsJws(apiKey).getPayload().get("session", String.class));
  }

  protected CompletableFuture<User> findUser(HttpServletRequest request) {
    return userDatabaseTable().findUser(findUserId(request));
  }

  private String findProductApiKey(HttpServletRequest request) {
    return request.getHeader("Authorization").replace("Bearer ", "");
  }

  private String findHomeApiKey(HttpServletRequest request) {
    return request.getHeader("Home-Authorization").replace("Bearer ", "");
  }
}
