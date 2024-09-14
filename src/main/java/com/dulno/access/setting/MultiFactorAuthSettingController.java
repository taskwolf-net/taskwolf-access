package com.dulno.access.setting;

import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.mfa.MultiFactorAuthDatabaseTable;
import com.dulno.core.user.mfa.MultiFactorAuthFactory;
import com.dulno.core.user.mfa.MultiFactorAuthUser;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class MultiFactorAuthSettingController extends DulnoRestController {
  private final MultiFactorAuthDatabaseTable multiFactorAuthDatabaseTable;
  private final MultiFactorAuthFactory multiFactorAuthFactory;

  private MultiFactorAuthSettingController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    MultiFactorAuthDatabaseTable multiFactorAuthDatabaseTable,
    MultiFactorAuthFactory multiFactorAuthFactory
  ) {
    super(secretKey, userDatabaseTable);
    this.multiFactorAuthDatabaseTable = multiFactorAuthDatabaseTable;
    this.multiFactorAuthFactory = multiFactorAuthFactory;
  }

  @RequestMapping(path = "/settings/2fa/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> multiFactorAuth(
    HttpServletRequest request
  ) {
    return multiFactorAuthDatabaseTable.authExists(findUserId(request))
      .thenApply(exists -> Map.of("enabled", exists));
  }

  @RequestMapping(path = "/settings/2fa/switch/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> switchMultiFactorAuth(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return findUser(request).thenCompose(user -> switchMultiFactorAuth(user,
      body.getString("password")));
  }

  private CompletableFuture<Map<String, Object>> switchMultiFactorAuth(
    User user, String password
  ) {
    if (!user.passwordHash().equals(hashPassword(password))) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return multiFactorAuthDatabaseTable.authExists(user.id())
      .thenCompose(exists -> switchMultiFactorAuth(user.id(), exists));
  }

  private CompletableFuture<Map<String, Object>> switchMultiFactorAuth(
    UUID userId, boolean multiFactorAuthEnabled
  ) {
    if (multiFactorAuthEnabled) {
      multiFactorAuthDatabaseTable.deleteAuth(userId);
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var auth = multiFactorAuthFactory.createAuth(userId);
    return auth.setup().thenCompose(value -> auth.generateQRCode()
      .thenApply(Base64::encodeBase64String)
      .thenCompose(qrCodeEncoded -> multiFactorAuthDatabaseTable.findAuth(userId)
        .thenApply(authUser -> Map.of("qrCode", qrCodeEncoded, "secret",
          authUser.secret(), "recoveryCodes", authUser.recoveryCodes()))));
  }

  private String hashPassword(String password) {
    return Hashing.sha256().hashString(password, StandardCharsets.UTF_8)
      .toString();
  }

  @RequestMapping(path = "/settings/2fa/confirm/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> confirmMultiFactorAuth(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var userId = findUserId(request);
    return multiFactorAuthDatabaseTable.authExists(userId)
      .thenCompose(exists -> confirmMultiFactorAuth(userId,
        body.getString("code"), exists));
  }

  private CompletableFuture<Map<String, Object>> confirmMultiFactorAuth(
    UUID userId, String code, boolean multiFactorEnabled
  ) {
    if (!multiFactorEnabled) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return multiFactorAuthDatabaseTable.findAuth(userId)
      .thenCompose(auth -> confirmMultiFactorAuth(userId, code, auth));
  }

  private CompletableFuture<Map<String, Object>> confirmMultiFactorAuth(
    UUID userId, String code, MultiFactorAuthUser auth
  ) {
    if (auth.confirmed()) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return multiFactorAuthFactory.createAuth(userId).verifyCode(code)
      .thenCompose(verified -> !verified ?
        CompletableFuture.completedFuture(Map.of("success", false)) :
        multiFactorAuthDatabaseTable.confirmAuth(userId)
          .thenApply(value -> Map.of("success", true)));
  }

  @RequestMapping(path = "/settings/2fa/isConfirmed/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> isMultiFactorAuthConfirmed(
    HttpServletRequest request
  ) {
    var userId = findUserId(request);
    return multiFactorAuthDatabaseTable.authExists(userId)
      .thenCompose(exists -> isMultiFactorAuthConfirmed(userId, exists));
  }

  private CompletableFuture<Map<String, Object>> isMultiFactorAuthConfirmed(
    UUID userId, boolean multiFactorEnabled
  ) {
    if (!multiFactorEnabled) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return multiFactorAuthDatabaseTable.findAuth(userId)
      .thenApply(auth -> Map.of("success", true, "confirmed", auth.confirmed()));
  }
}