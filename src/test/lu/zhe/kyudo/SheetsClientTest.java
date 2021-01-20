package lu.zhe.kyudo;

import com.google.api.services.sheets.v4.*;
import com.google.api.services.sheets.v4.model.*;
import com.google.common.collect.*;
import com.squareup.square.models.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;
import org.mockito.*;

import java.io.*;
import java.time.*;
import java.util.*;

import static com.google.common.truth.Truth.*;
import static org.mockito.Mockito.*;

/** Unit tests for {@link SheetsClient}. */
@RunWith(JUnit4.class)
public class SheetsClientTest {
  private Member johnDoeMember;
  private Member janeDoeMember;
  private Member bobSmithMember;
  private Member aliceEveMember;


  private MemberDatabase memberDatabase;

  @Before
  public void setup() {
    Customer johnDoe = mock(Customer.class);
    Customer janeDoe = mock(Customer.class);
    Customer bobSmith = mock(Customer.class);
    Customer aliceEve = mock(Customer.class);

    when(johnDoe.getId()).thenReturn("asdf");
    when(janeDoe.getId()).thenReturn("foobar");
    when(bobSmith.getId()).thenReturn("qux");
    when(aliceEve.getId()).thenReturn("tttt");
    when(johnDoe.getEmailAddress()).thenReturn("john.doe@gmail.com");
    when(janeDoe.getEmailAddress()).thenReturn("jane.doe@gmail.com");
    when(bobSmith.getEmailAddress()).thenReturn("bob.smith@gmail.com");
    when(aliceEve.getEmailAddress()).thenReturn("alice.eve@gmail.com");
    when(johnDoe.getGivenName()).thenReturn("John");
    when(johnDoe.getFamilyName()).thenReturn("Doe");
    when(janeDoe.getGivenName()).thenReturn("Jane");
    when(janeDoe.getFamilyName()).thenReturn("Doe");
    when(bobSmith.getGivenName()).thenReturn("Bob");
    when(bobSmith.getFamilyName()).thenReturn("Smith");
    when(aliceEve.getGivenName()).thenReturn("Alice");
    when(aliceEve.getFamilyName()).thenReturn("Eve");
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

    MemberDatabase.Builder builder = MemberDatabase.builder();

    johnDoeMember = Member.create(johnDoe);
    janeDoeMember = Member.create(janeDoe);
    bobSmithMember = Member.create(bobSmith);
    aliceEveMember = Member.create(aliceEve);
    builder.addMember(johnDoeMember);
    builder.addMember(janeDoeMember);
    builder.addMember(bobSmithMember);
    builder.addMember(aliceEveMember);
    memberDatabase = builder.build();
  }

  @Test
  public void parseAttendance() throws IOException {
    Sheets sheets = mock(Sheets.class);
    Sheets.Spreadsheets spreadsheets = mock(Sheets.Spreadsheets.class);
    Sheets.Spreadsheets.Values values = mock(Sheets.Spreadsheets.Values.class);
    Sheets.Spreadsheets.Values.Get valuesGet = mock(Sheets.Spreadsheets.Values.Get.class);
    ValueRange valueRange = new ValueRange();
    List<List<Object>> responses =
        ImmutableList.of(ImmutableList.of("", "", "John Doe, Jane Doe", "", "1/20/2018"),
            ImmutableList.of("", "", "John Doe, Jane Doe", "", "2/01/2018"),
            ImmutableList.of("", "", "John Doe, Jane Doe, Bob Smith", "", "2/25/2018"),
            ImmutableList.of("", "", "John Doe, Alice Eve", "", "3/20/2018"),
            ImmutableList.of("", "", "John Doe, Jane Doe", "", "4/01/2018"));
    valueRange.setValues(responses);

    when(sheets.spreadsheets()).thenReturn(spreadsheets);
    when(spreadsheets.values()).thenReturn(values);
    when(values.get(eq("foo"), eq("Form Responses 1!A2:E"))).thenReturn(valuesGet);
    when(valuesGet.execute()).thenReturn(valueRange);

    SheetsClient client = new SheetsClient(sheets);

    ImmutableMultiset<Member> result = client.readAttendanceSheet(memberDatabase,
        "foo",
        LocalDate.parse("2018-02-01"),
        LocalDate.parse("2018-03-31"));

    assertThat(result).containsExactly(johnDoeMember,
        johnDoeMember,
        janeDoeMember,
        janeDoeMember,
        bobSmithMember,
        aliceEveMember,
        aliceEveMember);
  }

