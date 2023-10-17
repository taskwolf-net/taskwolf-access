package net.taskwolf.access.verification;

import net.taskwolf.core.user.UserDatabaseTable;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

import java.security.Key;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor(staticName = "create")
public final class Verification {
  private final UserDatabaseTable userDatabaseTable;
  private final Key secret;
  private final String email;
  private final String passwordHash;

  public CompletableFuture<Boolean> isAuthenticated() {
    var futureResponse = new CompletableFuture<Boolean>();
    if (email == null || passwordHash == null || email.equals("") ||
      passwordHash.equals("")
    ) {
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

  private static final int EXPIRATION_TIME = 1000 * 60 * 60;

  public String generateApiKey() {
    var expiration = new Date(System.currentTimeMillis() + EXPIRATION_TIME);
    return Jwts.builder()
      .setExpiration(expiration)
      .claim("email", email)
      .signWith(secret)
      .compact();
  }
}
