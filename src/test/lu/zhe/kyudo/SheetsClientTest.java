package lu.zhe.kyudo;

import com.google.api.services.sheets.v4.*;
import com.google.api.services.sheets.v4.model.*;
import com.google.common.collect.*;
import com.squareup.square.models.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import java.io.*;
import java.time.*;
import java.util.*;

import static com.google.common.truth.Truth.*;
import static org.mockito.Mockito.*;

/** Unit tests for {@link SheetsClient}. */
@RunWith(JUnit4.class)
public class SheetsClientTest {
  @Test
  public void parseAttendance() throws IOException {
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

    Member johnDoeMember = Member.create(johnDoe);
    Member janeDoeMember = Member.create(janeDoe);
    Member bobSmithMember = Member.create(bobSmith);
    Member aliceEveMember = Member.create(aliceEve);
    builder.addMember(johnDoeMember);
    builder.addMember(janeDoeMember);
    builder.addMember(bobSmithMember);
    builder.addMember(aliceEveMember);
    MemberDatabase database = builder.build();

    Sheets sheets = mock(Sheets.class);
    Sheets.Spreadsheets spreadsheets = mock(Sheets.Spreadsheets.class);
    Sheets.Spreadsheets.Values values = mock(Sheets.Spreadsheets.Values.class);
    Sheets.Spreadsheets.Values.Get valuesGet = mock(Sheets.Spreadsheets.Values.Get.class);
    ValueRange valueRange = new ValueRange();
    when(sheets.spreadsheets()).thenReturn(spreadsheets);
    when(spreadsheets.values()).thenReturn(values);
    when(values.get(eq("foo"), eq("Form Responses 1!A2:E"))).thenReturn(valuesGet);
    when(valuesGet.execute()).thenReturn(valueRange);

    List<List<Object>> responses =
        ImmutableList.of(ImmutableList.of("", "", "John Doe, Jane Doe", "", "1/20/2018"),
            ImmutableList.of("", "", "John Doe, Jane Doe", "", "2/01/2018"),
            ImmutableList.of("", "", "John Doe, Jane Doe, Bob Smith", "", "2/25/2018"),
            ImmutableList.of("", "", "John Doe, Alice Eve", "", "3/20/2018"),
            ImmutableList.of("", "", "John Doe, Jane Doe", "", "4/01/2018"));
    valueRange.setValues(responses);

    SheetsClient client = new SheetsClient(sheets);

    ImmutableMultiset<Member> result = client.readAttendanceSheet(database,
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
}
