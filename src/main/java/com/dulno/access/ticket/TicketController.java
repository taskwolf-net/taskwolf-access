package com.dulno.access.ticket;

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
import java.util.function.Consumer;

@Accessors(fluent = true)
public class TicketController extends DulnoRestController {
  @Getter(AccessLevel.PROTECTED)
  private final TicketDatabaseTable ticketDatabaseTable;
  @Getter(AccessLevel.PROTECTED)
  private final TicketMessageDatabaseTable ticketMessageDatabaseTable;

  protected TicketController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    TicketDatabaseTable ticketDatabaseTable,
    TicketMessageDatabaseTable ticketMessageDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
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
}
