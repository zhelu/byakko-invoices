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

  public MemberDatabase getMembers() throws IOException, ApiException {
    MemberDatabase.Builder result = MemberDatabase.builder();
    String cursor = null;
    do {
      ListCustomersResponse response = client.getCustomersApi().listCustomers(cursor, null, null);
      cursor = response.getCursor();
      response.getCustomers().stream().map(Member::create).forEach(result::addMember);
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
      response
          .getOrders()
          .stream()
          .filter(o -> o.getCustomerId() != null)
          .forEach(o -> result.putAll(database.idToMember().get(o.getCustomerId()),
              getPayment(o, database.idToMember().get(o.getCustomerId()))));
      cursor = response.getCursor();
    } while (cursor != null);
    return result.build();
  }
}
