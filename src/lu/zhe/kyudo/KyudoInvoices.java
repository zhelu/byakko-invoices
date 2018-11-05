package lu.zhe.kyudo;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.*;
import com.google.devtools.common.options.OptionsParser;
import lu.zhe.csv.CsvReader;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/** Generates invoices for Byakko Kyudodojo. */
public class KyudoInvoices {
  private static final DateTimeFormatter ATTENDANCE_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("M/d/yyyy");
  private static final DateTimeFormatter PAYMENT_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("MM/dd/yy");
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  private static final String GREETING_TEMPLATE = "Hello!\n\n" +
      "This is an automatically generated email from Byakko Kyudojo about dues.\n\n";
  private static final String DUES_TEMPLATE =
      "\n\nBased on your status (%s), previous outstanding dues, and previous payments, " +
          "we believe your outstanding dues are: $%d\n\n" +
          "You can pay by check or credit card after practice, or you can respond to this email " +
          "if you'd like to receive an invoice and link to pay online via email.\n\n" +
          "This does not included any payments recorded after %s.\n\n" +
          "If you think this is incorrect, please see Zhe Lu or respond to this message.\n\n" +
          "Thank you!";
  private static final String MONTHLY_TEMPLATE = GREETING_TEMPLATE +
      "Our records indicate that you attended practice for %d month(s) between %s and %s" +
      DUES_TEMPLATE;
  private static final String ASSOCIATED_TEMPLATE = GREETING_TEMPLATE +
      "Our records indicate that you attended %d practices between %s and %s" + DUES_TEMPLATE;
  private static final Splitter ATTENDANCE_SPLITTER = Splitter.on(", ");
  private static final String FIRST_NAME = "First Name";
  private static final String LAST_NAME = "Last Name";
  private static final String TYPE = "Type";
  private static final String REGULAR_MEMBERS = "Regular members";
  private static final String DATE = "Date";
  private static final String EMAIL_ADDRESS = "Email Address";
  private static final String FIRST_SHOT_ITEM = "First Shot";
  private static final String CUSTOMER_NAME = "Customer Name";
  private static final String ITEM = "Item";
  private static final String QUANTITY = "Qty";
  private static HttpTransport HTTP_TRANSPORT;

