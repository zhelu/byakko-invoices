package lu.zhe.kyudo;

import com.google.auto.value.*;
import com.google.common.collect.*;

import java.time.*;
import java.util.*;

/** Wrapper for created invoices. */
@AutoValue
public abstract class Invoices {
  public static Builder builder() {
    return new lu.zhe.kyudo.AutoValue_Invoices.Builder();
  }

  public abstract ImmutableListMultimap<Member, Payment> waivers();

  public abstract ImmutableListMultimap<Member, Payment> owed();

  public abstract ImmutableMap<Member, Integer> attendanceCount();

  public abstract ImmutableMap<Member, Integer> payments();

  public String computeWaivers() {
    StringBuilder result = new StringBuilder();
    result.append("member,amount");
    for (Map.Entry<Member, Collection<Payment>> entry : waivers().asMap().entrySet()) {
      int amount = entry.getValue().stream().mapToInt(Payment::amount).sum();
      if (amount > 0) {
        result.append("\n").append(entry.getKey().name()).append(",").append(amount);
      }
    }
    return result.toString();
  }

  public String computeOwed() {
    StringBuilder result = new StringBuilder();
    result.append("member,amount");
    for (Map.Entry<Member, Collection<Payment>> entry : owed().asMap().entrySet()) {
      int amount = entry.getValue().stream().mapToInt(Payment::amount).sum();
      if (amount > 0) {
        result.append("\n").append(entry.getKey().name()).append(",").append(amount);
      }
    }
    return result.toString();
  }

  @AutoValue.Builder
  public static abstract class Builder {
    protected abstract ImmutableListMultimap.Builder<Member, Payment> waiversBuilder();

    protected abstract ImmutableListMultimap.Builder<Member, Payment> owedBuilder();

    protected abstract ImmutableMap.Builder<Member, Integer> attendanceCountBuilder();

    protected abstract ImmutableMap.Builder<Member, Integer> paymentsBuilder();

    public void processMember(
        Member member, int attendanceCount, List<Payment> payments, List<Payment> waivers,
        List<Payment> owed) {
      int waiverAmount = waivers.stream().mapToInt(Payment::amount).sum() +
          payments.stream().mapToInt(Payment::amount).sum();
      int owedAmount =
          owed.stream().mapToInt(Payment::amount).sum() + attendanceCount * member.type().value();
      List<Payment> newWaivers = new ArrayList<>();
      List<Payment> newOwed = new ArrayList<>();
      if (waiverAmount > owedAmount) {
        newWaivers.addAll(Collections.nCopies((waiverAmount - owedAmount) / member.type().value(),
            Payment.create(member, Payment.PaymentType.DUES)));
      } else if (waiverAmount < owedAmount) {
        newOwed.addAll(Collections.nCopies((owedAmount - waiverAmount) / member.type().value(),
            Payment.create(member, Payment.PaymentType.DUES)));
      }

      waiversBuilder().putAll(member, newWaivers);
      owedBuilder().putAll(member, newOwed);
      attendanceCountBuilder().put(member, attendanceCount);
      paymentsBuilder().put(member, payments.stream().mapToInt(Payment::amount).sum());
    }

    public abstract Invoices build();
  }

  @AutoValue
  public static abstract class InvoiceEmail {
    private static final String GREETING_TEMPLATE = "Hello!\n\n" +
        "This is an automatically generated email from Byakko Kyudojo about dues.\n\n";
    private static final String DUES_TEMPLATE =
        "\n\nBased on your status (%s), previous outstanding dues, and previous payments, " +
            "we believe your outstanding dues are: $%d\n\n" +
            "You can pay by check or credit card after practice, or you can respond to this email " +
            "if you'd like to receive an invoice and link to pay online via email. If you signed " +
            "up for auto-invoicing, you should receive an invoice by email within the next hour." +
            "\n\n" + "This does not included any payments recorded after %s.\n\n" +
            "If you think this is incorrect, please see Zhe Lu or respond to this message.\n\n" +
            "Thank you!";
    private static final String MONTHLY_TEMPLATE = GREETING_TEMPLATE +
        "Our records indicate that you attended practice for %d month(s) between %s and %s" +
        DUES_TEMPLATE;
    private static final String ASSOCIATE_TEMPLATE = GREETING_TEMPLATE +
        "Our records indicate that you attended %d practice(s) between %s and %s" + DUES_TEMPLATE;

    public static InvoiceEmail create(
        Member member, int count, int owed, LocalDate startDate, LocalDate endDate) {
      String emailText = "";
      switch (member.type()) {
        case REGULAR:
          // Fall through intended
        case MEMBER:
          emailText = String.format(MONTHLY_TEMPLATE,
              count,
              startDate,
              endDate,
              "regular: $40/month",
              owed,
              endDate);
          break;
        case ASSOCIATE:
          emailText = String.format(ASSOCIATE_TEMPLATE,
              count,
              startDate,
              endDate,
              "associate: $15/practice",
              owed,
              endDate);
          break;
        case STUDENT:
          emailText = String.format(MONTHLY_TEMPLATE,
              count,
              startDate,
              endDate,
              "student: $20/month",
              owed,
              endDate);
          break;
      }
      if (emailText.isEmpty()) {
        throw new IllegalStateException("No email created");
      }
      if (member.customer().getEmailAddress() == null ||
          member.customer().getEmailAddress().isEmpty()) {
        throw new IllegalStateException("No email address");
      }
      return new lu.zhe.kyudo.AutoValue_Invoices_InvoiceEmail(member.customer().getEmailAddress(),
          emailText,
          member,
          ImmutableList.copyOf(Collections.nCopies(owed / member.type().value(),
              Payment.create(member, Payment.PaymentType.DUES))));
    }

    public abstract String emailTo();

    public abstract String emailText();

    public abstract Member member();

    public abstract ImmutableList<Payment> owed();

    @Override
    public String toString() {
      return "-----------------------------------------\n" + "TO: " + emailTo() + "\n" +
          emailText() + "\n" + "-----------------------------------------\n";
    }
  }
}
