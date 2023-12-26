package net.taskwolf.access.verification;

import lombok.RequiredArgsConstructor;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.UUID;

@RequiredArgsConstructor(staticName = "create")
public final class VerificationMail {
  private final String verificationMailHost;
  private final String verificationMail;
  private final String verificationMailPassword;
  private final String email;
  private final String username;
  private final UUID userId;
  private final String verificationToken;

  private static final String EMAIL_TITLE = "Verification";
  private static final String VERIFICATION_URL = "https://taskwolf.net/register/confirm/%s/%s/";
  private static final String EMAIL_BODY = "Hey %s,\n" +
    "\n" +
    "We’re excited to welcome you to Taskwolf! Before you begin your " +
    "journey, we need to verify your account. Follow these steps to complete " +
    "the verification process:\n" +
    "\n" +
    "Click the link below to verify your account:\n" +
    "%s\n" +
    "\n" +
    "After verification, you’ll have access to all the amazing features on Taskwolf.\n" +
    "\n" +
    "If you encounter any issues or have questions, our support team is here to help. Simply reply to this email or reach out to us at support@taskwolf.net\n" +
    "\n" +
    "Welcome aboard!";

  public void send() {
    new Thread(() -> sendEmail(email, EMAIL_TITLE, String.format(EMAIL_BODY,
      username, String.format(VERIFICATION_URL, userId.toString(),
        verificationToken)))).start();
  }

  private void sendEmail(String target, String title, String body) {
    try {
      var session = createEmailSession();
      var message = createMessage(session, target, title, body);
      var transport = session.getTransport("smtp");
      transport.connect(verificationMailHost, verificationMail, verificationMailPassword);
      transport.sendMessage(message, message.getAllRecipients());
      transport.close();
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private Session createEmailSession() {
    var props = System.getProperties();
    props.put("mail.smtp.host", verificationMailHost);
    props.put("mail.smtp.port", "465");
    props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
    var session = Session.getDefaultInstance(props, null);
    session.setDebug(false);
    return session;
  }

  private Message createMessage(Session session, String target, String title, String body) throws Exception {
    var message = new MimeMessage(session);
    message.setFrom(new InternetAddress(verificationMail, "Taskwolf"));
    var address = new InternetAddress[]{new InternetAddress(target)};
    message.setRecipients(Message.RecipientType.TO, address);
    message.setSubject(title);
    message.setText(body);
    message.setSentDate(new Date());
    return message;
  }
}
