package com.dulno.access.ticket;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.ticket.Ticket;
import com.dulno.core.ticket.TicketDatabaseTable;
import com.dulno.core.ticket.TicketMessage;
import com.dulno.core.ticket.TicketMessageDatabaseTable;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.activity.ActivityType;
import com.dulno.core.user.activity.UserActivityDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.UUID;

@RestController
public final class TicketModificationController extends TicketController {
  private final UserActivityDatabaseTable activityDatabaseTable;

  private TicketModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    TicketDatabaseTable ticketDatabaseTable,
    TicketMessageDatabaseTable ticketMessageDatabaseTable,
    UserActivityDatabaseTable activityDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, ticketDatabaseTable,
      ticketMessageDatabaseTable);
    this.activityDatabaseTable = activityDatabaseTable;
  }

  @RequestMapping(path = "/ticket/create/", method = RequestMethod.POST)
  public void createTicket(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var userId = findUserId(request);
    ticketDatabaseTable().generateAvailableTicketId().thenAccept(ticketId ->
      ticketMessageDatabaseTable().generateAvailableTicketMessageId().thenAccept(
        messageId -> createTicket(userId, ticketId, messageId,
          body.getString("title"), body.getString("type"),
          body.getString("message"))));
  }

  private void createTicket(
    UUID userId, UUID ticketId, UUID messageId, String title, String type,
    String message
  ) {
    ticketDatabaseTable().insertTicket(Ticket.create(ticketId, userId, title,
      Ticket.Type.valueOf(type), Ticket.Status.OPEN, -1, List.of(messageId)));
    ticketMessageDatabaseTable().insertTicketMessage(TicketMessage.create(messageId,
      ticketId, userId, message, System.currentTimeMillis()));
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

  @RequestMapping(path = "/ticket/delete/", method = RequestMethod.POST)
  public void deleteTicket(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var userId = findUserId(request);
    performTicketOperation(userId, body.getUUID("ticket"),
      ticket -> deleteTicket(ticket, true), () -> {});
  }

  public void deleteTicket(Ticket ticket, boolean activity) {
    ticketDatabaseTable().deleteTicket(ticket.id());
    for (var message : ticket.messages()) {
      ticketMessageDatabaseTable().deleteTicketMessage(message);
    }
    if (activity) {
      activityDatabaseTable.insertActivity(ticket.creator(), "activity.ticket.delete.title",
        "activity.ticket.delete.description", ActivityType.TICKET);
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
      messageId, ticket.id(), userId, message, System.currentTimeMillis()));
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