  @Test
  public void readAccounts() throws IOException {
    Sheets sheets = mock(Sheets.class);
    Sheets.Spreadsheets spreadsheets = mock(Sheets.Spreadsheets.class);
    Sheets.Spreadsheets.Values values = mock(Sheets.Spreadsheets.Values.class);
    Sheets.Spreadsheets.Values.Get valuesGet = mock(Sheets.Spreadsheets.Values.Get.class);
    ValueRange valueRange = new ValueRange();
    List<List<Object>> responses =
        ImmutableList.of(ImmutableList.of("John Doe", "", "", "", "", "", "", "40"),
            ImmutableList.of("Jane Doe", "", "", "", "", "", "", "-120")); // 8 * -15
    valueRange.setValues(responses);

    when(sheets.spreadsheets()).thenReturn(spreadsheets);
    when(spreadsheets.values()).thenReturn(values);
    when(values.get(eq("foo"), eq("2021-01 to 2021-02!A2:H"))).thenReturn(valuesGet);
    when(valuesGet.execute()).thenReturn(valueRange);

    SheetsClient client = new SheetsClient(sheets);

    SheetsClient.Accounts accounts =
        client.readAccounts(memberDatabase, "foo", LocalDate.parse("2021-04-30"));

    assertThat(accounts.waivers()).containsExactly(johnDoeMember,
        Payment.create(johnDoeMember, Payment.PaymentType.DUES));
    assertThat(accounts.owed().asMap()).containsExactly(janeDoeMember,
        ImmutableList.copyOf(Collections.nCopies(8,
            Payment.create(janeDoeMember, Payment.PaymentType.DUES))));
  }

  @Test
  public void writeInvoices() throws IOException {
    Sheets sheets = mock(Sheets.class);
    Sheets.Spreadsheets spreadsheets = mock(Sheets.Spreadsheets.class);
    Sheets.Spreadsheets.Values values = mock(Sheets.Spreadsheets.Values.class);
    Sheets.Spreadsheets.Values.Append valuesAppend = mock(Sheets.Spreadsheets.Values.Append.class);

    Sheets.Spreadsheets.BatchUpdate batchUpdate = mock(Sheets.Spreadsheets.BatchUpdate.class);

    when(sheets.spreadsheets()).thenReturn(spreadsheets);
    when(spreadsheets.values()).thenReturn(values);
    when(spreadsheets.batchUpdate(eq("foo"), ArgumentMatchers.argThat(req -> {
      int size = req.getRequests().size();
      if (size != 1) {
        return false;
      }
      return req
          .getRequests()
          .get(0)
          .getAddSheet()
          .getProperties()
          .getTitle()
          .equals("2021-01 to 2021-02");
    }))).thenReturn(batchUpdate);
    List<List<Object>> expected = ImmutableList.of(SheetsClient.INVOICE_SHEET_HEADERS,
        ImmutableList.of("Alice Eve", "regular", 0, 2, 40, "= D:D * E:E", 40, "= C:C - F:F + G:G"),
        ImmutableList.of("Bob Smith", "student", 40, 0, 20, "= D:D * E:E", 80, "= C:C - F:F + G:G"),
        ImmutableList.of("Jane Doe",
            "associate",
            -30,
            3,
            15,
            "= D:D * E:E",
            15,
            "= C:C - F:F + G:G"),
        ImmutableList.of("John Doe",
            "regular",
            -80,
            2,
            40,
            "= D:D * E:E",
            80,
            "= C:C - F:F + G:G"));
    when(values.append(eq("foo"),
        eq("2021-01 to 2021-02!A1"),
        ArgumentMatchers.argThat(req -> req
            .getValues()
            .equals(expected)))).thenReturn(valuesAppend);

    when(valuesAppend.setValueInputOption(eq("USER_ENTERED"))).thenReturn(valuesAppend);
    when(valuesAppend.setInsertDataOption(eq("INSERT_ROWS"))).thenReturn(valuesAppend);

    SheetsClient client = new SheetsClient(sheets);
    Invoices.Builder invoiceBuilder = Invoices.builder();
    invoiceBuilder.paymentsBuilder().put(johnDoeMember, 80);
    invoiceBuilder.paymentsBuilder().put(janeDoeMember, 15);
    invoiceBuilder.paymentsBuilder().put(bobSmithMember, 80);
    invoiceBuilder.paymentsBuilder().put(aliceEveMember, 40);
    invoiceBuilder.attendanceCountBuilder().put(johnDoeMember, 2);
    invoiceBuilder.attendanceCountBuilder().put(janeDoeMember, 3);
    invoiceBuilder.attendanceCountBuilder().put(aliceEveMember, 2);
    SheetsClient.Accounts.Builder accountsBuilder = SheetsClient.Accounts.builder();
    accountsBuilder
        .owedBuilder()
        .putAll(johnDoeMember,
            ImmutableList.copyOf(Collections.nCopies(2,
                Payment.create(johnDoeMember, Payment.PaymentType.DUES))));
    accountsBuilder
        .owedBuilder()
        .putAll(janeDoeMember,
            ImmutableList.copyOf(Collections.nCopies(2,
                Payment.create(janeDoeMember, Payment.PaymentType.DUES))));
    accountsBuilder
        .waiversBuilder()
        .putAll(bobSmithMember,
            ImmutableList.copyOf(Collections.nCopies(2,
                Payment.create(bobSmithMember, Payment.PaymentType.DUES))));

    client.writeInvoices("foo",
        invoiceBuilder.build(),
        accountsBuilder.build(),
        LocalDate.parse("2021-01-01"));

    verify(batchUpdate, times(1)).execute();

    verify(valuesAppend, times(1)).execute();
  }

