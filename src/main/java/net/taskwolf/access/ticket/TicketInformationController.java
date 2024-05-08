package net.taskwolf.access.ticket;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
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
import java.util.*;
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
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var ticketId = body.getUUID("ticket");
    var userId = findUserId(request);
    performTicketOperation(userId, ticketId, ticket -> AsyncIterator.execute(
      ticket.messages(), ticketMessageDatabaseTable()::findTicketMessage)
        .thenAccept(messages -> detailedTicketInformation(userId,
          ticket, messages).thenAccept(futureResponse::complete)),
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
    UUID userId, Ticket ticket, List<TicketMessage> messages
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(messages, message -> messageInformation(userId, message))
      .thenAccept(information -> futureResponse.complete(
        assemblyDetailedTicketInformation(ticket, information)));
    return futureResponse;
  }

  private Map<String, Object> assemblyDetailedTicketInformation(
    Ticket ticket, List<Map<String, Object>> messageInformation
  ) {
    messageInformation.sort(Comparator.comparing(entry ->
      (long) entry.get("rawTime")));
    var information = superficialTicketInformation(ticket);
    information.put("messages", messageInformation);
    return information;
  }

  private CompletableFuture<Map<String, Object>> messageInformation(
    UUID userId, TicketMessage message
  ) {
    return userDatabaseTable().findUser(message.author()).thenApply(author ->
      assemblyMessageInformation(userId, message, author));
  }

  private Map<String, Object> assemblyMessageInformation(
    UUID userId, TicketMessage message, User author
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", message.id());
    information.put("author", author.name());
    information.put("selfWritten", message.author().equals(userId));
    information.put("message", message.message());
    information.put("rawTime", message.time());
    information.put("time", formatTime(message.time()));
    return information;
  }

  private String formatTime(long time) {
    var calendar = Calendar.getInstance();
    calendar.setTimeInMillis(time);
    return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(calendar.getTime());
  }
}

