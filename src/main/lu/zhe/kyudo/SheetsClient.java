package lu.zhe.kyudo;

import com.google.api.client.auth.oauth2.*;
import com.google.api.client.googleapis.javanet.*;
import com.google.api.client.json.jackson2.*;
import com.google.api.services.sheets.v4.*;
import com.google.api.services.sheets.v4.model.*;
import com.google.common.annotations.*;
import com.google.common.base.*;
import com.google.common.collect.*;

import java.io.*;
import java.security.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/** Client for interfacing with Google sheets to process attendance. */
public class SheetsClient {
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
}
