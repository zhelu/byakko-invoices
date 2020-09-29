package lu.zhe.kyudo;

import com.google.api.client.auth.oauth2.*;
import com.google.api.client.extensions.java6.auth.oauth2.*;
import com.google.api.client.extensions.jetty.auth.oauth2.*;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.googleapis.javanet.*;
import com.google.api.client.json.jackson2.*;
import com.google.common.annotations.*;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.squareup.square.exceptions.*;
import lu.zhe.csv.*;

import java.io.*;
import java.security.*;
import java.time.*;
import java.util.*;

/** Generates invoices for Byakko Kyudodojo. */
public class KyudoInvoices {
  public static final String APP_NAME = "Byakko Kyudojo Invoice Generator | 白虎弓道場";
  private static final Joiner COMMA_JOINER = Joiner.on(",");

  private final KyudoInvoiceOptions options;
  private final GmailClient gmailClient;
  private final SheetsClient sheetsClient;
  private final SquareApiClient squareClient;
  private final LocalDate startDateInclusive;
  private final LocalDate endDateInclusive;
  private final String owedPath;
  private final String waiversPath;

  @VisibleForTesting
  KyudoInvoices(
      KyudoInvoiceOptions options, GmailClient gmailClient, SheetsClient sheetsClient,
      SquareApiClient squareClient, LocalDate startDateInclusive, LocalDate endDateInclusive,
      String owedPath, String waiversPath) {
    this.options = options;
    this.gmailClient = gmailClient;
    this.sheetsClient = sheetsClient;
    this.squareClient = squareClient;
    this.startDateInclusive = startDateInclusive;
    this.endDateInclusive = endDateInclusive;
    this.owedPath = owedPath;
    this.waiversPath = waiversPath;
  }

  public static KyudoInvoices create(
      KyudoInvoiceOptions options, String waiversPath,
      String owedPath) throws IOException, GeneralSecurityException {
    if (options.oauthClientId.isEmpty()) {
      throw new IllegalArgumentException("No Google OAuth client id specified");
    }
    if (options.oauthClientSecret.isEmpty()) {
      throw new IllegalArgumentException("No Google OAuth client secret specified");
    }
    if (options.user.isEmpty()) {
      throw new IllegalArgumentException("No Gmail user specified");
    }
    if (options.sheetsId.isEmpty()) {
      throw new IllegalArgumentException("No attendance Sheets id specified");
    }
    if (options.locationId.isEmpty()) {
      throw new IllegalArgumentException("No Square location id specified");
    }
    if (options.squareAccessToken.isEmpty()) {
      throw new IllegalArgumentException("No Square access token specified");
    }
    LocalDate startDate =
        options.startDate.equals(LocalDate.parse("1900-01-01")) ? getStartDate() : options.startDate;
    LocalDate endDate = getEndDateInclusive(startDate);

    Credential credential = createCredential(options);
    return new KyudoInvoices(options,
        GmailClient.create(credential, options.user),
        SheetsClient.create(credential),
        SquareApiClient.create(options.squareAccessToken),
        startDate,
        endDate,
        owedPath,
        waiversPath);
  }

  private static ListMultimap<Member, Payment> parsePaymentsFromCsv(
      ImmutableTable<Integer, String, String> csv, MemberDatabase memberDatabase) {
    ImmutableListMultimap.Builder<Member, Payment> result = ImmutableListMultimap.builder();
    for (Map.Entry<?, Map<String, String>> entry : csv.rowMap().entrySet()) {
      Member member = memberDatabase.nameToMember().get(entry.getValue().get("member"));
      int quantity = Integer.parseInt(entry.getValue().get("amount")) / member.type().value();
      result.putAll(member,
          Collections.nCopies(quantity, Payment.create(member, Payment.PaymentType.DUES)));
    }
    return result.build();
  }

