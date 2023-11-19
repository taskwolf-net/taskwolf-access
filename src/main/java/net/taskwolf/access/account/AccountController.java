package net.taskwolf.access.account;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.account.AccountLink;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class AccountController extends TaskwolfRestController {
  private final CoreModule coreModule;

  private AccountController(
    Key secretKey, UserDatabaseTable userDatabaseTable, CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/account/information/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findAccountInformation(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var registeredModule = coreModule.moduleLoader().findModule(module);
    if (registeredModule.isEmpty()) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var accountLink = registeredModule.get().accountLink();
    var apiKey = findApiKey(request);
    var userId = findUserId(request);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    accountLink.accountExists(userId).thenAccept(exists ->
      futureResponse.complete(assemblyAccountInformation(accountLink, apiKey, exists)));
    return futureResponse;
  }

  private Map<String, Object> assemblyAccountInformation(
    AccountLink accountLink, String apiKey, boolean accountExists
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("accountExists", accountExists);
    information.put("registrationUrl", accountLink.registrationUrl(apiKey));
    information.put("linkDescription", accountLink.description());
    return information;
  }
}
