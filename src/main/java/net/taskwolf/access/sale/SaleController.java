package net.taskwolf.access.sale;

import net.taskwolf.core.sale.Sale;
import net.taskwolf.core.sale.SaleMessageSenderType;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.sale.SaleDatabaseTable;
import net.taskwolf.core.sale.SaleMessageDatabaseTable;
import org.owasp.html.HtmlPolicyBuilder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class SaleController {
  private final SaleDatabaseTable saleDatabaseTable;
  private final SaleMessageDatabaseTable saleMessageDatabaseTable;

  private SaleController(
    SaleDatabaseTable saleDatabaseTable,
    SaleMessageDatabaseTable saleMessageDatabaseTable
  ) {
    this.saleDatabaseTable = saleDatabaseTable;
    this.saleMessageDatabaseTable = saleMessageDatabaseTable;
  }

  @RequestMapping(path = "/sale/create/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> createSale(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    return saleDatabaseTable.generateAvailableSaleId()
      .thenCompose(saleId -> saleMessageDatabaseTable.generateAvailableMessageId()
        .thenApply(messageId -> createSale(saleId, messageId,
          body.getSanitizedString("email"), body.getSanitizedString("firstName"),
          body.getSanitizedString("lastName"), body.getSanitizedString("phoneNumber"),
          body.getSanitizedString("country"), body.getSanitizedString("companyName"),
          body.getSanitizedString("companySize"), body.getSanitizedString("companyRole"),
          body.getSanitizedString("title"),
          body.getSanitizedString("message", buildMessageSanitizationPolicy()))));
  }

  private PolicyFactory buildMessageSanitizationPolicy() {
    return new HtmlPolicyBuilder()
      .allowAttributes("class", "contenteditable", "data-list").globally()
      .toFactory().and(Sanitizers.FORMATTING
        .and(Sanitizers.BLOCKS).and(Sanitizers.IMAGES).and(Sanitizers.STYLES)
        .and(Sanitizers.LINKS));
  }

  private Map<String, Object> createSale(
    UUID saleId, UUID messageId, String email, String firstName, String lastName,
    String phoneNumber, String country, String companyName, String companySize,
    String companyRole, String title, String message
  ) {
    if (email.isEmpty() || firstName.isEmpty() || lastName.isEmpty() ||
      title.isEmpty() || message.isEmpty()
    ) {
      return Map.of("success", false);
    }
    if (!email.contains("@")) {
      return Map.of("success", false);
    }
    saleDatabaseTable.insertSale(saleId, email, firstName, lastName,
      phoneNumber, country, companyName, companySize, companyRole, title,
      Sale.Status.OPEN.toString(), -1);
    saleMessageDatabaseTable.insertSaleMessage(messageId, "", saleId, email,
      SaleMessageSenderType.USER, message, System.currentTimeMillis());
    return Map.of("success", true);
  }
}