  private static Credential createCredential(
      KyudoInvoiceOptions options) throws IOException, GeneralSecurityException {
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        JacksonFactory.getDefaultInstance(),
        options.oauthClientId,
        options.oauthClientSecret,
        ImmutableList.of("https://www.googleapis.com/auth/gmail.compose",
            "https://www.googleapis.com/auth/gmail.send",
            "https://www.googleapis.com/auth/spreadsheets.readonly")).build();
    return new AuthorizationCodeInstalledApp(flow,
        new LocalServerReceiver()).authorize(options.user);
  }

  private static LocalDate getStartDate() {
    LocalDate localDate = LocalDate.now();
    return localDate.minusMonths(2).withDayOfMonth(1);
  }

  private static LocalDate getEndDateInclusive(LocalDate startDate) {
    return startDate.plusMonths(2).minusDays(1);
  }

  private static void printDiffs(
      Invoices invoices, Multiset<Member> attendance, ListMultimap<Member, Payment> paymentsTable,
      ListMultimap<Member, Payment> waivers, ListMultimap<Member, Payment> owed) {
    System.out.println("==================================================================");
    System.out.println("DIFFS:");
    System.out.println("==================================================================");
    for (Member member : invoices.emails().keySet()) {
      System.out.println(member.name() + "(" + member.type() + ")" + ": ");
      System.out.println("\tBillable periods: " + attendance.count(member) + " -> " +
          (attendance.count(member) * member.type().value()));
      System.out.println("\tOwed: " + COMMA_JOINER.join(paymentsTable.get(member)) + " -> " +
          owed.get(member).stream().mapToInt(Payment::amount).sum());
      System.out.println("\tWaivers: " + COMMA_JOINER.join(waivers.get(member)) + " -> " +
          waivers.get(member).stream().mapToInt(Payment::amount).sum());
      System.out.println("\tPayments: " + COMMA_JOINER.join(paymentsTable.get(member)) + " -> " +
          paymentsTable.get(member).stream().mapToInt(Payment::amount).sum());
      System.out.println("------------------------------------------------------------------");
      System.out.println(
          "\tNet owed: " + invoices.owed().get(member).stream().mapToInt(Payment::amount).sum());
      System.out.println("\tNet banked: " +
          invoices.waivers().get(member).stream().mapToInt(Payment::amount).sum());
      System.out.println("==================================================================");
    }
    System.out.println("==================================================================");
  }

  public void run(String waiversPath, String owedPath) throws IOException, ApiException {
    Invoices invoices = generateInvoices();
    invoices.emails().values().forEach(gmailClient::sendEmail);
    invoices.writeOutCsv(waiversPath, owedPath);

    squareClient.createAndSendInvoices(invoices, options.locationId);

    System.out.println("===================================================================");
    System.out.println("WAIVERS:");
    System.out.println(invoices.computeWaivers());
    System.out.println("OWED:");
    System.out.println(invoices.computeOwed());
  }

  public void runTest() throws IOException, ApiException {
    Invoices invoices = generateInvoices();

    invoices.emails().values().forEach(e -> {
      System.out.println("-------------------------------------------------------------------");
      System.out.println(e.emailTo());
      System.out.println(e.emailText());
      System.out.println();
    });
    System.out.println("===================================================================");
    System.out.println("WAIVERS:");
    System.out.println(invoices.computeWaivers());
    System.out.println("OWED:");
    System.out.println(invoices.computeOwed());
    System.out.println();
  }

  private Invoices generateInvoices() throws IOException, ApiException {
    MemberDatabase memberDatabase = squareClient.getMembers();
    ListMultimap<Member, Payment> paymentsTable = squareClient.getPayments(memberDatabase,
        options.locationId,
        startDateInclusive,
        endDateInclusive);

    Multiset<Member> attendance = sheetsClient.readAttendanceSheet(memberDatabase,
        options.sheetsId,
        startDateInclusive,
        endDateInclusive);

    ImmutableTable<Integer, String, String> waiversCsv = CsvReader.parseFile(waiversPath, true);
    ImmutableTable<Integer, String, String> owedCsv = CsvReader.parseFile(owedPath, true);
    ListMultimap<Member, Payment> waivers = parsePaymentsFromCsv(waiversCsv, memberDatabase);
    ListMultimap<Member, Payment> owed = parsePaymentsFromCsv(owedCsv, memberDatabase);

    Invoices.Builder invoicesBuilder = Invoices.builder();
    for (Member member : Sets.union(attendance.elementSet(), owed.keySet())) {
      invoicesBuilder.processMember(member,
          attendance.count(member),
          paymentsTable.get(member),
          waivers.get(member),
          owed.get(member),
          startDateInclusive,
          endDateInclusive);
    }

    Invoices invoices = invoicesBuilder.build();

    printDiffs(invoices, attendance, paymentsTable, waivers, owed);

    return invoices;
  }
}
