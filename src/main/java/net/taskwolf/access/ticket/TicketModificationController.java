package net.taskwolf.access.ticket;

import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.ticket.Ticket;
import net.taskwolf.core.ticket.TicketDatabaseTable;
import net.taskwolf.core.ticket.TicketMessage;
import net.taskwolf.core.ticket.TicketMessageDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public final class TicketModificationController extends TicketController {
  private TicketModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    TicketDatabaseTable ticketDatabaseTable,
    TicketMessageDatabaseTable ticketMessageDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, ticketDatabaseTable,
      ticketMessageDatabaseTable);
  }

  @RequestMapping(path = "/ticket/create/", method = RequestMethod.POST)
  public void createTicket(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var title = (String) input.get("title");
    var type = (String) input.get("type");
    var message = (String) input.get("message");
    var userId = findUserId(request);
    ticketDatabaseTable().generateAvailableTicketId().thenAccept(ticketId ->
      ticketMessageDatabaseTable().generateAvailableTicketMessageId().thenAccept(
        messageId -> createTicket(userId, ticketId, messageId, title, type, message)));
  }

  private void createTicket(
    UUID userId, UUID ticketId, UUID messageId, String title, String type,
    String message
  ) {
    ticketDatabaseTable().insertTicket(Ticket.create(ticketId, userId, title,
      Ticket.Type.valueOf(type), Ticket.Status.OPEN, List.of(messageId)));
    ticketMessageDatabaseTable().insertTicketMessage(TicketMessage.create(messageId,
      ticketId, userId, message, System.currentTimeMillis()));
  }

  @RequestMapping(path = "/ticket/delete/", method = RequestMethod.POST)
  public void deleteTicket(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var ticketId = UUID.fromString((String) input.get("ticket"));
    var userId = findUserId(request);
    performTicketOperation(userId, ticketId, this::deleteTicket, () -> {});
  }

  private void deleteTicket(Ticket ticket) {
    ticketDatabaseTable().deleteTicket(ticket.id());
    for (var message : ticket.messages()) {
      ticketMessageDatabaseTable().deleteTicketMessage(message);
    }
  }

  @RequestMapping(path = "/ticket/message/add/", method = RequestMethod.POST)
  public void addTicketMessage(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var ticketId = UUID.fromString((String) input.get("ticket"));
    var message = (String) input.get("message");
    var userId = findUserId(request);
    performTicketOperation(userId, ticketId, ticket -> ticketMessageDatabaseTable()
      .generateAvailableTicketMessageId().thenAccept(messageId ->
        addTicketMessage(userId, ticket, messageId, message)), () -> {});
  }

  private void addTicketMessage(
    UUID userId, Ticket ticket, UUID messageId, String message
  ) {
    ticketDatabaseTable().addTicketMessage(ticket.id(), messageId);
    ticketMessageDatabaseTable().insertTicketMessage(TicketMessage.create(
      messageId, ticket.id(), userId, message, System.currentTimeMillis()));
  }

  @RequestMapping(path = "/ticket/message/delete/", method = RequestMethod.POST)
  public void deleteTicketMessage(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var ticketId = UUID.fromString((String) input.get("ticket"));
    var messageId = UUID.fromString((String) input.get("message"));
    var userId = findUserId(request);
    performTicketOperation(userId, ticketId, ticket ->
      deleteTicketMessage(ticket, messageId), () -> {});
  }

  private void deleteTicketMessage(Ticket ticket, UUID messageId) {
    if (!ticket.messages().contains(messageId)) {
      return;
    }
    ticketMessageDatabaseTable().deleteTicketMessage(messageId);
    ticketDatabaseTable().removeTicketMessage(ticket.id(), messageId);
  }
}
