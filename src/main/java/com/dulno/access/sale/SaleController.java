package com.dulno.access.sale;

import com.dulno.core.sale.Sale;
import com.dulno.core.sale.SaleMessageSenderType;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.sale.SaleDatabaseTable;
import com.dulno.core.sale.SaleMessageDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

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
    var body = DulnoRequestBody.of(payload, response);
    return saleDatabaseTable.generateAvailableSaleId()
      .thenCompose(saleId -> saleMessageDatabaseTable.generateAvailableMessageId()
        .thenApply(messageId -> createSale(saleId, messageId,
          body.getString("email"), body.getString("firstName"),
          body.getString("lastName"), body.getString("phoneNumber"),
          body.getString("country"), body.getString("companyName"),
          body.getString("companySize"), body.getString("companyRole"),
          body.getString("title"), body.getString("message"))));
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
