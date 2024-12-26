package com.dulno.access.account;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.account.AccountLink;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.locale.Translation;
import com.dulno.core.module.Module;
import com.dulno.core.module.ModuleLoader;
import com.dulno.core.module.RegisteredModule;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
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
public final class AccountController extends DulnoRestController {
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final TeamTargetDatabaseTable teamTargetDatabaseTable;
  private final ModuleLoader moduleLoader;
  private final Translation translation;

  private AccountController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    ModuleLoader moduleLoader, Translation translation
  ) {
    super(secretKey, userDatabaseTable);
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.teamTargetDatabaseTable = teamTargetDatabaseTable;
    this.moduleLoader = moduleLoader;
    this.translation = translation;
  }

  @RequestMapping(path = "/account/apps/", method = RequestMethod.GET)
  public CompletableFuture<String> findAccountApps(HttpServletRequest request) {
    var futureResponse = new CompletableFuture<String>();
    findAccountTarget(findUserId(request))
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
          new AbstractMap.SimpleEntry<>(module, exists))).thenAccept(
      entries -> futureResponse.complete(entries.stream().filter(
        AbstractMap.SimpleEntry::getValue).map(AbstractMap.SimpleEntry::getKey).toList()));
    return futureResponse;
  }

  private String finishAccountAppFinding(List<RegisteredModule> modules) {
    return new JSONObject(Map.of("apps", modules.stream().map(entry ->
        new JSONObject(Map.of("logo", entry.module().moduleInformation().logo(),
          "name", entry.module().moduleInformation().name(), "id", entry.name())))
      .toList())).toString();
  }

  @RequestMapping(path = "/accounts/", method = RequestMethod.POST)
  public CompletableFuture<String> findAccounts(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var registeredModule = moduleLoader.findRegisteredModuleById(body.getString("module"));
    return registeredModule.map(module ->
      findAccounts(findUserId(request), module.module())).orElse(null);
  }

  private CompletableFuture<String> findAccounts(UUID userId, Module module) {
    var futureResponse = new CompletableFuture<String>();
    findAccountTarget(userId)
      .thenAccept(target -> module.accountLink().findAccounts(target)
        .thenApply(accounts -> futureResponse.complete(new JSONObject(
          Map.of("accounts", accounts.stream()
            .map(account -> Map.of("identifier", account.identifier(),
              "name", account.name()))
            .toList())).toString())));
    return futureResponse;
  }

  @RequestMapping(path = "/account/remove/", method = RequestMethod.POST)
  public void removeAccount(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var registeredModule = moduleLoader.findRegisteredModuleById(body.getString("module"));
    if (registeredModule.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    findAccountTarget(findUserId(request))
      .thenAccept(target -> registeredModule.get().module().accountLink()
        .removeAccount(target, body.getString("identifier")));
  }

  public void deleteAllAccounts(UUID targetId) {
    findLinkedAccounts(targetId).thenApply(modules ->
        modules.stream().map(module -> module.module().accountLink()).toList())
      .thenAccept(accountLinks -> deleteAccounts(targetId, accountLinks));
  }

  private void deleteAccounts(UUID targetId, List<AccountLink> accountLinks) {
    AsyncIterator.execute(accountLinks, accountLink ->
        accountLink.findAccounts(targetId).thenApply(accounts ->
          new AbstractMap.SimpleEntry<>(accountLink, accounts))).thenAccept(
      entries -> entries.forEach(entry -> entry.getValue().forEach(account ->
        entry.getKey().removeAccount(targetId, account.identifier()))));
  }

  @RequestMapping(path = "/account/information/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findAccountInformation(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var registeredModule = moduleLoader.findRegisteredModuleById(body.getString("module"));
    return registeredModule.map(module ->
      findAccountInformation(request, module.module().accountLink())).orElse(null);
  }

  private CompletableFuture<Map<String, Object>> findAccountInformation(
    HttpServletRequest request, AccountLink accountLink
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> findAccountTarget(user.id())
      .thenAccept(target -> accountLink.accountExists(target)
        .thenAccept(exists -> futureResponse.complete(assemblyAccountInformation(
          user, accountLink, findApiKey(request), target, exists)))));
    return futureResponse;
  }

  private Map<String, Object> assemblyAccountInformation(
    User user, AccountLink accountLink, String apiKey, UUID target,
    boolean accountExists
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("accountExists", accountExists);
    information.put("registrationUrl", accountLink.registrationUrl(target, apiKey));
    information.put("linkDescription", translation.translate(user,
      accountLink.description()));
    return information;
  }

  private CompletableFuture<UUID> findAccountTarget(UUID userId) {
    return userTargetDatabaseTable.findTargetSecured(userId).thenCompose(target ->
      userId.equals(target) ? CompletableFuture.completedFuture(target) :
        teamTargetDatabaseTable.findTargetSecured(userId)
          .thenApply(team -> team.orElse(target)));
  }
}
