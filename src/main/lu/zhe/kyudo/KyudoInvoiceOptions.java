package lu.zhe.kyudo;

import com.google.devtools.common.options.Converter;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParsingException;

import java.time.LocalDate;

/** Command line options for processing attendance, payment information, to generate invoices. */
public class KyudoInvoiceOptions extends OptionsBase {
  @Option(name = "attendance", abbrev = 'a', help = "path to attendance CSV file",
      defaultValue = "")
  public String attendance;

  @Option(name = "sheetsId", help = "Google Sheets Id for attendance file", defaultValue = "")
  public String sheetsId;

  @Option(name = "startDate",
      help = "Start date in ISO8601 format, or empty string to start at the beginning of the " +
          "month two months prior to the current month",
      defaultValue = "1900-01-01", converter = LocalDateConverter.class)
  public LocalDate startDate;

  @Option(name = "payment", abbrev = 'p', help = "path to payments CSV file",
      defaultValue = "")
  public String payment;

  @Option(name = "waivers", abbrev = 'w', help = "path to special waivers CSV file",
      defaultValue = "")
  public String waivers;

  @Option(name = "owed", abbrev = 'o', help = "path to CSV listing owed counts", defaultValue = "")
  public String owed;

  @Option(name = "members", abbrev = 'm', help = "path to members CSV file",
      defaultValue = "")
  public String members;

  @Option(name = "base_path", abbrev = 'b',
      help = "base path shared by all other file paths. It will be prepended when resolving files",
      defaultValue = "")
  public String basePath;

  @Option(name = "oauth_client_id", help = "Oath2 client name", defaultValue = "")
  public String oauthClientId;

  @Option(name = "oauth_client_secret", help = "Oath2 client secret", defaultValue = "")
  public String oauthClientSecret;

  @Option(name = "square_access_token", help = "Square API's access token", defaultValue = "")
  public String squareAccessToken;

  @Option(name = "square_location_id", help = "Location id for the store", defaultValue = "")
  public String locationId;

  @Option(name = "user", abbrev = 'u', help = "Login user", defaultValue = "")
  public String user;

  @Option(name = "send_email", help = "Actually send emails", defaultValue = "false")
  public boolean sendEmail;

  public static class LocalDateConverter implements Converter<LocalDate> {
    @Override
    public LocalDate convert(String s) throws OptionsParsingException {
      try {
        return LocalDate.parse(s);
      } catch (Exception e) {
        throw new OptionsParsingException("Error parsing LocalDate flag: " + e.getMessage());
      }
    }

    @Override
    public String getTypeDescription() {
      return LocalDate.class.getCanonicalName();
    }
  }
}