  @Test
  public void generateEmails() throws IOException {
    Sheets sheets = mock(Sheets.class);
    Sheets.Spreadsheets spreadsheets = mock(Sheets.Spreadsheets.class);
    Sheets.Spreadsheets.Values values = mock(Sheets.Spreadsheets.Values.class);
    Sheets.Spreadsheets.Values.Get valuesGet = mock(Sheets.Spreadsheets.Values.Get.class);
    ValueRange valueRange = new ValueRange();
    List<List<Object>> responses =
        ImmutableList.of(ImmutableList.of("John Doe", "regular", -80, 2, 40, 80, 0, 0),
            ImmutableList.of("Jane Doe", "associate", 45, 2, 15, 30, 15, 30),
            ImmutableList.of("Bob Smith", "student", 40, 2, 20, 40, 20, -60));
    valueRange.setValues(responses);

    when(sheets.spreadsheets()).thenReturn(spreadsheets);
    when(spreadsheets.values()).thenReturn(values);
    when(values.get(eq("foo"), eq("2021-01 to 2021-02!A2:H"))).thenReturn(valuesGet);
    when(valuesGet.execute()).thenReturn(valueRange);

    SheetsClient client = new SheetsClient(sheets);
    List<Invoices.InvoiceEmail> emails = client.generateEmails("foo",
        memberDatabase,
        LocalDate.parse("2021-01-01"),
        LocalDate.parse("2021-02-28"));

    assertThat(emails).hasSize(3);
    Invoices.InvoiceEmail email1 = emails.get(0);
    assertThat(email1.emailTo()).isEqualTo("john.doe@gmail.com");
    assertThat(email1.emailText()).contains("dues are: $0");
    Invoices.InvoiceEmail email2 = emails.get(1);
    assertThat(email2.emailTo()).isEqualTo("jane.doe@gmail.com");
    assertThat(email2.emailText()).contains("dues are: $0");
    Invoices.InvoiceEmail email3 = emails.get(2);
    assertThat(email3.emailTo()).isEqualTo("bob.smith@gmail.com");
    assertThat(email3.emailText()).contains("dues are: $60");
  }
}
