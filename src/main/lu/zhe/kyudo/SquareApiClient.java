package lu.zhe.kyudo;

import com.google.common.annotations.*;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.squareup.square.*;
import com.squareup.square.exceptions.*;
import com.squareup.square.models.*;

import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/** Client to integrating with the Square API. */
public class SquareApiClient {
  private static final String AUTOINVOICE_GROUP = "AutoInvoice";
  private final SquareClient client;

  @VisibleForTesting
  SquareApiClient(SquareClient client) {
    this.client = client;
  }

  public static SquareApiClient create(String accessToken) {
    return new SquareApiClient(new SquareClient.Builder()
        .environment(Environment.PRODUCTION)
        .accessToken(accessToken)
        .build());
  }

  private static List<Payment> getPayment(Order order, Member member) {
    if (order.getLineItems() == null || order.getLineItems().isEmpty()) {
      return ImmutableList.of();
    }
    OrderLineItem item = order.getLineItems().get(0);
    final Payment.PaymentType type;
    if (Ascii.toUpperCase(item.getName()).contains("FIRST SHOT")) {
      type = Payment.PaymentType.FIRST_SHOT;
    } else if (Ascii.toUpperCase(item.getName()).contains("DUES")) {
      type = Payment.PaymentType.DUES;
    } else {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(Collections.nCopies(Integer.parseInt(item.getQuantity()),
        Payment.create(member, type)));
  }

  public ImmutableMap<String, String> getMemberGroups() throws IOException, ApiException {
    ImmutableMap.Builder<String, String> groups = ImmutableMap.builder();
    String cursor = null;
    do {
      ListCustomerGroupsResponse response =
          client.getCustomerGroupsApi().listCustomerGroups(cursor);
      for (CustomerGroup group : response.getGroups()) {
        groups.put(group.getId(), group.getName());
      }
      cursor = response.getCursor();
    } while (cursor != null);
    return groups.build();
  }

  public MemberDatabase getMembers() throws IOException, ApiException {
    Map<String, String> groups = getMemberGroups();
    MemberDatabase.Builder result = MemberDatabase.builder();
    String cursor = null;
    do {
      ListCustomersResponse response = client.getCustomersApi().listCustomers(cursor, null, null);
      cursor = response.getCursor();
      if (response.getCustomers() != null) {
        response
            .getCustomers()
            .stream()
            .map(c -> Member.create(c, groups))
            .forEach(result::addMember);
      }
    } while (cursor != null);
    return result.build();
  }

  public ListMultimap<Member, Payment> getPayments(
      MemberDatabase database, String locationId, LocalDate startDate,
      LocalDate endDateInclusive) throws IOException, ApiException {
    SearchOrdersDateTimeFilter dateTimeFilter = new SearchOrdersDateTimeFilter(null,
        null,
        new TimeRange(startDate
            .atStartOfDay(ZoneId.of("America/New_York"))
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            endDateInclusive
                .plusDays(1)
                .atStartOfDay(ZoneId.of("America/New_York"))
                .minusSeconds(1)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
    SearchOrdersFilter filter =
        new SearchOrdersFilter(new SearchOrdersStateFilter(ImmutableList.of("COMPLETED")),
            dateTimeFilter,
            null,
            null,
            null);
    SearchOrdersQuery query =
        new SearchOrdersQuery(filter, new SearchOrdersSort("CLOSED_AT", null));
    ImmutableListMultimap.Builder<Member, Payment> result = ImmutableListMultimap.builder();
    String cursor = null;
    do {
      SearchOrdersRequest request =
          new SearchOrdersRequest(ImmutableList.of(locationId), cursor, query, null, false);
      SearchOrdersResponse response = client.getOrdersApi().searchOrders(request);
      if (response.getOrders() != null) {
        response
            .getOrders()
            .stream()
            .filter(o -> o.getCustomerId() != null)
            .forEach(o -> result.putAll(database.idToMember().get(o.getCustomerId()),
                getPayment(o, database.idToMember().get(o.getCustomerId()))));
      }
      cursor = response.getCursor();
    } while (cursor != null);
    return result.build();
  }

  public void cancelOutstandingInvoicesForAutoInvoicedCustomers(
      MemberDatabase memberDatabase, String locationId) throws IOException, ApiException {
    for (Map.Entry<String, Member> entry : memberDatabase.idToMember().entrySet()) {
      if (entry
          .getValue()
          .customer()
          .getGroupIds()
          .stream()
          .noneMatch(g -> AUTOINVOICE_GROUP.equals(g))) {
        continue;
      }
      String cursor = null;
      do {
        SearchInvoicesResponse searchResponse = client
            .getInvoicesApi()
            .searchInvoices(new SearchInvoicesRequest.Builder(new InvoiceQuery.Builder(new InvoiceFilter.Builder(
                ImmutableList.of(locationId)).customerIds(ImmutableList.of(entry.getKey())).build())
                .sort(new InvoiceSort.Builder("INVOICE_SORT_DATE").order("DESC").build())
                .build()).cursor(cursor).build());
        cursor = searchResponse.getCursor();
        if (searchResponse.getErrors() != null && !searchResponse.getErrors().isEmpty()) {
          searchResponse
              .getErrors()
              .forEach(error -> System.err.println(error.getCategory() + " " + error.getDetail()));
          continue;
        }
        if (searchResponse.getInvoices() == null) {
          continue;
        }
        Instant earliestTime = Instant.now().minus(Duration.ofDays(365));
        for (Invoice invoice : searchResponse.getInvoices()) {
          if (Instant.parse(invoice.getCreatedAt()).isBefore(earliestTime)) {
            // Earlier than time boundary
            break;
          }
          if (!invoice.getStatus().equals("UNPAID")) {
            continue;
          }
          String invoiceId = invoice.getId();
          int version = invoice.getVersion();
          CancelInvoiceResponse cancelInvoiceResponse = client
              .getInvoicesApi()
              .cancelInvoice(invoiceId, new CancelInvoiceRequest.Builder(version).build());
          if (cancelInvoiceResponse.getErrors() != null &&
              !cancelInvoiceResponse.getErrors().isEmpty()) {
            cancelInvoiceResponse
                .getErrors()
                .forEach(error -> System.err.println(
                    error.getCategory() + " " + error.getDetail()));
          }
        }
      } while (cursor != null);
    }
  }

  public void createAndSendInvoices(
      List<Invoices.InvoiceEmail> invoices, String locationId) throws IOException, ApiException {
    Map<String, String> groups = getMemberGroups();
    for (Invoices.InvoiceEmail entry : invoices) {
      if (entry
          .member()
          .customer()
          .getGroupIds()
          .stream()
          .map(groups::get)
          .noneMatch(AUTOINVOICE_GROUP::equals)) {
        continue;
      }

      if (entry.owed().isEmpty()) {
        continue;
      }

      Order order = new Order.Builder(locationId)
          .customerId(entry.member().customer().getId())
          .lineItems(ImmutableList.of(new OrderLineItem.Builder(String.valueOf(entry.owed().size()))
              .name("Dues")
              .basePriceMoney(new Money(Long.valueOf(entry.owed().iterator().next().amount() * 100),
                  "USD"))
              .build()))
          .build();
      CreateOrderResponse orderResponse = client
          .getOrdersApi()
          .createOrder(new CreateOrderRequest(order, Instant.now().toString()));

      if (orderResponse.getErrors() != null && !orderResponse.getErrors().isEmpty()) {
        orderResponse
            .getErrors()
            .forEach(error -> System.err.println(error.getCategory() + " " + error.getDetail()));
        continue;
      }

      CreateInvoiceResponse invoiceResponse = client
          .getInvoicesApi()
          .createInvoice(new CreateInvoiceRequest.Builder(new Invoice.Builder()
              .orderId(orderResponse.getOrder().getId())
              .locationId(locationId)
              .primaryRecipient(new InvoiceRecipient.Builder()
                  .customerId(entry.member().customer().getId())
                  .build())
              .paymentRequests(ImmutableList.of(new InvoicePaymentRequest.Builder()
                  .requestMethod("EMAIL")
                  .requestType("BALANCE")
                  .dueDate(LocalDate.now().toString())
                  .tippingEnabled(false)
                  .build()))
              .acceptedPaymentMethods(new InvoiceAcceptedPaymentMethods.Builder()
                  .card(true)
                  .bankAccount(true)
                  .build())
              .build()).idempotencyKey(Instant.now().toString()).build());

      if (invoiceResponse.getErrors() != null && !invoiceResponse.getErrors().isEmpty()) {
        invoiceResponse
            .getErrors()
            .forEach(error -> System.err.println(error.getCategory() + " " + error.getDetail()));
        continue;
      }

      PublishInvoiceResponse publishResponse = client
          .getInvoicesApi()
          .publishInvoice(invoiceResponse.getInvoice().getId(),
              new PublishInvoiceRequest(0, Instant.now().toString()));

      if (publishResponse.getErrors() != null && !publishResponse.getErrors().isEmpty()) {
        publishResponse
            .getErrors()
            .forEach(error -> System.err.println(error.getCategory() + " " + error.getDetail()));
        continue;
      }
    }
  }
}
