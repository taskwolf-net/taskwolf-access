package net.taskwolf.access.account;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.account.AccountLink;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.module.ModuleLoader;
import net.taskwolf.core.module.RegisteredModule;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
public final class AccountController extends TaskwolfRestController {
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final ModuleLoader moduleLoader;
  private final CoreModule coreModule;

  private AccountController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable, ModuleLoader moduleLoader,
    CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.moduleLoader = moduleLoader;
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/account/apps/", method = RequestMethod.GET)
  public CompletableFuture<String> findAccountApps(HttpServletRequest request) {
    var futureResponse = new CompletableFuture<String>();
    userTargetDatabaseTable.findTargetSecured(findUserId(request))
      .thenAccept(target -> findLinkedAccounts(target).thenAccept(modules ->
        futureResponse.complete(finishAccountAppFinding(modules))));
    return futureResponse;
  }

  public CompletableFuture<List<RegisteredModule>> findLinkedAccounts(UUID targetId) {
    var modules = moduleLoader.allRegisteredModules().stream()
      .filter(module -> module.module().accountLink() != null)
      .filter(module -> !module.module().accountLink().registrationUrl(targetId,
        "").isEmpty()).toList();
    var futureResponse = new CompletableFuture<List<RegisteredModule>>();
    AsyncIterator.execute(modules, module -> module.module().accountLink()
        .accountExists(targetId).thenApply(exists ->
          new AbstractMap.SimpleEntry<>(module, exists)), modules.size(),
      entries -> futureResponse.complete(entries.stream().filter(
        AbstractMap.SimpleEntry::getValue).map(AbstractMap.SimpleEntry::getKey).toList()));
    return futureResponse;
  }

  private String finishAccountAppFinding(List<RegisteredModule> modules) {
    return new JSONObject(Map.of("apps", modules.stream().map(entry ->
      entry.module().moduleInformation()).map(entry -> new JSONObject(Map.of(
      "logo", entry.logo(), "name", entry.name()))).toList())).toString();
  }

  @RequestMapping(path = "/accounts/", method = RequestMethod.POST)
  public CompletableFuture<String> findAccounts(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var registeredModule = moduleLoader.findModule(module);
    if (registeredModule.isEmpty()) {
      return CompletableFuture.completedFuture("");
    }
    var futureResponse = new CompletableFuture<String>();
    userTargetDatabaseTable.findTargetSecured(findUserId(request)).thenAccept(
      target -> registeredModule.get().accountLink().findAccounts(target)
        .thenApply(accounts -> futureResponse.complete(new JSONObject(
          Map.of("accounts", accounts.stream().map(JSONObject::new)
            .collect(Collectors.toList()))).toString())));
    return futureResponse;
  }

  @RequestMapping(path = "/account/remove/", method = RequestMethod.POST)
  public void removeAccount(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var identifier = (String) input.get("identifier");
    var registeredModule = moduleLoader.findModule(module);
    if (registeredModule.isEmpty()) {
      return;
    }
    userTargetDatabaseTable.findTargetSecured(findUserId(request)).thenAccept(
      target -> registeredModule.get().accountLink().removeAccount(
        target, identifier));
  }

  public void deleteAllAccounts(UUID targetId) {
    findLinkedAccounts(targetId).thenApply(modules ->
        modules.stream().map(module -> module.module().accountLink()).toList())
      .thenAccept(accountLinks -> deleteAccounts(targetId, accountLinks));
  }

  private void deleteAccounts(UUID targetId, List<AccountLink> accountLinks) {
    AsyncIterator.execute(accountLinks, accountLink ->
        accountLink.findAccounts(targetId).thenApply(identifier ->
          new AbstractMap.SimpleEntry<>(accountLink, identifier)), accountLinks.size(),
      accounts -> accounts.forEach(account -> account.getValue().forEach(identifier ->
        account.getKey().removeAccount(targetId, identifier))));
  }

  @RequestMapping(path = "/account/information/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findAccountInformation(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var module = (String) input.get("module");
    var registeredModule = moduleLoader.findModule(module);
    if (registeredModule.isEmpty()) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var accountLink = registeredModule.get().accountLink();
    var apiKey = findApiKey(request);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userTargetDatabaseTable.findTargetSecured(findUserId(request)).thenAccept(
      target -> findUser(request).thenAccept(user -> accountLink.accountExists(target)
        .thenAccept(exists -> futureResponse.complete(assemblyAccountInformation(
          user, accountLink, apiKey, target, exists)))));
    return futureResponse;
  }

  private Map<String, Object> assemblyAccountInformation(
    User user, AccountLink accountLink, String apiKey, UUID target,
    boolean accountExists
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("accountExists", accountExists);
    information.put("registrationUrl", accountLink.registrationUrl(target, apiKey));
    information.put("linkDescription", coreModule.translate(user,
      accountLink.description()));
    return information;
  }
}
