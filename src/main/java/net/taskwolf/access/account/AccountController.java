package net.taskwolf.access.account;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.account.AccountLink;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.user.UserDatabaseTable;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
public final class AccountController extends TaskwolfRestController {
  private final CoreModule coreModule;

  private AccountController(
    Key secretKey, UserDatabaseTable userDatabaseTable, CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/account/apps/", method = RequestMethod.GET)
  public CompletableFuture<String> findAccountApps(
    HttpServletRequest request
  ) {
    var userId = findUserId(request);
    var modules = coreModule.moduleLoader().allRegisteredModules().stream()
      .filter(module -> module.module().accountLink() != null).toList();
    var futureResponse = new CompletableFuture<String>();
    AsyncIterator.execute(modules, module -> module.module().accountLink()
        .accountExists(userId).thenApply(exists -> new AbstractMap.SimpleEntry<>(module, exists)),
      modules.size(), entries -> futureResponse.complete(new JSONObject(Map.of("apps",
        entries.stream().filter(AbstractMap.SimpleEntry::getValue).map(entry ->
          entry.getKey().module().moduleInformation()).map(entry ->
          new JSONObject(Map.of("logo", entry.logo(), "name", entry.name()))).toList())).toString()));
    return futureResponse;
  }

  @RequestMapping(path = "/accounts/", method = RequestMethod.POST)
  public CompletableFuture<String> findAccounts(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var userId = findUserId(request);
    var registeredModule = coreModule.moduleLoader().findModule(module);
    if (registeredModule.isEmpty()) {
      return CompletableFuture.completedFuture("");
    }
    return registeredModule.get().accountLink().findAccounts(userId)
      .thenApply(accounts -> new JSONObject(Map.of("accounts", accounts.stream()
        .map(JSONObject::new).collect(Collectors.toList()))).toString());
  }

  @RequestMapping(path = "/account/remove/", method = RequestMethod.POST)
  public void removeAccount(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var identifier = (String) input.get("identifier");
    var userId = findUserId(request);
    var registeredModule = coreModule.moduleLoader().findModule(module);
    if (registeredModule.isEmpty()) {
      return;
    }
    registeredModule.get().accountLink().removeAccount(userId, identifier);
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
