package com.dulno.access.ticket;

import com.dulno.core.user.User;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.ticket.Ticket;
import com.dulno.core.ticket.TicketDatabaseTable;
import com.dulno.core.ticket.TicketMessageDatabaseTable;
import com.dulno.core.user.UserDatabaseTable;

import java.security.Key;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Accessors(fluent = true)
public class TicketController extends DulnoRestController {
  private final Key productKey;
  private final Key homeKey;
  @Getter(AccessLevel.PROTECTED)
  private final TicketDatabaseTable ticketDatabaseTable;
  @Getter(AccessLevel.PROTECTED)
  private final TicketMessageDatabaseTable ticketMessageDatabaseTable;

  protected TicketController(
    Key productKey, Key homeKey, UserDatabaseTable userDatabaseTable,
    TicketDatabaseTable ticketDatabaseTable,
    TicketMessageDatabaseTable ticketMessageDatabaseTable
  ) {
    super(productKey, userDatabaseTable);
    this.productKey = productKey;
    this.homeKey = homeKey;
    this.ticketDatabaseTable = ticketDatabaseTable;
    this.ticketMessageDatabaseTable = ticketMessageDatabaseTable;
  }

  protected void performTicketOperation(
    UUID userId, UUID ticketId, Consumer<Ticket> operation, Runnable failResponse
  ) {
    ticketDatabaseTable.ticketExists(ticketId).thenAccept(exists ->
      performTicketOperation(userId, ticketId, exists, operation, failResponse));
  }

  private void performTicketOperation(
    UUID userId, UUID ticketId, boolean ticketExists, Consumer<Ticket> operation,
    Runnable failResponse
  ) {
    if (!ticketExists) {
      failResponse.run();
      return;
    }
    ticketDatabaseTable.findTicket(ticketId).thenAccept(ticket ->
      performTicketOperation(userId, ticket, operation, failResponse));
  }

  private void performTicketOperation(
    UUID userId, Ticket ticket, Consumer<Ticket> operation, Runnable failResponse
  ) {
    if (!ticket.creator().equals(userId)) {
      failResponse.run();
      return;
    }
    operation.accept(ticket);
  }

  protected UUID findUserId(HttpServletRequest request) {
    if (request.getHeader("Authorization") != null) {
      return findUserId(findProductApiKey(request), productKey);
    }
    return findUserId(findHomeApiKey(request), homeKey);
  }

  private UUID findUserId(String apiKey, Key key) {
    return UUID.fromString(Jwts.parser().setSigningKey(key).build()
      .parseClaimsJws(apiKey).getPayload().get("id", String.class));
  }

  protected UUID findSessionId(HttpServletRequest request) {
    if (request.getHeader("Authorization") != null) {
      return findSessionId(findProductApiKey(request), productKey);
    }
    return findSessionId(findHomeApiKey(request), homeKey);
  }

  private UUID findSessionId(String apiKey, Key key) {
    return UUID.fromString(Jwts.parser().setSigningKey(key).build()
      .parseClaimsJws(apiKey).getPayload().get("session", String.class));
  }

  protected CompletableFuture<User> findUser(HttpServletRequest request) {
    return userDatabaseTable().findUser(findUserId(request));
  }

  private String findProductApiKey(HttpServletRequest request) {
    return request.getHeader("Authorization").replace("Bearer ", "");
  }

  private String findHomeApiKey(HttpServletRequest request) {
    return request.getHeader("Home-Authorization").replace("Bearer ", "");
  }
}
