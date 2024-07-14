package net.taskwolf.access.setting;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.mfa.MultiFactorAuthDatabaseTable;
import net.taskwolf.core.user.mfa.MultiFactorAuthFactory;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class MultiFactorAuthSettingController extends TaskwolfRestController {
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

  @RequestMapping(path = "/settings/2fa/switch/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> switchMultiFactorAuth(
    HttpServletRequest request
  ) {
    var userId = findUserId(request);
    return multiFactorAuthDatabaseTable.authExists(userId)
      .thenCompose(exists -> switchMultiFactorAuth(userId, exists));
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
        .thenApply(authUser -> Map.of("qrCode", qrCodeEncoded, "recoveryCodes",
          authUser.recoveryCodes()))));
  }
}