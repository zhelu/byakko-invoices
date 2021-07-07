package lu.zhe.kyudo;

import com.google.api.client.auth.oauth2.*;
import com.google.api.client.googleapis.javanet.*;
import com.google.api.client.json.jackson2.*;
import com.google.api.services.sheets.v4.*;
import com.google.api.services.sheets.v4.model.*;
import com.google.auto.value.*;
import com.google.common.annotations.*;
import com.google.common.base.*;
import com.google.common.collect.*;

import java.io.*;
import java.security.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.function.Supplier;

/** Client for interfacing with Google sheets to process attendance. */
public class SheetsClient {
  static final ImmutableList<Object> INVOICE_SHEET_HEADERS = ImmutableList.of("member",
      "status",
      "starting amount",
      "billable instances",
      "rate",
      "charges",
      "payments",
      "ending amount");
  private static final Splitter ATTENDANCE_SPLITTER = Splitter.on(", ");
  private static final DateTimeFormatter ATTENDANCE_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("M/d/yyyy");
  private final Sheets sheets;

  @VisibleForTesting
  SheetsClient(Sheets sheets) {
    this.sheets = sheets;
  }

  public static SheetsClient create(
      Credential credential) throws GeneralSecurityException, IOException {
    return new SheetsClient(new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(),
        JacksonFactory.getDefaultInstance(),
        credential).setApplicationName(KyudoInvoices.APP_NAME).build());
  }

  private static String getTabName(YearMonth firstYearMonth) {
    return firstYearMonth + " to " + firstYearMonth.plusMonths(1);
  }

  private static String getInvoiceMemberType(Member member) {
    switch (member.type()) {
      case MEMBER:
      case REGULAR:
        return "regular";
      case STUDENT:
        return "student";
      case ASSOCIATE:
        return "associate";
    }
    throw new IllegalStateException("Unknown type");
  }

  private static int getAmountOwedOrBanked(
      Supplier<ListMultimap<Member, Payment>> owedSupplier,
      Supplier<ListMultimap<Member, Payment>> waiverSupplier, Member member) {
    ListMultimap<Member, Payment> owed = owedSupplier.get();
    ListMultimap<Member, Payment> waivers = waiverSupplier.get();
    if (owed.containsKey(member)) {
      return -owed.get(member).stream().mapToInt(Payment::amount).sum();
    } else if (waivers.containsKey(member)) {
      return waivers.get(member).stream().mapToInt(Payment::amount).sum();
    }
    return 0;
  }

  public ImmutableMultiset<Member> readAttendanceSheet(
      MemberDatabase memberDatabase, String sheetId, LocalDate startDateInclusive,
      LocalDate endDateInclusive) throws IOException {
    ImmutableMultiset.Builder<Member> builder = ImmutableMultiset.builder();
    SetMultimap<Member, YearMonth> attendanceByMonth = HashMultimap.create();
    ValueRange response =
        sheets.spreadsheets().values().get(sheetId, "Form Responses 1!A2:E").execute();
    for (List<Object> row : response.getValues()) {
      LocalDate date = LocalDate.parse((String) row.get(4), ATTENDANCE_DATE_FORMATTER);
      if (date.isBefore(startDateInclusive) || date.isAfter(endDateInclusive)) {
        continue;
      }
      String members = (String) row.get(2);
      for (String memberName : ATTENDANCE_SPLITTER.splitToList(members)) {
        Member member = memberDatabase.nameToMember().get(memberName);
        switch (member.type()) {
          case ASSOCIATE:
            builder.add(member);
            break;
          case MEMBER:
            break;
          case REGULAR:
            // fall through intended
          case STUDENT:
            attendanceByMonth.put(member, YearMonth.of(date.getYear(), date.getMonth()));
        }
      }
    }
    attendanceByMonth.asMap().forEach((m, c) -> builder.addCopies(m, c.size()));
    int months = 0;
    LocalDate date = startDateInclusive;
    while (date.isBefore(endDateInclusive)) {
      ++months;
      date = date.plusMonths(1);
    }
    int monthsCopy = months;
    memberDatabase
        .nameToMember()
        .values()
        .stream()
        .filter(m -> m.type() == MemberType.MEMBER)
        .forEach(m -> builder.addCopies(m, monthsCopy));
    return builder.build();
  }

  /**
   * Reads accounts from sheets.
   */
  public Accounts readAccounts(
      MemberDatabase memberDatabase, String sheetId, LocalDate endDate) throws IOException {
    LocalDate firstMonth = endDate.minusMonths(3);
    String previousAccountsTab = getTabName(YearMonth.from(firstMonth));
    ValueRange response =
        sheets.spreadsheets().values().get(sheetId, previousAccountsTab + "!A2:H").execute();
    Accounts.Builder result = Accounts.builder();
    for (List<Object> row : response.getValues()) {
      Member member = memberDatabase.nameToMember().get(row.get(0));
      int amount = Integer.parseInt((String) row.get(7));
      Payment payment = Payment.create(member, Payment.PaymentType.DUES);
      int count = amount / payment.amount();
      if (amount > 0) {
        result
            .waiversBuilder()
            .putAll(member, ImmutableList.copyOf(Collections.nCopies(count, payment)));
      } else {
        result
            .owedBuilder()
            .putAll(member, ImmutableList.copyOf(Collections.nCopies(-count, payment)));
      }
    }
    return result.build();
  }

  /** Write out invoices to Sheets. */
  public void writeInvoices(
      String sheetsId, Invoices invoices, Accounts accounts,
      LocalDate startDate) throws IOException {
    String tabName = getTabName(YearMonth.from(startDate));
    AddSheetRequest addSheetRequest =
        new AddSheetRequest().setProperties(new SheetProperties().setTitle(tabName));
    sheets
        .spreadsheets()
        .batchUpdate(sheetsId,
            new BatchUpdateSpreadsheetRequest().setRequests(ImmutableList.of(new Request().setAddSheet(
                addSheetRequest))))
        .execute();

    List<List<Object>> values = new ArrayList<>();

    values.add(INVOICE_SHEET_HEADERS);
    for (Member member : Streams
        .concat(accounts.owed().keySet().stream(),
            invoices.attendanceCount().keySet().stream(),
            accounts.waivers().keySet().stream())
        .distinct()
        .sorted(Comparator.comparing(Member::name))
        .collect(ImmutableList.toImmutableList())) {
      values.add(ImmutableList.of(member.name(),
          getInvoiceMemberType(member),
          getAmountOwedOrBanked(accounts::owed, accounts::waivers, member),
          invoices.attendanceCount().getOrDefault(member, 0),
          member.type().value(),
          "= D:D * E:E",
          invoices.payments().getOrDefault(member, 0),
          "= C:C - F:F + G:G"));
    }
    ValueRange writeData = new ValueRange().setValues(values);

    sheets
        .spreadsheets()
        .values()
        .append(sheetsId, tabName + "!A1", writeData)
        .setValueInputOption("USER_ENTERED")
        .setInsertDataOption("INSERT_ROWS")
        .execute();
  }

  public ImmutableList<Invoices.InvoiceEmail> generateEmails(
      String sheetId, MemberDatabase memberDatabase, LocalDate startDate,
      LocalDate endDate) throws IOException {
    String tabName = getTabName(YearMonth.from(startDate));
    ValueRange response = sheets.spreadsheets().values().get(sheetId, tabName + "!A2:H").execute();
    ImmutableList.Builder<Invoices.InvoiceEmail> emails = ImmutableList.builder();
    for (List<Object> row : response.getValues()) {
      Member member = memberDatabase.nameToMember().get(row.get(0));
      int owed = -Integer.parseInt(String.valueOf(row.get(7)));
      if (owed < 0) {
        owed = 0;
      }
      int attendance = Integer.parseInt(String.valueOf(row.get(3)));
      if (owed > 0 || attendance > 0) {
        emails.add(Invoices.InvoiceEmail.create(member, attendance, owed, startDate, endDate));
      }
    }
    return emails.build();
  }

  @AutoValue
  public abstract static class Accounts {
    static Builder builder() {
      return new lu.zhe.kyudo.AutoValue_SheetsClient_Accounts.Builder();
    }

    public abstract ImmutableListMultimap<Member, Payment> owed();

    public abstract ImmutableListMultimap<Member, Payment> waivers();

    @AutoValue.Builder
    public abstract static class Builder {
      abstract ImmutableListMultimap.Builder<Member, Payment> owedBuilder();

      abstract ImmutableListMultimap.Builder<Member, Payment> waiversBuilder();

      public abstract Accounts build();
    }
  }
}
