package lu.zhe.kyudo;

import com.google.devtools.common.options.*;
import com.squareup.square.exceptions.*;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.security.*;
import java.util.concurrent.atomic.*;

/** Entry point for the invoice generator. */
public class KyudoInvoiceRunner {
  public static void main(String[] args) throws IOException, GeneralSecurityException {
    OptionsParser parser = OptionsParser.newOptionsParser(KyudoInvoiceOptions.class);
    parser.parseAndExitUponError(args);
    KyudoInvoiceOptions options = parser.getOptions(KyudoInvoiceOptions.class);

    new Gui(options).start();
  }

  private static class Gui extends Frame {
    private final AtomicReference<String> waiversDirectory = new AtomicReference<>();
    private final AtomicReference<String> waiversFile = new AtomicReference<>();
    private final AtomicReference<String> owedDirectory = new AtomicReference<>();
    private final AtomicReference<String> owedFile = new AtomicReference<>();

    private final KyudoInvoices invoices;

    public Gui(KyudoInvoiceOptions options) throws IOException, GeneralSecurityException {
      setTitle("Byakko Kyudojo Invoice Generator");
      setSize(/* width= */ 400, /* height = */300);
      setLayout(new GridLayout(10, 1));
      setResizable(false);

      invoices = KyudoInvoices.create(options);

      Button fillSpreadsheet = new Button("Compute invoices and fill spreadsheet");
      Button emailButton = new Button("Send emails");
      Checkbox printEmailCheckbox = new Checkbox("Print emails");
      printEmailCheckbox.setState(false);
      fillSpreadsheet.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          try {
            invoices.fillSpreadsheet();
            System.exit(0);
          } catch (IOException | ApiException ex) {
            ex.printStackTrace();
            System.exit(1);
          }
        }
      });
      emailButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          try {
            if (printEmailCheckbox.getState()) {
              invoices.printEmails();
            } else {
              invoices.sendEmails();
            }
            System.exit(0);
          } catch (IOException | ApiException ex) {
            ex.printStackTrace();
            System.exit(1);
          }
        }
      });
      add(fillSpreadsheet);
      add(emailButton);
      add(printEmailCheckbox);

      addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          System.exit(0);
        }
      });
    }

    public void start() {
      setVisible(true);
    }
  }
}
