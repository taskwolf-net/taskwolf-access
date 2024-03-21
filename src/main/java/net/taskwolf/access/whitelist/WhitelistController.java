package net.taskwolf.access.whitelist;

import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.whitelist.WhitelistConfiguration;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public final class WhitelistController {
  private final WhitelistConfiguration whitelistConfiguration;

  private WhitelistController(
    WhitelistConfiguration whitelistConfiguration
  ) {
    this.whitelistConfiguration = whitelistConfiguration;
  }

  @RequestMapping(path = "/whitelist/isValid/", method = RequestMethod.POST)
  public Map<String, Object> isValid(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var key = body.getString("key");
    return Map.of("isValid", whitelistConfiguration.whitelistKey().equals(key));
  }
}