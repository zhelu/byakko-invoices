package lu.zhe.kyudo;

import com.google.api.client.auth.oauth2.*;
import com.google.api.client.googleapis.javanet.*;
import com.google.api.client.json.jackson2.*;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.*;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.*;
import com.google.common.annotations.*;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.security.*;
import java.util.*;

/** Client for sending emails. */
public class GmailClient {
  private final Gmail client;
  private final String user;

  @VisibleForTesting
  GmailClient(Gmail client, String user) {
    this.client = client;
    this.user = user;
  }

  public static GmailClient create(
      Credential credential, String user) throws GeneralSecurityException, IOException {
    Gmail gmailService = new Gmail.Builder(GoogleNetHttpTransport.newTrustedTransport(),
        JacksonFactory.getDefaultInstance(),
        credential).setApplicationName(KyudoInvoices.APP_NAME).build();
    return new GmailClient(gmailService, user);
  }

  public void sendEmail(Invoices.InvoiceEmail email) {
    try {
      Properties properties = new Properties();
      Session session = Session.getDefaultInstance(properties, null);

      MimeMessage mimeMessage = new MimeMessage(session);

      mimeMessage.setFrom(new InternetAddress(user, "Byakko Kyudojo Treasurer | 白虎弓道場会計"));
      mimeMessage.addRecipient(MimeMessage.RecipientType.TO, new InternetAddress(email.emailTo()));
      mimeMessage.setSubject("Byakko Kyudojo membership dues - Invoice");
      mimeMessage.setText(email.emailText());

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      mimeMessage.writeTo(buffer);
      byte[] bytes = buffer.toByteArray();
      String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
      com.google.api.services.gmail.model.Message message = new Message();
      message.setRaw(encodedEmail);

      Draft draft = new Draft();
      draft.setMessage(message);
      draft = client.users().drafts().create(user, draft).execute();

      client.users().drafts().send(user, draft).execute();
    } catch (Exception e) {
      System.err.println(
          "May have failed sending email to: " + email.emailTo() + "\n" + email.emailText() +
              "\n\n");
    }
  }
}
