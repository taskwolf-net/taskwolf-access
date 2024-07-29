package net.taskwolf.access.stripe;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.stripe.StripeClient;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentListParams;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.organization.OrganizationDatabaseTable;
import net.taskwolf.core.stripe.StripeConfiguration;
import net.taskwolf.core.stripe.StripeDatabaseTable;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class StripePaymentRequestController extends StripeController {
  private final StripeClient stripeClient;

  private StripePaymentRequestController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    StripeConfiguration stripeConfiguration,
    StripeDatabaseTable stripeDatabaseTable, StripeClient stripeClient,
    UserTargetDatabaseTable targetDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, stripeConfiguration, stripeDatabaseTable,
      targetDatabaseTable, organizationDatabaseTable);
    this.stripeClient = stripeClient;
  }

  @RequestMapping(path = "/payments/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findPayments(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performStripeOperation(findUserId(request), account -> new Thread(() ->
        futureResponse.complete(findPayments(account.accountId()))).start(),
      () -> futureResponse.complete(Map.of("payments", Lists.newArrayList())));
    return futureResponse;
  }

  private Map<String, Object> findPayments(String accountId) {
    try {
      var parameters = PaymentIntentListParams.builder()
        .setCustomer(accountId).build();
      var payments = stripeClient.paymentIntents().list(parameters);
      var information = Lists.newArrayList();
      for (var payment : payments.getData()) {
        var invoice = stripeClient.invoices().retrieve(payment.getInvoice());
        information.add(assemblyPaymentInformation(payment, invoice));
      }
      return Map.of("payments", information);
    } catch (Exception exception) {
      exception.printStackTrace();
      return Maps.newHashMap();
    }
  }

  private Map<String, Object> assemblyPaymentInformation(
    PaymentIntent payment, Invoice invoice
  ) throws Exception {
    var information = Maps.<String, Object>newHashMap();
    information.put("time", formatTime(payment.getCreated() * 1000));
    information.put("amount", payment.getAmount() / 100D);
    information.put("method", stripeClient.paymentMethods()
      .retrieve(payment.getPaymentMethod()).getType());
    information.put("status", payment.getStatus());
    information.put("invoice", invoice.getInvoicePdf());
    return information;
  }

  private String formatTime(long time) {
    var calendar = Calendar.getInstance();
    calendar.setTimeInMillis(time);
    return new SimpleDateFormat("dd.MM.yyyy").format(calendar.getTime());
  }
}
