package net.taskwolf.access.verification;

import net.taskwolf.core.mail.TaskwolfMail;

import java.util.UUID;

public final class VerificationMail extends TaskwolfMail {
  public static VerificationMail create(
    String verificationMailHost, String verificationMail,
    String verificationMailPassword, String target, String username, UUID userId,
    String verificationToken
  ) {
    return new VerificationMail(verificationMailHost, verificationMail,
      verificationMailPassword, target, username, userId, verificationToken);
  }

  private final String username;
  private final UUID userId;
  private final String verificationToken;

  private VerificationMail(
    String verificationMailHost, String verificationMail,
    String verificationMailPassword, String target, String username, UUID userId,
    String verificationToken
  ) {
    super(verificationMailHost, verificationMail, verificationMailPassword, target);
    this.username = username;
    this.userId = userId;
    this.verificationToken = verificationToken;
  }

  private static final String EMAIL_TITLE = "Verification";

  @Override
  protected String emailTitle() {
    return EMAIL_TITLE;
  }

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

  @Override
  protected String emailBody() {
    return String.format(EMAIL_BODY, username, String.format(VERIFICATION_URL,
      userId.toString(), verificationToken));
  }
}
