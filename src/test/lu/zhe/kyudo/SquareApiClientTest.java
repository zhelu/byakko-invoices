package lu.zhe.kyudo;

import com.google.common.collect.*;
import com.squareup.square.*;
import com.squareup.square.api.*;
import com.squareup.square.exceptions.*;
import com.squareup.square.models.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.io.*;
import java.time.*;

import static com.google.common.truth.Truth.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for {@link SquareApiClient}. */
@RunWith(JUnit4.class)
public class SquareApiClientTest {
  private final SquareClient squareClient = mock(SquareClient.class);
  private final SquareApiClient client = new SquareApiClient(squareClient);

  @Test
  public void getMembers() throws IOException, ApiException {
    CustomersApi api = mock(CustomersApi.class);

    Customer johnDoe = mock(Customer.class);
    Customer janeDoe = mock(Customer.class);
    Customer bobSmith = mock(Customer.class);
    when(johnDoe.getId()).thenReturn("asdf");
    when(janeDoe.getId()).thenReturn("foobar");
    when(bobSmith.getId()).thenReturn("qux");
    when(johnDoe.getGivenName()).thenReturn("John");
    when(johnDoe.getFamilyName()).thenReturn("Doe");
    when(janeDoe.getGivenName()).thenReturn("Jane");
    when(janeDoe.getFamilyName()).thenReturn("Doe");
    when(bobSmith.getGivenName()).thenReturn("Bob");
    when(bobSmith.getFamilyName()).thenReturn("Smith");
    CustomerGroupInfo group = mock(CustomerGroupInfo.class);
    when(group.getName()).thenReturn("MEMBER");
    CustomerGroupInfo groupAssociate = mock(CustomerGroupInfo.class);
    when(groupAssociate.getName()).thenReturn("ASSOCIATE");

    when(johnDoe.getGroups()).thenReturn(ImmutableList.of(group));
    when(janeDoe.getGroups()).thenReturn(ImmutableList.of(group));
    when(bobSmith.getGroups()).thenReturn(ImmutableList.of(groupAssociate));

    when(squareClient.getCustomersApi()).thenReturn(api);
    when(api.listCustomers(any(), any(), any())).thenAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocationOnMock) {
        String cursor = invocationOnMock.getArgument(0);
        if (cursor == null) {
          return new ListCustomersResponse(ImmutableList.of(),
              ImmutableList.of(johnDoe, janeDoe),
              "foo");
        } else if (cursor.equals("foo")) {
          return new ListCustomersResponse(ImmutableList.of(), ImmutableList.of(bobSmith), null);
        }
        throw new IllegalStateException("Unhandled cursor state");
      }
    });

    MemberDatabase database = client.getMembers();

    assertThat(database.idToMember().keySet()).containsExactly("asdf", "foobar", "qux");
    assertThat(database.nameToMember().keySet()).containsExactly("John Doe",
        "Jane Doe",
        "Bob Smith");
    assertThat(database.idToMember().get("asdf").customer()).isSameInstanceAs(johnDoe);
    assertThat(database.idToMember().get("foobar").customer()).isSameInstanceAs(janeDoe);
    assertThat(database.idToMember().get("qux").customer()).isSameInstanceAs(bobSmith);

    assertThat(database.nameToMember().get("John Doe").customer()).isSameInstanceAs(johnDoe);
    assertThat(database.nameToMember().get("Jane Doe").customer()).isSameInstanceAs(janeDoe);
    assertThat(database.nameToMember().get("Bob Smith").customer()).isSameInstanceAs(bobSmith);

    assertThat(database.nameToMember().get("John Doe").type()).isEqualTo(MemberType.MEMBER);
    assertThat(database.nameToMember().get("Jane Doe").type()).isEqualTo(MemberType.MEMBER);
    assertThat(database.nameToMember().get("Bob Smith").type()).isEqualTo(MemberType.ASSOCIATE);

    assertThat(database.nameToMember().get("John Doe").name()).isEqualTo("John Doe");
    assertThat(database.nameToMember().get("Jane Doe").name()).isEqualTo("Jane Doe");
    assertThat(database.nameToMember().get("Bob Smith").name()).isEqualTo("Bob Smith");
  }

  @Test
  public void getPayments() throws IOException, ApiException {
    Customer johnDoe = mock(Customer.class);
    Customer janeSmith = mock(Customer.class);
    when(johnDoe.getId()).thenReturn("asdf");
    when(janeSmith.getId()).thenReturn("foobar");
    when(johnDoe.getGivenName()).thenReturn("John");
    when(johnDoe.getFamilyName()).thenReturn("Doe");
    when(janeSmith.getGivenName()).thenReturn("Jane");
    when(janeSmith.getFamilyName()).thenReturn("Smith");
    CustomerGroupInfo group = mock(CustomerGroupInfo.class);
    when(group.getName()).thenReturn("MEMBER");
    when(johnDoe.getGroups()).thenReturn(ImmutableList.of(group));
    when(janeSmith.getGroups()).thenReturn(ImmutableList.of(group));

    MemberDatabase.Builder builder = MemberDatabase.builder();

    Member johnDoeMember = Member.create(johnDoe);
    Member janeSmithMember = Member.create(janeSmith);
    builder.addMember(johnDoeMember);
    builder.addMember(janeSmithMember);
    MemberDatabase database = builder.build();

    OrdersApi api = mock(OrdersApi.class);

    OrderLineItem firstShot = mock(OrderLineItem.class);
    when(firstShot.getName()).thenReturn("First Shot");
    when(firstShot.getQuantity()).thenReturn("1");
    OrderLineItem dues = mock(OrderLineItem.class);
    when(dues.getName()).thenReturn("Dues");
    when(dues.getQuantity()).thenReturn("2");
    OrderLineItem other = mock(OrderLineItem.class);
    when(other.getName()).thenReturn("asdf");
    when(other.getQuantity()).thenReturn("1");

    when(api.searchOrders(any())).thenAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocationOnMock) {
        SearchOrdersRequest request = invocationOnMock.getArgument(0);
        if (request.getReturnEntries()) {
          throw new IllegalStateException("returnEntries expected to be false");
        }
        if (request.getLimit() != null) {
          throw new IllegalStateException("limit expected to be null");
        }
        SearchOrdersQuery query = request.getQuery();
        if (!query.getSort().getSortField().equals("CLOSED_AT")) {
          throw new IllegalStateException("incorrect sort field");
        }
        if (query.getSort().getSortOrder() != null) {
          throw new IllegalStateException("null sortOrder expected");
        }
        if (!query.getFilter().getStateFilter().getStates().equals(ImmutableList.of("COMPLETED"))) {
          throw new IllegalStateException("COMPLETED STATE expected");
        }
        TimeRange timeRange = query.getFilter().getDateTimeFilter().getClosedAt();
        if (!timeRange.getStartAt().equals("2020-01-01T00:00:00-05:00")) {
          throw new IllegalStateException("Incorrect start time");
        }
        if (!timeRange.getEndAt().equals("2020-06-30T23:59:59-04:00")) {
          throw new IllegalStateException("Incorrect end time");
        }
        if (!request.getLocationIds().equals(ImmutableList.of("foobarqux"))) {
          throw new IllegalStateException("Incorrect location ids");
        }
        String cursor = request.getCursor();
        if (cursor == null) {
          Order johnFirstShot = mock(Order.class);
          when(johnFirstShot.getCustomerId()).thenReturn("asdf");
          when(johnFirstShot.getLineItems()).thenReturn(ImmutableList.of(firstShot));
          Order janeDues = mock(Order.class);
          when(janeDues.getCustomerId()).thenReturn("foobar");
          when(janeDues.getLineItems()).thenReturn(ImmutableList.of(dues));
          Order nullIdOrder = mock(Order.class);
          Order unknownPaymentType = mock(Order.class);
          when(unknownPaymentType.getCustomerId()).thenReturn("asdf");
          when(unknownPaymentType.getLineItems()).thenReturn(ImmutableList.of(other));
          return new SearchOrdersResponse(ImmutableList.of(),
              ImmutableList.of(johnFirstShot, janeDues, nullIdOrder, unknownPaymentType),
              "bar",
              ImmutableList.of());
        } else if (cursor.equals("bar")) {
          Order johnDues = mock(Order.class);
          when(johnDues.getCustomerId()).thenReturn("asdf");
          when(johnDues.getLineItems()).thenReturn(ImmutableList.of(dues));
          return new SearchOrdersResponse(ImmutableList.of(),
              ImmutableList.of(johnDues),
              null,
              ImmutableList.of());
        }
        throw new IllegalStateException("unhandled cursor state");
      }
    });

    when(squareClient.getOrdersApi()).thenReturn(api);

    ListMultimap<Member, Payment> result = client.getPayments(database,
        "foobarqux",
        LocalDate.parse("2020-01-01"),
        LocalDate.parse("2020-06-30"));

    assertThat(result.keySet()).containsExactly(johnDoeMember, janeSmithMember);
    assertThat(result.get(johnDoeMember)).containsExactly(Payment.create(johnDoeMember,
        Payment.PaymentType.DUES),
        Payment.create(johnDoeMember, Payment.PaymentType.DUES),
        Payment.create(johnDoeMember, Payment.PaymentType.FIRST_SHOT));
    assertThat(result.get(janeSmithMember)).containsExactly(Payment.create(janeSmithMember,
        Payment.PaymentType.DUES), Payment.create(janeSmithMember, Payment.PaymentType.DUES));
  }
}
