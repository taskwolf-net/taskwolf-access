package com.dulno.access.stripe;

import com.dulno.access.verification.Verification;
import com.dulno.access.verification.VerificationLoginController;
import com.dulno.core.access.DulnoHomeRestController;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.stripe.StripeCompletionDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripeCompletionController extends DulnoHomeRestController {
  private final Key productKey;
  private final Key refreshKey;
  private final StripeCompletionDatabaseTable stripeCompletionDatabaseTable;
  private final VerificationLoginController verificationLoginController;

  private StripeCompletionController(
    @Qualifier("homeKey") Key homeKey, @Qualifier("productKey") Key productKey,
    @Qualifier("refreshKey") Key refreshKey, UserDatabaseTable userDatabaseTable,
    StripeCompletionDatabaseTable stripeCompletionDatabaseTable,
    VerificationLoginController verificationLoginController
  ) {
    super(homeKey, userDatabaseTable);
    this.productKey = productKey;
    this.refreshKey = refreshKey;
    this.stripeCompletionDatabaseTable = stripeCompletionDatabaseTable;
    this.verificationLoginController = verificationLoginController;
  }

  @RequestMapping(path = "/stripe/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completeStripeProcess(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var completionToken = body.getString("token");
    var userId = findUserId(request);
    return stripeCompletionDatabaseTable
      .stripeCompletionExists(userId, completionToken)
      .thenCompose(exists -> checkCompletionExistence(request, userId,
        completionToken, exists));
  }

  private CompletableFuture<Map<String, Object>> checkCompletionExistence(
    HttpServletRequest request, UUID userId, String completionToken,
    boolean completionExists
  ) {
    if (!completionExists) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return userDatabaseTable().findUser(userId).thenCompose(user ->
      stripeCompletionDatabaseTable
        .isStripeCompletionConfirmed(userId, completionToken)
        .thenCompose(confirmed -> checkCompletionConfirmed(request, user,
          completionToken, confirmed)));
  }

  private CompletableFuture<Map<String, Object>> checkCompletionConfirmed(
    HttpServletRequest request, User user,  String completionToken,
    boolean completionConfirmed
  ) {
    if (!completionConfirmed) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    stripeCompletionDatabaseTable.deleteStripeCompletion(user.id(), completionToken);
    var verification = Verification.create(userDatabaseTable(), secretKey(),
      productKey, refreshKey, user.email(), "");
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    verificationLoginController.processAuthorizedLogin(request, verification,
      futureResponse);
    return futureResponse;
  }
}