  static {
    try {
      HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
  }

  public static void main(String[] args) throws Exception {
    OptionsParser parser = OptionsParser.newOptionsParser(KyudoInvoiceOptions.class);
    parser.parseAndExitUponError(args);
    KyudoInvoiceOptions options = parser.getOptions(KyudoInvoiceOptions.class);

    String basePath = options.basePath;
    String attendancePath = basePath.isEmpty() ? options.attendance : basePath + options.attendance;
    String paymentPath = basePath.isEmpty() ? options.payment : basePath + options.payment;
    String waiversPath = basePath.isEmpty() ? options.waivers : basePath + options.waivers;
    String membersPath = basePath.isEmpty() ? options.members : basePath + options.members;
    String owedPath = basePath.isEmpty() ? options.owed : basePath + options.owed;

    checkFileTimeStamp(attendancePath, options.endDate);
    checkFileTimeStamp(paymentPath, options.endDate);
    checkFileTimeStamp(membersPath, options.endDate);

    ImmutableTable<Integer, String, String> attendance =
        CsvReader.parseFile(attendancePath, true);
    ImmutableTable<Integer, String, String> payment =
        CsvReader.parseFile(paymentPath, true);
    ImmutableTable<Integer, String, String> waivers =
        CsvReader.parseFile(waiversPath, true);
    ImmutableTable<Integer, String, String> members =
        CsvReader.parseFile(membersPath, true);
    ImmutableTable<Integer, String, String> owed =
        CsvReader.parseFile(owedPath, true);

    Invoices invoices =
        processAttendanceAndPayments(attendance, payment, members, waivers, owed, options.startDate,
            options.endDate);

    if (options.sendEmail) {
      Gmail gmailService = createGmailService(options);
      Properties properties = new Properties();
      Session session = Session.getDefaultInstance(properties, null);

      for (Invoices.EmailEntry email : invoices) {
        sendMail(gmailService, session, options.user, email);
      }
    } else {
      for (Invoices.EmailEntry email : invoices) {
        if (!email.email.isEmpty()) {
          System.out.println(email.email + "\n" + email.text);
          System.out.println();
        }
      }
    }

    System.out.println();
    System.out.println();

    for (Invoices.EmailEntry entry : invoices) {
      if (entry.email.isEmpty()) {
        System.out.println("No email for " + entry.name);
      }
    }

    Instant now = Instant.now();
    System.out.println("\n\nUPDATED WAIVERS:\n\nmember");
    FileWriter waiverWriter = options.sendEmail ? new FileWriter(new File(
        options.basePath + "waiver-" + now.toString().replaceAll(":", "_") + ".csv")) : null;
    if (waiverWriter != null) {
      waiverWriter.append("member,amount\n");
    }
    for (Map.Entry<String, Integer> entry : invoices.getWaivers().entrySet()) {
      if (entry.getValue() == 0) {
        continue;
      }
      System.out.println(entry.getKey() + "," + entry.getValue());
      if (waiverWriter != null) {
        waiverWriter.append(entry.getKey() + "," + entry.getValue() + "\n");
      }
    }
    if (waiverWriter != null) {
      waiverWriter.close();
    }
    System.out.println();
    System.out.println();

    System.out.println("\n\nUPDATED OWED:\n\nmember");
    FileWriter owedWriter = options.sendEmail ? new FileWriter(new File(
        options.basePath + "owed-" + now.toString().replaceAll(":", "_") + ".csv")) : null;
    if (owedWriter != null) {
      owedWriter.append("member,amount\n");
    }
    for (Map.Entry<String, Integer> entry : invoices.getOwed().entrySet()) {
      if (entry.getValue() == 0) {
        continue;
      }
      System.out.println(entry.getKey() + "," + entry.getValue());
      if (owedWriter != null) {
        owedWriter.append(entry.getKey() + "," + entry.getValue() + "\n");
      }
    }
    if (owedWriter != null) {
      owedWriter.close();
    }
    System.out.println();
    System.out.println();
  }

  private static void sendMail(Gmail gmailService, Session session, String user,
                               Invoices.EmailEntry emailEntry) throws Exception {
    // Don't send anything if there's no email.
    if (emailEntry.email.isEmpty()) {
      return;
    }
    MimeMessage email = new MimeMessage(session);

    email.setFrom(new InternetAddress(user, "Byakko Kyudojo Treasurer"));
    email.addRecipient(MimeMessage.RecipientType.TO, new InternetAddress(emailEntry.email));
    email.setSubject("Byakko Kyudojo membership dues - Invoice");
    email.setText(emailEntry.text);

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    email.writeTo(buffer);
    byte[] bytes = buffer.toByteArray();
    String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
    Message message = new Message();
    message.setRaw(encodedEmail);

    Draft draft = new Draft();
    draft.setMessage(message);
    draft = gmailService.users().drafts().create(user, draft).execute();

    gmailService.users().drafts().send(user, draft).execute();
  }

  /**
   * Processes attendance and payment data, along with current waivers.
   *
   * @return a data structure that contains emails to send, along with a list of new waivers
   */
  private static Invoices processAttendanceAndPayments(
      ImmutableTable<Integer, String, String> attendanceTable,
      ImmutableTable<Integer, String, String> paymentTable,
      ImmutableTable<Integer, String, String> membersTable,
      ImmutableTable<Integer, String, String> waiversTable,
      ImmutableTable<Integer, String, String> owedTable,
      LocalDate startDate, LocalDate endDate) {
    BiMap<String, String> attendanceToPaymentNameMapping = HashBiMap.create();
    SetMultimap<String, YearMonth> attendanceByMonth = LinkedHashMultimap.create();
    Multiset<String> attendanceCount = HashMultiset.create();
    Map<String, Type> typeMapping = new HashMap<>();
    Map<String, String> emailMapping = new HashMap<>();
    for (int i = 0; i < membersTable.rowKeySet().size(); ++i) {
      String firstName = membersTable.get(i, FIRST_NAME);
      String lastName = membersTable.get(i, LAST_NAME);
      Type type = Type.fromString(membersTable.get(i, TYPE));
      String email = membersTable.get(i, EMAIL_ADDRESS);
      String fullName = firstName + " " + lastName;
      typeMapping.put(fullName, type);
      emailMapping.put(fullName, email);
      for (int j = 0; j < attendanceTable.rowKeySet().size(); ++j) {
        String attendanceEntry = attendanceTable.get(j, REGULAR_MEMBERS);
        List<String> attendanceMembers = ATTENDANCE_SPLITTER.splitToList(attendanceEntry);
        boolean found = false;
        for (String name : attendanceMembers) {
          String[] nameSplit = name.split(" ", 2);
          if (firstName.startsWith(nameSplit[0].replaceAll("\\.", "")) &&
              lastName.startsWith(nameSplit[1].replaceAll("\\.", ""))) {
            attendanceToPaymentNameMapping.put(name, fullName);
            typeMapping.put(name, type);
            emailMapping.put(name, email);
            found = true;
            break;
          }
        }
        if (found) {
          break;
        }
      }
    }
    for (int i = 0; i < attendanceTable.rowKeySet().size(); ++i) {
      LocalDate practiceDate =
          LocalDate.parse(attendanceTable.get(i, DATE), ATTENDANCE_DATE_FORMATTER);
      if (practiceDate.isBefore(startDate) || practiceDate.isAfter(endDate)) {
        continue;
      }
      YearMonth month = YearMonth.of(practiceDate.getYear(), practiceDate.getMonth());
      List<String> names = ATTENDANCE_SPLITTER.splitToList(attendanceTable.get(i, REGULAR_MEMBERS));
      for (String name : names) {
        try {
          switch (typeMapping.get(name)) {
            case REGULAR:
              // Fall through intended
            case STUDENT:
              attendanceByMonth.put(name, month);
              break;
            case ASSOCIATE:
              attendanceCount.add(name);
              break;
          }
        } catch (NullPointerException e) {
          throw new RuntimeException("Could not identify type of member: " + name);
        }
      }
    }
    // Count is number of free months.
    Map<String, Integer> waivers = new HashMap<>();
    for (int i = 0; i < waiversTable.rowKeySet().size(); ++i) {
      String member = waiversTable.get(i, "member");
      int amount = Integer.parseInt(waiversTable.get(i, "amount"));
      waivers.put(member, amount);
    }
    // Transaction name
    Map<String, Integer> paidAmounts = new HashMap<>();
    for (int i = 0; i < paymentTable.rowKeySet().size(); ++i) {
      LocalDate practiceDate =
          LocalDate.parse(paymentTable.get(i, DATE), PAYMENT_DATE_FORMATTER);
      if (practiceDate.isBefore(startDate) || practiceDate.isAfter(endDate)) {
        continue;
      }
      int quantity = new BigDecimal(paymentTable.get(i, QUANTITY)).intValueExact();
      String item = paymentTable.get(i, ITEM);
      String member = paymentTable.get(i, CUSTOMER_NAME);
      if (item.startsWith(FIRST_SHOT_ITEM)) {
        waivers.merge(member, getTwoMonthsFree(typeMapping.get(member)), Integer::sum);
      } else if (item.contains("Dues") || item.contains("dues")) {
        paidAmounts.merge(member, quantity * getBaseFeeByType(typeMapping.get(member)),
            Integer::sum);
      }
    }
    Set<String> invoiceNames = new HashSet<>();
    Map<String, Integer> owedAmounts = new HashMap<>();
    for (int i = 0; i < owedTable.rowKeySet().size(); ++i) {
      String member = owedTable.get(i, "member");
      int amount = Integer.parseInt(owedTable.get(i, "amount"));
      owedAmounts.put(member, amount);
    }
    invoiceNames.addAll(attendanceByMonth.keySet());
    invoiceNames.addAll(attendanceCount.elementSet());
    invoiceNames.addAll(owedAmounts.keySet());
    for (String name : invoiceNames) {
      Type type = typeMapping.get(name);
      if (type == null) {
        throw new RuntimeException("No type for member: " + name);
      }
      String paymentName = attendanceToPaymentNameMapping.get(name);
      switch (type) {
        case REGULAR:
          // Fall through intended
        case STUDENT:
          int owedDifference =
              attendanceByMonth.get(name).size() * getBaseFeeByType(type) -
                  paidAmounts.getOrDefault(paymentName, 0) -
                  waivers.getOrDefault(paymentName, 0);
          if (owedDifference < 0) {
            waivers.put(paymentName, -owedDifference);
          } else if (owedDifference > 0) {
            waivers.remove(paymentName);
            owedAmounts.merge(paymentName, owedDifference, Integer::sum);
          } else {
            waivers.remove(attendanceToPaymentNameMapping.get(name));
          }
          break;
        case ASSOCIATE:
          int owedAssociateDifference = attendanceCount.count(name) * getBaseFeeByType(type) -
              paidAmounts.getOrDefault(paymentName, 0) -
              waivers.getOrDefault(paymentName, 0);
          if (owedAssociateDifference < 0) {
            waivers.put(paymentName, -owedAssociateDifference);
          } else if (owedAssociateDifference > 0) {
            waivers.remove(name);
            owedAmounts.merge(paymentName, owedAssociateDifference, Integer::sum);
          } else {
            waivers.remove(name);
          }
          break;
      }
    }
    // We should reconcile owed and waivers. If there are entries in both, we should cancel out.
    for (String name : ImmutableSet.copyOf(owedAmounts.keySet())) {
      if (waivers.containsKey(name)) {
        int min = Math.min(waivers.get(name), owedAmounts.get(name));
        if (waivers.get(name) == min && owedAmounts.get(name) == min) {
          waivers.remove(name);
          owedAmounts.remove(name);
        } else if (waivers.get(name) == min) {
          waivers.remove(name);
          owedAmounts.put(name, owedAmounts.get(name) - min);
        } else {
          owedAmounts.remove(name);
          waivers.put(name, waivers.get(name) - min);
        }
      }
    }
    Invoices.Builder invoices = new Invoices.Builder();
    for (Map.Entry<String, Integer> owed : owedAmounts.entrySet()) {
      String emailText = "";
      String name = owed.getKey();
      String attendanceName = attendanceToPaymentNameMapping.inverse().get(name);
      switch (typeMapping.get(name)) {
        case REGULAR:
          emailText = String.format(MONTHLY_TEMPLATE, attendanceByMonth.get(attendanceName).size(),
              startDate, endDate, "regular: $40/month",
              owed.getValue(), endDate);
          break;
        case ASSOCIATE:
          emailText =
              String.format(ASSOCIATED_TEMPLATE, attendanceCount.count(attendanceName),
                  startDate,
                  endDate, "associate: $15/practice", owed.getValue(), endDate);
          break;
        case STUDENT:
          emailText = String.format(MONTHLY_TEMPLATE, attendanceByMonth.get(attendanceName).size(),
              startDate, endDate, "student: $20/month", owed.getValue(), endDate);
          break;
      }
      invoices.addEmail(name,
          emailMapping.getOrDefault(name, ""), emailText);
    }
    // Also add emails for people who attended practice but owe no money.
    Stream.concat(attendanceByMonth.keySet().stream(), attendanceCount.elementSet().stream()).map(
        attendanceToPaymentNameMapping::get).filter(n -> !owedAmounts.containsKey(n)).forEach(
        n -> {
          String attendanceName = attendanceToPaymentNameMapping.inverse().get(n);
          String emailText = "";
          switch (typeMapping.get(n)) {
            case REGULAR:
              emailText =
                  String.format(MONTHLY_TEMPLATE, attendanceByMonth.get(attendanceName).size(),
                      startDate, endDate, "regular: $40/month",
                      0, endDate);
              break;
            case ASSOCIATE:
              emailText =
                  String.format(ASSOCIATED_TEMPLATE, attendanceCount.count(attendanceName),
                      startDate,
                      endDate, "associate: $15/practice", 0, endDate);
              break;
            case STUDENT:
              emailText =
                  String.format(MONTHLY_TEMPLATE, attendanceByMonth.get(attendanceName).size(),
                      startDate, endDate, "student: $20/month", 0, endDate);
              break;
          }
          invoices.addEmail(n,
              emailMapping.getOrDefault(n, ""), emailText);
        }
    );
    invoices.addWaivers(waivers);
    invoices.addOwed(owedAmounts);
    return invoices.build();
  }

  private static int getTwoMonthsFree(Type type) {
    switch (type) {
      case REGULAR:
        return 80;
      case STUDENT:
        return 40;
      case ASSOCIATE:
        return 60;
    }
    return 0;
  }

  private static int getBaseFeeByType(Type type) {
    switch (type) {
      case REGULAR:
        return 40;
      case STUDENT:
        return 20;
      case ASSOCIATE:
        return 15;
    }
    return 0;
  }

  private static Gmail createGmailService(KyudoInvoiceOptions options) throws IOException {
    GoogleAuthorizationCodeFlow flow =
        new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, options.oauthClientId,
            options.oauthClientSecret,
            ImmutableList.of("https://www.googleapis.com/auth/gmail.compose",
                "https://www.googleapis.com/auth/gmail.send")).build();
    Credential credential =
        new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize(options.user);

    return
        new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(
            "Byakko Kyudojo Invoice Generator").build();
  }

