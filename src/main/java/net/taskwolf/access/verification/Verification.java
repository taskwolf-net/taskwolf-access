package net.taskwolf.access.verification;

import io.jsonwebtoken.Jwts;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.taskwolf.core.user.UserDatabaseTable;

import java.security.Key;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class Verification {
  private final UserDatabaseTable userDatabaseTable;
  private final Key secret;
  @Getter
  private final String email;
  private final String passwordHash;

  public CompletableFuture<Boolean> isAuthenticated() {
    var futureResponse = new CompletableFuture<Boolean>();
    if (email == null || passwordHash == null || email.isEmpty()) {
      futureResponse.complete(false);
      return futureResponse;
    }
    userDatabaseTable.userExists(email).thenAccept(exists ->
      isAuthenticated(exists).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<Boolean> isAuthenticated(boolean exists) {
    var futureResponse = new CompletableFuture<Boolean>();
    if (!exists) {
      futureResponse.complete(false);
      return futureResponse;
    }
    userDatabaseTable.findUser(email).thenAccept(user ->
      futureResponse.complete(user.passwordHash().equals(passwordHash)));
    return futureResponse;
  }

  private static final long MAXIMUM_EXPIRATION_TIME = 1000L * 60 * 60 * 24 * 30;

  public String generateApiKey(UUID userId) {
    return generateApiKey(userId, System.currentTimeMillis() + MAXIMUM_EXPIRATION_TIME);
  }

  public String generateApiKey(UUID userId, long expiration) {
    if (expiration - System.currentTimeMillis() > MAXIMUM_EXPIRATION_TIME) {
      expiration = System.currentTimeMillis() + MAXIMUM_EXPIRATION_TIME;
    }
    var expirationDate = new Date(expiration);
    return Jwts.builder().expiration(expirationDate)
      .claim("id", userId.toString())
      .signWith(secret)
      .compact();
  }
}
