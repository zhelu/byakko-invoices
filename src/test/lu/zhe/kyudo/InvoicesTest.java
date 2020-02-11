package lu.zhe.kyudo;

import com.google.common.base.*;
import com.google.common.collect.*;
import com.squareup.square.models.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import java.time.*;
import java.util.*;

import static com.google.common.truth.Truth.*;
import static org.mockito.Mockito.*;

/** Unit tests for {@link Invoices}. */
@RunWith(JUnit4.class)
public class InvoicesTest {
  private static final Splitter NEW_LINE_SPLITTER = Splitter.on("\n");

  @Test
  public void emails() {
    Customer johnDoe = mock(Customer.class);
    Customer janeDoe = mock(Customer.class);
    Customer bobSmith = mock(Customer.class);
    Customer aliceEve = mock(Customer.class);
    when(johnDoe.getId()).thenReturn("asdf");
    when(janeDoe.getId()).thenReturn("foobar");
    when(bobSmith.getId()).thenReturn("qux");
    when(aliceEve.getId()).thenReturn("tttt");
    when(johnDoe.getGivenName()).thenReturn("John");
    when(johnDoe.getFamilyName()).thenReturn("Doe");
    when(johnDoe.getEmailAddress()).thenReturn("johndoe@gmail.com");
    when(janeDoe.getGivenName()).thenReturn("Jane");
    when(janeDoe.getFamilyName()).thenReturn("Doe");
    when(janeDoe.getEmailAddress()).thenReturn("janedoe@gmail.com");
    when(bobSmith.getGivenName()).thenReturn("Bob");
    when(bobSmith.getFamilyName()).thenReturn("Smith");
    when(bobSmith.getEmailAddress()).thenReturn("bobsmith@gmail.com");
    when(aliceEve.getGivenName()).thenReturn("Alice");
    when(aliceEve.getFamilyName()).thenReturn("Eve");
    when(aliceEve.getEmailAddress()).thenReturn("aliceeve@gmail.com");
    CustomerGroupInfo regularGroup = mock(CustomerGroupInfo.class);
    when(regularGroup.getName()).thenReturn("REGULAR");
    CustomerGroupInfo associateGroup = mock(CustomerGroupInfo.class);
    when(associateGroup.getName()).thenReturn("ASSOCIATE");
    CustomerGroupInfo studentGroup = mock(CustomerGroupInfo.class);
    when(studentGroup.getName()).thenReturn("STUDENT");
    CustomerGroupInfo memberGroup = mock(CustomerGroupInfo.class);
    when(memberGroup.getName()).thenReturn("MEMBER");

    when(johnDoe.getGroups()).thenReturn(ImmutableList.of(regularGroup));
    when(janeDoe.getGroups()).thenReturn(ImmutableList.of(associateGroup));
    when(bobSmith.getGroups()).thenReturn(ImmutableList.of(studentGroup));
    when(aliceEve.getGroups()).thenReturn(ImmutableList.of(memberGroup));

    Member johnDoeMember = Member.create(johnDoe);
    Member janeDoeMember = Member.create(janeDoe);
    Member bobSmithMember = Member.create(bobSmith);
    Member aliceEveMember = Member.create(aliceEve);

    LocalDate startDate = LocalDate.parse("2020-01-01");
    LocalDate endDate = LocalDate.parse("2020-02-29");
    Invoices.Builder invoicesBuilder = Invoices.builder();
    invoicesBuilder.processMember(johnDoeMember,
        2,
        ImmutableList.of(Payment.create(johnDoeMember, Payment.PaymentType.FIRST_SHOT)),
        ImmutableList.of(),
        ImmutableList.of(),
        startDate,
        endDate);
    invoicesBuilder.processMember(janeDoeMember,
        3,
        ImmutableList.of(),
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.FIRST_SHOT)),
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.DUES)),
        startDate,
        endDate);
    invoicesBuilder.processMember(bobSmithMember,
        2,
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES)),
        startDate,
        endDate);
    invoicesBuilder.processMember(aliceEveMember,
        1,
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES),
            Payment.create(aliceEveMember, Payment.PaymentType.DUES),
            Payment.create(aliceEveMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES)),
        startDate,
        endDate);
    Invoices invoices = invoicesBuilder.build();

    Payment regularPayment = Payment.create(johnDoeMember, Payment.PaymentType.DUES);
    Payment associatePayment = Payment.create(janeDoeMember, Payment.PaymentType.DUES);
    Payment studentPayment = Payment.create(bobSmithMember, Payment.PaymentType.DUES);
    Payment memberPayment = Payment.create(aliceEveMember, Payment.PaymentType.DUES);

    assertThat(invoices.waivers().get(johnDoeMember)).isEmpty();
    assertThat(invoices.waivers().get(janeDoeMember)).isEmpty();
    assertThat(invoices.waivers().get(bobSmithMember)).isEmpty();
    assertThat(invoices.waivers().get(aliceEveMember)).containsExactly(memberPayment,
        memberPayment);

    assertThat(invoices.owed().get(johnDoeMember)).isEmpty();
    assertThat(invoices.owed().get(janeDoeMember)).containsExactly(associatePayment,
        associatePayment);
    assertThat(invoices.owed().get(bobSmithMember)).containsExactly(studentPayment, studentPayment);
    assertThat(invoices.owed().get(aliceEveMember)).isEmpty();

    Map<String, String> emails = invoices
        .emails()
        .values()
        .stream()
        .collect(ImmutableMap.toImmutableMap(Invoices.InvoiceEmail::emailTo,
            Invoices.InvoiceEmail::emailText));

    String johnDoeEmail = emails.get("johndoe@gmail.com");
    String janeDoeEmail = emails.get("janedoe@gmail.com");
    String bobSmithEmail = emails.get("bobsmith@gmail.com");
    String aliceEveEmail = emails.get("aliceeve@gmail.com");

    assertThat(johnDoeEmail).contains("for 2 month(s) between 2020-01-01 and 2020-02-29");
    assertThat(johnDoeEmail).contains("(regular: $40/month)");
    assertThat(johnDoeEmail).contains("dues are: $0");

    assertThat(janeDoeEmail).contains("3 practice(s) between 2020-01-01 and 2020-02-29");
    assertThat(janeDoeEmail).contains("(associate: $15/practice)");
    assertThat(janeDoeEmail).contains("dues are: $30");

    assertThat(bobSmithEmail).contains("for 2 month(s) between 2020-01-01 and 2020-02-29");
    assertThat(bobSmithEmail).contains("(student: $20/month)");
    assertThat(bobSmithEmail).contains("dues are: $40");

    assertThat(aliceEveEmail).contains("for 1 month(s) between 2020-01-01 and 2020-02-29");
    assertThat(aliceEveEmail).contains("(regular: $40/month)");
    assertThat(aliceEveEmail).contains("dues are: $0");
  }

  @Test
  public void waivers() {
    Customer johnDoe = mock(Customer.class);
    Customer janeDoe = mock(Customer.class);
    Customer bobSmith = mock(Customer.class);
    Customer aliceEve = mock(Customer.class);
    when(johnDoe.getId()).thenReturn("asdf");
    when(janeDoe.getId()).thenReturn("foobar");
    when(bobSmith.getId()).thenReturn("qux");
    when(aliceEve.getId()).thenReturn("tttt");
    when(johnDoe.getGivenName()).thenReturn("John");
    when(johnDoe.getFamilyName()).thenReturn("Doe");
    when(johnDoe.getEmailAddress()).thenReturn("johndoe@gmail.com");
    when(janeDoe.getGivenName()).thenReturn("Jane");
    when(janeDoe.getFamilyName()).thenReturn("Doe");
    when(janeDoe.getEmailAddress()).thenReturn("janedoe@gmail.com");
    when(bobSmith.getGivenName()).thenReturn("Bob");
    when(bobSmith.getFamilyName()).thenReturn("Smith");
    when(bobSmith.getEmailAddress()).thenReturn("bobsmith@gmail.com");
    when(aliceEve.getGivenName()).thenReturn("Alice");
    when(aliceEve.getFamilyName()).thenReturn("Eve");
    when(aliceEve.getEmailAddress()).thenReturn("aliceeve@gmail.com");
    CustomerGroupInfo regularGroup = mock(CustomerGroupInfo.class);
    when(regularGroup.getName()).thenReturn("REGULAR");
    CustomerGroupInfo associateGroup = mock(CustomerGroupInfo.class);
    when(associateGroup.getName()).thenReturn("ASSOCIATE");
    CustomerGroupInfo studentGroup = mock(CustomerGroupInfo.class);
    when(studentGroup.getName()).thenReturn("STUDENT");
    CustomerGroupInfo memberGroup = mock(CustomerGroupInfo.class);
    when(memberGroup.getName()).thenReturn("MEMBER");

    when(johnDoe.getGroups()).thenReturn(ImmutableList.of(regularGroup));
    when(janeDoe.getGroups()).thenReturn(ImmutableList.of(associateGroup));
    when(bobSmith.getGroups()).thenReturn(ImmutableList.of(studentGroup));
    when(aliceEve.getGroups()).thenReturn(ImmutableList.of(memberGroup));

    Member johnDoeMember = Member.create(johnDoe);
    Member janeDoeMember = Member.create(janeDoe);
    Member bobSmithMember = Member.create(bobSmith);
    Member aliceEveMember = Member.create(aliceEve);

    LocalDate startDate = LocalDate.parse("2020-01-01");
    LocalDate endDate = LocalDate.parse("2020-02-29");
    Invoices.Builder invoicesBuilder = Invoices.builder();
    invoicesBuilder.processMember(johnDoeMember,
        2,
        ImmutableList.of(Payment.create(johnDoeMember, Payment.PaymentType.DUES),
            Payment.create(johnDoeMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(johnDoeMember, Payment.PaymentType.FIRST_SHOT)),
        ImmutableList.of(),
        startDate,
        endDate);
    invoicesBuilder.processMember(janeDoeMember,
        3,
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.DUES),
            Payment.create(janeDoeMember, Payment.PaymentType.DUES),
            Payment.create(janeDoeMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.FIRST_SHOT)),
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.DUES)),
        startDate,
        endDate);
    invoicesBuilder.processMember(bobSmithMember,
        2,
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES)),
        startDate,
        endDate);
    invoicesBuilder.processMember(aliceEveMember,
        1,
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES),
            Payment.create(aliceEveMember, Payment.PaymentType.DUES),
            Payment.create(aliceEveMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES)),
        startDate,
        endDate);
    Invoices invoices = invoicesBuilder.build();

    assertThat(NEW_LINE_SPLITTER.splitToList(invoices.computeWaivers())).containsExactly(
        "member,amount",
        "John Doe,80",
        "Jane Doe,15",
        "Bob Smith,20",
        "Alice Eve,80");
  }

  @Test
  public void owed() {
    Customer johnDoe = mock(Customer.class);
    Customer janeDoe = mock(Customer.class);
    Customer bobSmith = mock(Customer.class);
    Customer aliceEve = mock(Customer.class);
    when(johnDoe.getId()).thenReturn("asdf");
    when(janeDoe.getId()).thenReturn("foobar");
    when(bobSmith.getId()).thenReturn("qux");
    when(aliceEve.getId()).thenReturn("tttt");
    when(johnDoe.getGivenName()).thenReturn("John");
    when(johnDoe.getFamilyName()).thenReturn("Doe");
    when(johnDoe.getEmailAddress()).thenReturn("johndoe@gmail.com");
    when(janeDoe.getGivenName()).thenReturn("Jane");
    when(janeDoe.getFamilyName()).thenReturn("Doe");
    when(janeDoe.getEmailAddress()).thenReturn("janedoe@gmail.com");
    when(bobSmith.getGivenName()).thenReturn("Bob");
    when(bobSmith.getFamilyName()).thenReturn("Smith");
    when(bobSmith.getEmailAddress()).thenReturn("bobsmith@gmail.com");
    when(aliceEve.getGivenName()).thenReturn("Alice");
    when(aliceEve.getFamilyName()).thenReturn("Eve");
    when(aliceEve.getEmailAddress()).thenReturn("aliceeve@gmail.com");
    CustomerGroupInfo regularGroup = mock(CustomerGroupInfo.class);
    when(regularGroup.getName()).thenReturn("REGULAR");
    CustomerGroupInfo associateGroup = mock(CustomerGroupInfo.class);
    when(associateGroup.getName()).thenReturn("ASSOCIATE");
    CustomerGroupInfo studentGroup = mock(CustomerGroupInfo.class);
    when(studentGroup.getName()).thenReturn("STUDENT");
    CustomerGroupInfo memberGroup = mock(CustomerGroupInfo.class);
    when(memberGroup.getName()).thenReturn("MEMBER");

    when(johnDoe.getGroups()).thenReturn(ImmutableList.of(regularGroup));
    when(janeDoe.getGroups()).thenReturn(ImmutableList.of(associateGroup));
    when(bobSmith.getGroups()).thenReturn(ImmutableList.of(studentGroup));
    when(aliceEve.getGroups()).thenReturn(ImmutableList.of(memberGroup));

    Member johnDoeMember = Member.create(johnDoe);
    Member janeDoeMember = Member.create(janeDoe);
    Member bobSmithMember = Member.create(bobSmith);
    Member aliceEveMember = Member.create(aliceEve);

    LocalDate startDate = LocalDate.parse("2020-01-01");
    LocalDate endDate = LocalDate.parse("2020-02-29");
    Invoices.Builder invoicesBuilder = Invoices.builder();
    invoicesBuilder.processMember(johnDoeMember,
        2,
        ImmutableList.of(Payment.create(johnDoeMember, Payment.PaymentType.DUES),
            Payment.create(johnDoeMember, Payment.PaymentType.DUES)),
        ImmutableList.of(),
        ImmutableList.of(Payment.create(johnDoeMember, Payment.PaymentType.DUES)),
        startDate,
        endDate);
    invoicesBuilder.processMember(janeDoeMember,
        3,
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.DUES),
            Payment.create(janeDoeMember, Payment.PaymentType.DUES),
            Payment.create(janeDoeMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.FIRST_SHOT)),
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.DUES),
            Payment.create(janeDoeMember, Payment.PaymentType.DUES),
            Payment.create(janeDoeMember, Payment.PaymentType.DUES)),
        startDate,
        endDate);
    invoicesBuilder.processMember(bobSmithMember,
        2,
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES)),
        startDate,
        endDate);
    invoicesBuilder.processMember(aliceEveMember,
        2,
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES),
            Payment.create(aliceEveMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES),
            Payment.create(aliceEveMember, Payment.PaymentType.DUES),
            Payment.create(aliceEveMember, Payment.PaymentType.DUES)),
        startDate,
        endDate);

    Invoices invoices = invoicesBuilder.build();
    assertThat(NEW_LINE_SPLITTER.splitToList(invoices.computeOwed())).containsExactly(
        "member,amount",
        "John Doe,40",
        "Jane Doe,15",
        "Bob Smith,40",
        "Alice Eve,80");
  }
}
