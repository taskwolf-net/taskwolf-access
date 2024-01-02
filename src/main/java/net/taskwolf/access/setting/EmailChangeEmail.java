package net.taskwolf.access.setting;

import net.taskwolf.core.mail.TaskwolfMail;

import java.util.UUID;

public final class EmailChangeEmail extends TaskwolfMail {
  public static EmailChangeEmail create(
    String verificationMailHost, String verificationMail,
    String verificationMailPassword, String target, UUID userId,
    String emailChangeToken
  ) {
    return new EmailChangeEmail(verificationMailHost, verificationMail,
      verificationMailPassword, target, userId, emailChangeToken);
  }

  private final UUID userId;
  private final String emailChangeToken;

  private EmailChangeEmail(
    String verificationMailHost, String verificationMail,
    String verificationMailPassword, String target, UUID userId,
    String emailChangeToken
  ) {
    super(verificationMailHost, verificationMail, verificationMailPassword, target);
    this.userId = userId;
    this.emailChangeToken = emailChangeToken;
  }

  private static final String EMAIL_TITLE = "Email Change";

  @Override
  protected String emailTitle() {
    return EMAIL_TITLE;
  }

  private static final String PASSWORD_RESET_URL = "https://taskwolf.net/email/change/complete/%s/%s/";
  private static final String EMAIL_BODY = "Hey, \n" +
    "\n" +
    "we have received a request to replace the email of one of our accounts with this email.\n" +
    "\n" +
    "If you are not a Taskwolf customer or have not requested the replacement, please ignore this email.\n" +
    "\n" +
    "However, if this is a genuine request, please click on the link below to complete the change:\n" +
    "\n" +
    "%s";

  @Override
  protected String emailBody() {
    return String.format(EMAIL_BODY, String.format(PASSWORD_RESET_URL,
      userId.toString(), emailChangeToken));
  }
}