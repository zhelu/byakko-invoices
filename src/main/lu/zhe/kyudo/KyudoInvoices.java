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

  @VisibleForTesting
  KyudoInvoices(
      KyudoInvoiceOptions options, GmailClient gmailClient, SheetsClient sheetsClient,
      SquareApiClient squareClient, LocalDate startDateInclusive, LocalDate endDateInclusive) {
    this.options = options;
    this.gmailClient = gmailClient;
    this.sheetsClient = sheetsClient;
    this.squareClient = squareClient;
    this.startDateInclusive = startDateInclusive;
    this.endDateInclusive = endDateInclusive;
  }

  public static KyudoInvoices create(
      KyudoInvoiceOptions options) throws IOException, GeneralSecurityException {
    if (options.oauthClientId.isEmpty()) {
      throw new IllegalArgumentException("No Google OAuth client id specified");
    }
    if (options.oauthClientSecret.isEmpty()) {
      throw new IllegalArgumentException("No Google OAuth client secret specified");
    }
    if (options.user.isEmpty()) {
      throw new IllegalArgumentException("No Gmail user specified");
    }
    if (options.attendanceSheetsId.isEmpty()) {
      throw new IllegalArgumentException("No attendance Sheets id specified");
    }
    if (options.invoiceSheetsId.isEmpty()) {
      throw new IllegalArgumentException("No invoice Sheets id specified");
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
        endDate);
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
            "https://www.googleapis.com/auth/spreadsheets")).build();
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

  public void fillSpreadsheet() throws IOException, ApiException {
    MemberDatabase memberDatabase = squareClient.getMembers();

    SheetsClient.Accounts accounts =
        sheetsClient.readAccounts(memberDatabase, options.invoiceSheetsId, endDateInclusive);

    Invoices invoices = generateInvoices(memberDatabase, accounts);

    sheetsClient.writeInvoices(options.invoiceSheetsId, invoices, accounts, startDateInclusive);

    System.out.println("===================================================================");
    System.out.println("WAIVERS:");
    System.out.println(invoices.computeWaivers());
    System.out.println("OWED:");
    System.out.println(invoices.computeOwed());
  }

  public void printEmails() throws IOException, ApiException {
    MemberDatabase memberDatabase = squareClient.getMembers();

    List<Invoices.InvoiceEmail> emails = sheetsClient.generateEmails(options.invoiceSheetsId,
        memberDatabase,
        startDateInclusive,
        endDateInclusive);

    for (Invoices.InvoiceEmail email : emails) {
      System.out.println(email);
    }
  }

  public void sendEmails() throws IOException, ApiException {
    MemberDatabase memberDatabase = squareClient.getMembers();

    List<Invoices.InvoiceEmail> emails = sheetsClient.generateEmails(options.invoiceSheetsId,
        memberDatabase,
        startDateInclusive,
        endDateInclusive);

    System.out.println("Sending emails");
    emails.forEach(gmailClient::sendEmail);

    System.out.println("Sending invoices");
    squareClient.cancelOutstandingInvoicesForAutoInvoicedCustomers(memberDatabase,
        options.locationId);
    squareClient.createAndSendInvoices(emails, options.locationId);
  }

  private Invoices generateInvoices(
      MemberDatabase memberDatabase,
      SheetsClient.Accounts accounts) throws IOException, ApiException {
    ListMultimap<Member, Payment> paymentsTable = squareClient.getPayments(memberDatabase,
        options.locationId,
        startDateInclusive,
        endDateInclusive);

    Multiset<Member> attendance = sheetsClient.readAttendanceSheet(memberDatabase,
        options.attendanceSheetsId,
        startDateInclusive,
        endDateInclusive);

    ListMultimap<Member, Payment> waivers = accounts.waivers();
    ListMultimap<Member, Payment> owed = accounts.owed();

    Invoices.Builder invoicesBuilder = Invoices.builder();
    for (Member member : Sets.union(attendance.elementSet(), paymentsTable.keySet())) {
      invoicesBuilder.processMember(member,
          attendance.count(member),
          paymentsTable.get(member),
          waivers.get(member),
          owed.get(member));
    }

    return invoicesBuilder.build();
  }
}
