package com.dulno.access.verification;

import io.jsonwebtoken.Jwts;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import com.dulno.core.user.UserDatabaseTable;

import java.security.Key;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class Verification {
  private final UserDatabaseTable userDatabaseTable;
  private final Key homeSecret;
  private final Key productSecret;
  private final Key refreshSecret;
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

  private static final long MAXIMUM_PRODUCT_EXPIRATION_TIME = 1000L * 60 * 10;

  public String generateProductApiKey(UUID userId, UUID sessionId) {
    return generateProductApiKey(userId, sessionId,
      System.currentTimeMillis() + MAXIMUM_PRODUCT_EXPIRATION_TIME);
  }

  public String generateProductApiKey(UUID userId, UUID sessionId, long expiration) {
    if (expiration - System.currentTimeMillis() > MAXIMUM_PRODUCT_EXPIRATION_TIME) {
      expiration = System.currentTimeMillis() + MAXIMUM_PRODUCT_EXPIRATION_TIME;
    }
    var expirationDate = new Date(expiration);
    return Jwts.builder().expiration(expirationDate)
      .claim("id", userId.toString())
      .claim("session", sessionId.toString())
      .signWith(productSecret)
      .compact();
  }

  private static final long MAXIMUM_REFRESH_EXPIRATION_TIME = 1000L * 60 * 60 * 24 * 30;

  public String generateRefreshToken(UUID userId, UUID sessionId) {
    return generateRefreshToken(userId, sessionId,
      System.currentTimeMillis() + MAXIMUM_REFRESH_EXPIRATION_TIME);
  }

  public String generateRefreshToken(UUID userId, UUID sessionId, long expiration) {
    if (expiration - System.currentTimeMillis() > MAXIMUM_REFRESH_EXPIRATION_TIME) {
      expiration = System.currentTimeMillis() + MAXIMUM_REFRESH_EXPIRATION_TIME;
    }
    var expirationDate = new Date(expiration);
    return Jwts.builder().expiration(expirationDate)
      .claim("id", userId.toString())
      .claim("session", sessionId.toString())
      .signWith(refreshSecret)
      .compact();
  }

  public String generateHomeApiKey(UUID userId, UUID sessionId) {
    var expirationDate = new Date(System.currentTimeMillis() + 1000L * 60 * 60);
    return Jwts.builder().expiration(expirationDate)
      .claim("id", userId.toString())
      .claim("session", sessionId.toString())
      .signWith(homeSecret)
      .compact();
  }
}
