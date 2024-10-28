package com.dulno.access.ticket;

import com.dulno.core.ticket.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.activity.ActivityType;
import com.dulno.core.user.activity.UserActivityDatabaseTable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TicketModificationController extends TicketController {
  private final UserActivityDatabaseTable activityDatabaseTable;

  private TicketModificationController(
    @Qualifier("productKey") Key productKey, @Qualifier("homeKey") Key homeKey,
    UserDatabaseTable userDatabaseTable, TicketDatabaseTable ticketDatabaseTable,
    TicketMessageDatabaseTable ticketMessageDatabaseTable,
    UserActivityDatabaseTable activityDatabaseTable
  ) {
    super(productKey, homeKey, userDatabaseTable, ticketDatabaseTable,
      ticketMessageDatabaseTable);
    this.activityDatabaseTable = activityDatabaseTable;
  }

  @RequestMapping(path = "/ticket/create/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> createTicket(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var userId = findUserId(request);
    return ticketDatabaseTable().findTicketCount(userId)
      .thenApply(ticketCount -> createTicket(userId, body.getString("title"),
        body.getString("type"), body.getString("message"), ticketCount));
  }

  private static final long TICKET_LIMIT = 5;

  private Map<String, Object> createTicket(
    UUID userId, String title, String type,
    String message, long ticketCount
  ) {
    if (ticketCount + 1 > TICKET_LIMIT) {
      return Map.of("success", false);
    }
    ticketDatabaseTable().generateAvailableTicketId()
      .thenAccept(ticketId -> ticketMessageDatabaseTable()
        .generateAvailableTicketMessageId()
        .thenAccept(messageId -> createTicket(userId, ticketId, messageId,
          title, type, message)));
    return Map.of("success", true);
  }

  private void createTicket(
    UUID userId, UUID ticketId, UUID messageId, String title, String type,
    String message
  ) {
    ticketDatabaseTable().insertTicket(Ticket.create(userId, ticketId, title,
      Ticket.Type.valueOf(type), Ticket.Status.OPEN, -1, List.of(messageId)));
    ticketMessageDatabaseTable().insertTicketMessage(TicketMessage.create(messageId,
      ticketId, userId, TicketMessageAuthorType.USER, message,
      System.currentTimeMillis()));
    activityDatabaseTable.insertActivity(userId, "activity.ticket.new.title",
      "activity.ticket.new.description", ActivityType.TICKET);
  }

  @RequestMapping(path = "/ticket/rename/", method = RequestMethod.POST)
  public void renameTicket(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var ticketId = body.getUUID("ticket");
    var userId = findUserId(request);
    performTicketOperation(userId, ticketId, ticket ->
      ticketDatabaseTable().renameTicket(ticketId, body.getString("title")),
      () -> {});
  }

  @RequestMapping(path = "/ticket/close/", method = RequestMethod.POST)
  public void closeTicket(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var userId = findUserId(request);
    performTicketOperation(userId, body.getUUID("ticket"), this::closeTicket,
      () -> {});
  }

  private void closeTicket(Ticket ticket) {
    ticketDatabaseTable().updateTicketStatus(ticket.id(), Ticket.Status.CLOSED);
    activityDatabaseTable.insertActivity(ticket.creator(), "activity.ticket.close.title",
      "activity.ticket.close.description", ActivityType.TICKET);
  }

  public void deleteTicket(Ticket ticket) {
    ticketDatabaseTable().deleteTicket(ticket.id());
    for (var message : ticket.messages()) {
      ticketMessageDatabaseTable().deleteTicketMessage(message);
    }
  }

  @RequestMapping(path = "/ticket/message/add/", method = RequestMethod.POST)
  public void addTicketMessage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var userId = findUserId(request);
    performTicketOperation(userId, body.getUUID("ticket"), ticket ->
      ticketMessageDatabaseTable().generateAvailableTicketMessageId()
        .thenAccept(messageId -> addTicketMessage(userId, ticket, messageId,
          body.getString("message"))), () -> {});
  }

  private void addTicketMessage(
    UUID userId, Ticket ticket, UUID messageId, String message
  ) {
    ticket.addMessage(messageId);
    ticket.disableExpirationTime();
    ticketDatabaseTable().updateTicket(ticket);
    ticketMessageDatabaseTable().insertTicketMessage(TicketMessage.create(
      messageId, ticket.id(), userId, TicketMessageAuthorType.USER,
      message, System.currentTimeMillis()));
  }

  @RequestMapping(path = "/ticket/message/delete/", method = RequestMethod.POST)
  public void deleteTicketMessage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var userId = findUserId(request);
    performTicketOperation(userId, body.getUUID("ticket"), ticket ->
      deleteTicketMessage(ticket, body.getUUID("message")), () -> {});
  }

  private void deleteTicketMessage(Ticket ticket, UUID messageId) {
    if (!ticket.messages().contains(messageId) || ticket.messages().indexOf(messageId) == 0) {
      return;
    }
    ticketMessageDatabaseTable().deleteTicketMessage(messageId);
    ticketDatabaseTable().removeTicketMessage(ticket.id(), messageId);
  }
}
