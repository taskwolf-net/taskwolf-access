package net.taskwolf.access.sale;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.question.QuestionDatabaseTable;
import net.taskwolf.core.question.QuestionMessageDatabaseTable;
import net.taskwolf.core.sale.SaleDatabaseTable;
import net.taskwolf.core.sale.SaleMessageDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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
  public void createSale(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    saleMessageDatabaseTable.generateAvailableMessageId()
      .thenAccept(messageId -> createSale(messageId, body.getString("email"),
        body.getString("firstName"), body.getString("lastName"),
        body.getString("phoneNumber"), body.getString("country"),
        body.getString("companyName"), body.getString("companySize"),
        body.getString("companyRole"), body.getString("title"),
        body.getString("question")));
}

  private void createSale(
    UUID messageId, String email, String firstName, String lastName,
    String phoneNumber, String country, String companyName, String companySize,
    String companyRole, String title, String message
  ) {
    saleDatabaseTable.insertSale(messageId, email, firstName, lastName,
      phoneNumber, country, companyName, companySize, companyRole, title, -1,
      Lists.newArrayList());
    saleMessageDatabaseTable.insertSaleMessage(messageId, email, message,
      System.currentTimeMillis());
  }
}
