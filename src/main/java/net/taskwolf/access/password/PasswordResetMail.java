package net.taskwolf.access.password;

import net.taskwolf.core.mail.TaskwolfMail;

import java.util.UUID;

public final class PasswordResetMail extends TaskwolfMail {
  public static PasswordResetMail create(
    String verificationMailHost, String verificationMail,
    String verificationMailPassword, String target, String username, UUID userId,
    String passwordResetToken
  ) {
    return new PasswordResetMail(verificationMailHost, verificationMail,
      verificationMailPassword, target, username, userId, passwordResetToken);
  }

  private final String username;
  private final UUID userId;
  private final String passwordResetToken;

  private PasswordResetMail(
    String verificationMailHost, String verificationMail,
    String verificationMailPassword, String target, String username, UUID userId,
    String passwordResetToken
  ) {
    super(verificationMailHost, verificationMail, verificationMailPassword, target);
    this.username = username;
    this.userId = userId;
    this.passwordResetToken = passwordResetToken;
  }

  private static final String EMAIL_TITLE = "Password Reset";

  @Override
  protected String emailTitle() {
    return EMAIL_TITLE;
  }

  private static final String PASSWORD_RESET_URL = "https://taskwolf.net/password/reset/complete/%s/%s/";
  private static final String EMAIL_BODY = "Hey %s,\n" +
    "\n" +
    "there was a request to change your password!\n" +
    "\n" +
    "If you did not make this request then please ignore this email.\n" +
    "\n" +
    "Otherwise, please click this link to change your password:\n" +
    "\n" +
    "%s";

  @Override
  protected String emailBody() {
    return String.format(EMAIL_BODY, username, String.format(PASSWORD_RESET_URL,
      userId.toString(), passwordResetToken));
  }
}