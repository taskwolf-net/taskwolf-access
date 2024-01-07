package net.taskwolf.access.ticket;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.ticket.Ticket;
import net.taskwolf.core.ticket.TicketDatabaseTable;
import net.taskwolf.core.ticket.TicketMessage;
import net.taskwolf.core.ticket.TicketMessageDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TicketInformationController extends TicketController {
  private TicketInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    TicketDatabaseTable ticketDatabaseTable,
    TicketMessageDatabaseTable ticketMessageDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, ticketDatabaseTable,
      ticketMessageDatabaseTable);
  }

  @RequestMapping(path = "/tickets/personal/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findPersonalTickets(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> ticketDatabaseTable()
      .findTicketsByCreator(user.id()).thenAccept(tickets ->
        futureResponse.complete(Map.of("tickets", tickets.stream()
          .map(this::superficialTicketInformation).toList()))));
    return futureResponse;
  }

  @RequestMapping(path = "/ticket/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findTicket(
    HttpServletRequest request, @RequestBody Map<String, Object> input
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var ticketId = UUID.fromString((String) input.get("ticket"));
    var userId = findUserId(request);
    performTicketOperation(userId, ticketId, ticket -> AsyncIterator.execute(
      ticket.messages(), ticketMessageDatabaseTable()::findTicketMessage,
      ticket.messages().size(), messages -> detailedTicketInformation(ticket,
        messages).thenAccept(futureResponse::complete)),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  private Map<String, Object> superficialTicketInformation(Ticket ticket) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", ticket.id());
    information.put("title", ticket.title());
    information.put("type", ticket.type());
    information.put("status", ticket.status());
    return information;
  }

  private CompletableFuture<Map<String, Object>> detailedTicketInformation(
    Ticket ticket, List<TicketMessage> messages
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(messages, this::messageInformation, messages.size(),
      information -> futureResponse.complete(assemblyDetailedTicketInformation(
        ticket, information)));
    return futureResponse;
  }

  private Map<String, Object> assemblyDetailedTicketInformation(
    Ticket ticket, List<Map<String, Object>> messageInformation
  ) {
    var information = superficialTicketInformation(ticket);
    information.put("messages", messageInformation);
    return information;
  }

  private CompletableFuture<Map<String, Object>> messageInformation(
    TicketMessage message
  ) {
    return userDatabaseTable().findUser(message.author()).thenApply(author ->
      assemblyMessageInformation(message, author));
  }

  private Map<String, Object> assemblyMessageInformation(
    TicketMessage message, User author
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("author", author.name());
    information.put("message", message.message());
    information.put("time", formatTime(message.time()));
    return information;
  }

  private String formatTime(long time) {
    var calendar = Calendar.getInstance();
    calendar.setTimeInMillis(time);
    return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(calendar.getTime());
  }
}

