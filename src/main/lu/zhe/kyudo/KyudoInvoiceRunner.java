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
  public static void main(String[] args) {
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

    public Gui(KyudoInvoiceOptions options) {
      setTitle("Byakko Kyudojo Invoice Generator");
      setSize(/* width= */ 400, /* height = */300);
      setLayout(new GridLayout(10, 1));
      setResizable(false);

      Label waiversLabel = new Label("Waivers path:");
      Button waiversButton = new Button("Choose \"waivers\" file");
      Label owedLabel = new Label("Owed path:");
      Button owedButton = new Button("Choose \"owed\" file");
      add(waiversLabel);
      add(waiversButton);
      add(owedLabel);
      add(owedButton);

      Button sendEmailsButton = new Button("Compute invoices and send emails");
      Button printEmailsButton = new Button("Show test emails");
      sendEmailsButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          sendEmailsButton.setEnabled(false);
          printEmailsButton.setEnabled(false);
          try {
            KyudoInvoices invoices = KyudoInvoices.create(options,
                owedDirectory.get() + owedFile.get(),
                waiversDirectory.get() + waiversFile.get());
            invoices.run(waiversDirectory.get(), owedDirectory.get());
            System.exit(0);
          } catch (IOException | GeneralSecurityException | ApiException ex) {
            ex.printStackTrace();
            System.exit(1);
          }
        }
      });
      printEmailsButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          sendEmailsButton.setEnabled(false);
          printEmailsButton.setEnabled(false);
          try {
            KyudoInvoices invoices = KyudoInvoices.create(options,
                owedDirectory.get() + owedFile.get(),
                waiversDirectory.get() + waiversFile.get());
            invoices.runTest();
            System.exit(0);
          } catch (IOException | GeneralSecurityException | ApiException ex) {
            ex.printStackTrace();
            System.exit(1);
          }
        }
      });
      sendEmailsButton.setEnabled(false);
      printEmailsButton.setEnabled(false);
      add(sendEmailsButton);
      add(printEmailsButton);

      waiversButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          FileDialog fileDialog = new FileDialog(Gui.this, "Select \"waivers\" file.");
          fileDialog.setDirectory(options.basePath);
          fileDialog.setVisible(true);
          if (fileDialog.getDirectory() != null && fileDialog.getFile() != null) {
            waiversDirectory.set(fileDialog.getDirectory());
            waiversFile.set(fileDialog.getFile());
            waiversLabel.setText(
                "Waivers path: " + fileDialog.getDirectory() + fileDialog.getFile());
            if (waiversDirectory.get() != null && waiversFile.get() != null &&
                owedDirectory.get() != null && owedFile.get() != null) {
              sendEmailsButton.setEnabled(true);
              printEmailsButton.setEnabled(true);
            }
          }
        }
      });
      owedButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          FileDialog fileDialog = new FileDialog(Gui.this, "Select \"owed\" file.");
          fileDialog.setDirectory(options.basePath);
          fileDialog.setVisible(true);
          if (fileDialog.getDirectory() != null && fileDialog.getFile() != null) {
            owedDirectory.set(fileDialog.getDirectory());
            owedFile.set(fileDialog.getFile());
            owedLabel.setText("Owed path: " + fileDialog.getDirectory() + fileDialog.getFile());
            if (waiversDirectory.get() != null && waiversFile.get() != null &&
                owedDirectory.get() != null && owedFile.get() != null) {
              sendEmailsButton.setEnabled(true);
              printEmailsButton.setEnabled(true);
            }
          }
        }
      });

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
