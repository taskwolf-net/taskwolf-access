package net.taskwolf.access.stripe;

import net.taskwolf.access.verification.Verification;
import net.taskwolf.access.verification.VerificationLoginController;
import net.taskwolf.core.access.TaskwolfHomeRestController;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.hashing.Hashing;
import net.taskwolf.core.stripe.StripeCompletionDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
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
public final class StripeCompletionController extends TaskwolfHomeRestController {
  private final Key productKey;
  private final Key refreshKey;
  private final StripeCompletionDatabaseTable stripeCompletionDatabaseTable;
  private final VerificationLoginController verificationLoginController;
  private final Hashing hashing;

  private StripeCompletionController(
    @Qualifier("homeKey") Key homeKey, @Qualifier("productKey") Key productKey,
    @Qualifier("refreshKey") Key refreshKey, UserDatabaseTable userDatabaseTable,
    StripeCompletionDatabaseTable stripeCompletionDatabaseTable,
    VerificationLoginController verificationLoginController, Hashing hashing
  ) {
    super(homeKey, userDatabaseTable);
    this.productKey = productKey;
    this.refreshKey = refreshKey;
    this.stripeCompletionDatabaseTable = stripeCompletionDatabaseTable;
    this.verificationLoginController = verificationLoginController;
    this.hashing = hashing;
  }

  @RequestMapping(path = "/stripe/complete/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> completeStripeProcess(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
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
      productKey, refreshKey, hashing, user.email(), "");
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    verificationLoginController.processAuthorizedLogin(request, verification,
      futureResponse);
    return futureResponse;
  }
}