  private static void checkFileTimeStamp(String path,
                                         LocalDate endDate) throws IllegalStateException {
    File f = new File(path);
    LocalDate fileModifiedDate = LocalDateTime.ofEpochSecond(f.lastModified(), 0,
        ZoneId.systemDefault().getRules().getOffset(Instant.now())).toLocalDate();
    if (!fileModifiedDate.isAfter(endDate)) {
      throw new IllegalStateException(
          "File's (" + path + ") last modified date is before the end date " + endDate +
              " for processing");
    }
  }

  private static class Invoices implements Iterable<Invoices.EmailEntry> {
    enum EmailTableColumn {
      EMAIL_TO,
      TEXT;
    }

    static class EmailEntry {
      public final String name;
      public final String email;
      public final String text;

      public EmailEntry(String name, String email, String text) {
        this.name = name;
        this.email = email;
        this.text = text;
      }
    }

    private final ImmutableTable<String, EmailTableColumn, String> emails;
    private final ImmutableSortedMap<String, Integer> waivers;
    private final ImmutableSortedMap<String, Integer> owed;

    public ImmutableMap<String, Integer> getWaivers() {
      return waivers;
    }

    public ImmutableMap<String, Integer> getOwed() {
      return owed;
    }

    private Invoices(ImmutableTable<String, EmailTableColumn, String> emails,
                     ImmutableSortedMap<String, Integer> waivers,
                     ImmutableSortedMap<String, Integer> owed) {
      this.emails = emails;
      this.waivers = waivers;
      this.owed = owed;
    }

