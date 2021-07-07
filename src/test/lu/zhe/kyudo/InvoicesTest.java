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

    when(johnDoe.getGroupIds()).thenReturn(ImmutableList.of("regular"));
    when(janeDoe.getGroupIds()).thenReturn(ImmutableList.of("associate"));
    when(bobSmith.getGroupIds()).thenReturn(ImmutableList.of("student"));
    when(aliceEve.getGroupIds()).thenReturn(ImmutableList.of("member"));

    Map<String, String> groups = ImmutableMap.of("regular",
        "REGULAR",
        "associate",
        "ASSOCIATE",
        "student",
        "STUDENT",
        "member",
        "MEMBER");

    Member johnDoeMember = Member.create(johnDoe, groups);
    Member janeDoeMember = Member.create(janeDoe, groups);
    Member bobSmithMember = Member.create(bobSmith, groups);
    Member aliceEveMember = Member.create(aliceEve, groups);

    Invoices.Builder invoicesBuilder = Invoices.builder();
    invoicesBuilder.processMember(johnDoeMember,
        2,
        ImmutableList.of(Payment.create(johnDoeMember, Payment.PaymentType.DUES),
            Payment.create(johnDoeMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(johnDoeMember, Payment.PaymentType.FIRST_SHOT)),
        ImmutableList.of());
    invoicesBuilder.processMember(janeDoeMember, 3,
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.DUES),
            Payment.create(janeDoeMember, Payment.PaymentType.DUES),
            Payment.create(janeDoeMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.FIRST_SHOT)),
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.DUES)));
    invoicesBuilder.processMember(bobSmithMember,
        2,
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES)));
    invoicesBuilder.processMember(aliceEveMember,
        1,
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES),
            Payment.create(aliceEveMember, Payment.PaymentType.DUES),
            Payment.create(aliceEveMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES)));
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

    when(johnDoe.getGroupIds()).thenReturn(ImmutableList.of("regular"));
    when(janeDoe.getGroupIds()).thenReturn(ImmutableList.of("associate"));
    when(bobSmith.getGroupIds()).thenReturn(ImmutableList.of("student"));
    when(aliceEve.getGroupIds()).thenReturn(ImmutableList.of("member"));
    Map<String, String> groups = ImmutableMap.of("regular",
        "REGULAR",
        "associate",
        "ASSOCIATE",
        "student",
        "STUDENT",
        "member",
        "MEMBER");
    Member johnDoeMember = Member.create(johnDoe, groups);
    Member janeDoeMember = Member.create(janeDoe, groups);
    Member bobSmithMember = Member.create(bobSmith, groups);
    Member aliceEveMember = Member.create(aliceEve, groups);

    LocalDate startDate = LocalDate.parse("2020-01-01");
    LocalDate endDate = LocalDate.parse("2020-02-29");
    Invoices.Builder invoicesBuilder = Invoices.builder();
    invoicesBuilder.processMember(johnDoeMember,
        2,
        ImmutableList.of(Payment.create(johnDoeMember, Payment.PaymentType.DUES),
            Payment.create(johnDoeMember, Payment.PaymentType.DUES)),
        ImmutableList.of(),
        ImmutableList.of(Payment.create(johnDoeMember, Payment.PaymentType.DUES)));
    invoicesBuilder.processMember(janeDoeMember,
        3,
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.DUES),
            Payment.create(janeDoeMember, Payment.PaymentType.DUES),
            Payment.create(janeDoeMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.FIRST_SHOT)),
        ImmutableList.of(Payment.create(janeDoeMember, Payment.PaymentType.DUES),
            Payment.create(janeDoeMember, Payment.PaymentType.DUES),
            Payment.create(janeDoeMember, Payment.PaymentType.DUES)));
    invoicesBuilder.processMember(bobSmithMember,
        2,
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES),
            Payment.create(bobSmithMember, Payment.PaymentType.DUES)));
    invoicesBuilder.processMember(aliceEveMember,
        2,
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES),
            Payment.create(aliceEveMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES)),
        ImmutableList.of(Payment.create(aliceEveMember, Payment.PaymentType.DUES),
            Payment.create(aliceEveMember, Payment.PaymentType.DUES),
            Payment.create(aliceEveMember, Payment.PaymentType.DUES)));

    Invoices invoices = invoicesBuilder.build();
    assertThat(NEW_LINE_SPLITTER.splitToList(invoices.computeOwed())).containsExactly(
        "member,amount",
        "John Doe,40",
        "Jane Doe,15",
        "Bob Smith,40",
        "Alice Eve,80");
  }
}
