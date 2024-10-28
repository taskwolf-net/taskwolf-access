package com.dulno.access.ticket;

import com.dulno.core.database.paging.DatabaseDirection;
import com.dulno.core.database.paging.DatabaseOrder;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.ticket.Ticket;
import com.dulno.core.ticket.TicketDatabaseTable;
import com.dulno.core.ticket.TicketMessage;
import com.dulno.core.ticket.TicketMessageDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import org.springframework.beans.factory.annotation.Qualifier;
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
    @Qualifier("productKey") Key productKey, @Qualifier("homeKey") Key homeKey,
    UserDatabaseTable userDatabaseTable, TicketDatabaseTable ticketDatabaseTable,
    TicketMessageDatabaseTable ticketMessageDatabaseTable
  ) {
    super(productKey, homeKey, userDatabaseTable, ticketDatabaseTable,
      ticketMessageDatabaseTable);
  }

  @RequestMapping(path = "/tickets/page/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findTicketsPage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var targetPage = body.getInt("targetPage");
    var sortingColumn = body.getString("sorting");
    var sortingOrder = DatabaseOrder.valueOf(body.getString("order"));
    var search = body.getString("search");
    var type = body.has("type") ? body.getString("type") : null;
    var status = body.has("status") ? body.getString("status") : null;
    return findUser(request)
      .thenCompose(user -> ticketDatabaseTable().findTicketsOfCreator(user.id(),
          targetPage, sortingColumn, sortingOrder, search, type, status)
        .thenApply(page -> Map.of("tickets",
          page.content().stream().map(this::superficialTicketInformation).toList(),
          "page", page.pageState(), "pageNumber", page.pageNumber())));
  }

  @RequestMapping(path = "/tickets/page/shift/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> shiftTicketsPage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var pageState = body.getString("pageState");
    var startingPoint = DatabaseDirection.valueOf(body.getString("startingPoint"));
    var direction = DatabaseDirection.valueOf(body.getString("direction"));
    var sortingColumn = body.getString("sorting");
    var sortingOrder = DatabaseOrder.valueOf(body.getString("order"));
    var type = body.has("type") ? body.getString("type") : null;
    var status = body.has("status") ? body.getString("status") : null;
    return findUser(request)
      .thenCompose(user -> ticketDatabaseTable().findTicketsOfCreator(user.id(),
          pageState, startingPoint, direction, sortingColumn, sortingOrder,
          type, status)
        .thenApply(page -> Map.of("tickets",
          page.content().stream().map(this::superficialTicketInformation).toList(),
          "page", page.pageState(), "pageNumber", page.pageNumber())));
  }

  @RequestMapping(path = "/ticket/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findTicket(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    var ticketId = body.getUUID("ticket");
    performTicketOperation(findUserId(request), ticketId, ticket ->
        AsyncIterator.execute(ticket.messages(),
            ticketMessageDatabaseTable()::findTicketMessage)
          .thenAccept(messages -> detailedTicketInformation(ticket, messages)
            .thenAccept(futureResponse::complete)),
      () -> futureResponse.complete(Maps.newHashMap()));
    return futureResponse;
  }

  private Map<String, Object> superficialTicketInformation(Ticket ticket) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", ticket.id());
    information.put("title", ticket.title());
    information.put("type", ticket.type());
    information.put("status", ticket.status());
    information.put("lastMessageSeen", ticket.lastMessageSeen());
    return information;
  }

  private CompletableFuture<Map<String, Object>> detailedTicketInformation(
    Ticket ticket, List<TicketMessage> messages
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(messages, this::messageInformation)
      .thenAccept(information -> futureResponse.complete(
        assemblyDetailedTicketInformation(ticket, information)))
      .thenAccept(value -> checkTicketLastMessageSeen(ticket));
    return futureResponse;
  }

  private void checkTicketLastMessageSeen(Ticket ticket) {
    if (!ticket.lastMessageSeen()) {
      ticketDatabaseTable().updateTicketLastMessageSeen(ticket.id(), true);
    }
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
    TicketMessage message
  ) {
    var selfWritten = message.authorType().isUser();
    var futureAuthorName = selfWritten ?
      userDatabaseTable().findUser(message.author()).thenApply(User::name) :
      CompletableFuture.completedFuture("Dulno");
    return futureAuthorName.thenApply(author ->
      assemblyMessageInformation(message, author, selfWritten));
  }

  private Map<String, Object> assemblyMessageInformation(
    TicketMessage message, String authorName, boolean selfWritten
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", message.id());
    information.put("author", authorName);
    information.put("selfWritten", selfWritten);
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

  @RequestMapping(path = "/tickets/unseen/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> hasUnseenTickets(
    HttpServletRequest request
  ) {
    return ticketDatabaseTable().hasUnseenTickets(findUserId(request))
      .thenApply(unseenTickets -> Map.of("unseenTickets", unseenTickets));
  }
}