    public static class Builder {
      private final ImmutableTable.Builder<String, EmailTableColumn, String> emails =
          ImmutableTable.builder();
      private final ImmutableSortedMap.Builder<String, Integer> waivers =
          ImmutableSortedMap.naturalOrder();
      private final ImmutableSortedMap.Builder<String, Integer> owed =
          ImmutableSortedMap.naturalOrder();

      public Builder addEmail(String name, String email, String text) {
        emails.put(name, EmailTableColumn.EMAIL_TO, email);
        emails.put(name, EmailTableColumn.TEXT, text);
        return this;
      }

      public Builder addWaivers(Map<String, Integer> waivers) {
        this.waivers.putAll(waivers);
        return this;
      }

      public Builder addOwed(Map<String, Integer> owed) {
        this.owed.putAll(owed);
        return this;
      }

      public Invoices build() {
        return new Invoices(emails.build(), waivers.build(), owed.build());
      }
    }

    public Iterator<EmailEntry> iterator() {
      final Iterator<Map.Entry<String, Map<EmailTableColumn, String>>> emailIterator =
          emails.rowMap().entrySet().iterator();
      return new Iterator<EmailEntry>() {

        @Override
        public boolean hasNext() {
          return emailIterator.hasNext();
        }

        @Override
        public EmailEntry next() {
          Map.Entry<String, Map<EmailTableColumn, String>> entry = emailIterator.next();
          return new EmailEntry(entry.getKey(), entry.getValue().get(EmailTableColumn.EMAIL_TO),
              entry.getValue().get(EmailTableColumn.TEXT));
        }
      };
    }
  }

  // Describes the type of member.
  private enum Type {
    REGULAR,
    STUDENT,
    ASSOCIATE;

    public static Type fromString(String s) {
      return Type.valueOf(Ascii.toUpperCase(s));
    }
  }
}
