package com.intellicampus.security.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service("securityEmailService")
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.mail-from:noreply@intellicampus.edu}")
    private String mailFrom;

    @Value("${spring.mail.username:${MAIL_USERNAME:}}")
    private String mailUsername;

    public EmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Dispatches a 6-digit OTP to the registered user's email address via SMTP.
     * Uses HTML email formatting and strict delivery confirmation.
     */
    public void sendPasswordResetOtp(String toEmail, String otp) {
        boolean isSmtpConfigured = mailSender != null && mailUsername != null && !mailUsername.trim().isEmpty();

        if (!isSmtpConfigured) {
            log.error("SMTP delivery failed: SMTP service is not configured (mailSender is null or mail username is empty)");
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to dispatch verification code: Email delivery service is not configured. Please ensure MAIL_USERNAME and MAIL_PASSWORD are set.");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // For Gmail SMTP, the From address should match the authenticated account
            String senderEmail = (mailUsername != null && !mailUsername.isBlank()) ? mailUsername.trim() : mailFrom;
            try {
                helper.setFrom(new jakarta.mail.internet.InternetAddress(senderEmail, "IntelliCampus Academic Portal"));
            } catch (Exception e) {
                helper.setFrom(senderEmail);
            }

            helper.setTo(toEmail);
            helper.setSubject("IntelliCampus - Password Reset Verification Code");

            String htmlBody = buildOtpEmailHtml(otp);
            String textFallback = "Your IntelliCampus password reset verification code is: " + otp
                    + "\n\nThis code is valid for 10 minutes. If you did not request this, please ignore this email.";
            helper.setText(textFallback, htmlBody);

            mailSender.send(message);
            log.info("Password reset OTP email successfully sent to '{}'", toEmail);
        } catch (org.springframework.web.server.ResponseStatusException rse) {
            throw rse;
        } catch (org.springframework.mail.MailAuthenticationException mae) {
            String reason = mae.getMessage();
            if (mae.getCause() != null && mae.getCause().getMessage() != null) {
                reason = mae.getCause().getMessage();
            }
            log.error("SMTP delivery failed: Authentication failure for user '{}' - {}", mailUsername, reason);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to dispatch verification code: SMTP authentication failed (" + reason + ")");
        } catch (org.springframework.mail.MailSendException mse) {
            String reason = mse.getMessage();
            if (mse.getRootCause() != null && mse.getRootCause().getMessage() != null) {
                reason = mse.getRootCause().getMessage();
            } else if (mse.getCause() != null && mse.getCause().getMessage() != null) {
                reason = mse.getCause().getMessage();
            }
            log.error("SMTP delivery failed: {}", reason, mse);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to dispatch verification code: " + (reason != null ? reason : "SMTP delivery error"));
        } catch (Exception ex) {
            String reason = ex.getMessage();
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                reason = ex.getCause().getMessage();
            }
            log.error("SMTP delivery failed: {}", reason, ex);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to dispatch verification code: " + (reason != null ? reason : "SMTP delivery error"));
        }
    }

    private String buildOtpEmailHtml(String otp) {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='UTF-8'></head>"
                + "<body style='font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif; background-color: #0f172a; color: #f8fafc; padding: 40px 20px; margin: 0;'>"
                + "  <table width='100%' border='0' cellspacing='0' cellpadding='0' style='max-width: 560px; margin: 0 auto; background-color: #1e293b; border-radius: 12px; border: 1px solid #334155; overflow: hidden;'>"
                + "    <tr>"
                + "      <td style='padding: 32px 32px 24px 32px; background: linear-gradient(135deg, #1e1b4b 0%, #0f172a 100%); border-bottom: 1px solid #334155;'>"
                + "        <h1 style='margin: 0; font-size: 22px; color: #60a5fa;'>IntelliCampus</h1>"
                + "        <p style='margin: 4px 0 0 0; font-size: 13px; color: #94a3b8;'>Academic Governance &amp; Mentorship Portal</p>"
                + "      </td>"
                + "    </tr>"
                + "    <tr>"
                + "      <td style='padding: 32px;'>"
                + "        <h2 style='margin: 0 0 16px 0; font-size: 18px; color: #f1f5f9;'>Password Reset Verification Code</h2>"
                + "        <p style='font-size: 14px; line-height: 1.6; color: #cbd5e1; margin-bottom: 24px;'>"
                + "          We received a request to reset your password. Use the 6-digit verification code below to complete your password reset:"
                + "        </p>"
                + "        <div style='text-align: center; margin: 28px 0; padding: 20px; background-color: #0f172a; border-radius: 8px; border: 2px dashed #3b82f6;'>"
                + "          <span style='font-family: \"SFMono-Regular\", Consolas, \"Liberation Mono\", Menlo, Courier, monospace; font-size: 36px; font-weight: 700; letter-spacing: 10px; color: #60a5fa;'>"
                +             otp
                + "          </span>"
                + "        </div>"
                + "        <p style='font-size: 13px; line-height: 1.6; color: #94a3b8; margin: 16px 0 24px 0; text-align: center;'>"
                + "          <strong>Validity:</strong> 10 minutes &bull; <strong>Max Attempts:</strong> 5"
                + "        </p>"
                + "        <p style='font-size: 12px; line-height: 1.6; color: #64748b; margin-top: 24px; border-top: 1px solid #334155; padding-top: 16px;'>"
                + "          If you did not request a password reset, you can safely ignore this email. Never share this code with anyone, including campus administrators."
                + "        </p>"
                + "      </td>"
                + "    </tr>"
                + "  </table>"
                + "</body>"
                + "</html>";
    }

    public void sendPasswordResetEmail(String toEmail, String recipientName, String rawToken, String role) {
        String roleSegment = (role != null && role.equalsIgnoreCase("MENTOR")) ? "mentor" : "faculty";
        String resetUrl = String.format("%s/%s/reset-password/%s", frontendUrl, roleSegment, rawToken);

        log.info("Sending password reset email to '{}'. Reset URL: {}", toEmail, resetUrl);

        if (mailSender == null) {
            log.warn("JavaMailSender bean is not configured. Reset link for {}: {}", toEmail, resetUrl);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject("IntelliCampus - Password Reset Request");

            String htmlContent = buildResetPasswordEmailHtml(recipientName, resetUrl);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Password reset email successfully sent to '{}'", toEmail);
        } catch (Exception ex) {
            log.error("Failed to send password reset email to '{}': {}", toEmail, ex.getMessage());
            // Log fallback reset link in server console for local testing / development
            log.info("LOCAL DEV FALLBACK - Password Reset Link for {}: {}", toEmail, resetUrl);
        }
    }

    private String buildResetPasswordEmailHtml(String name, String resetUrl) {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='UTF-8'></head>"
                + "<body style='font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif; background-color: #0f172a; color: #f8fafc; padding: 40px 20px;'>"
                + "  <table width='100%' border='0' cellspacing='0' cellpadding='0' style='max-width: 560px; margin: 0 auto; background-color: #1e293b; border-radius: 12px; border: 1px solid #334155; overflow: hidden;'>"
                + "    <tr>"
                + "      <td style='padding: 32px 32px 24px 32px; background: linear-gradient(135deg, #1e1b4b 0%, #0f172a 100%); border-bottom: 1px solid #334155;'>"
                + "        <h1 style='margin: 0; font-size: 22px; color: #60a5fa;'>IntelliCampus</h1>"
                + "        <p style='margin: 4px 0 0 0; font-size: 13px; color: #94a3b8;'>Academic Governance & Mentorship Portal</p>"
                + "      </td>"
                + "    </tr>"
                + "    <tr>"
                + "      <td style='padding: 32px;'>"
                + "        <h2 style='margin: 0 0 16px 0; font-size: 18px; color: #f1f5f9;'>Password Reset Request</h2>"
                + "        <p style='font-size: 14px; line-height: 1.6; color: #cbd5e1; margin-bottom: 24px;'>"
                + "          Hello " + (name != null ? name : "User") + ",<br><br>"
                + "          We received a request to reset your password for your IntelliCampus account. Click the button below to choose a new password. This link is single-use and will expire in <strong>15 minutes</strong>."
                + "        </p>"
                + "        <div style='text-align: center; margin: 32px 0;'>"
                + "          <a href='" + resetUrl + "' style='display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 12px 28px; border-radius: 8px; font-weight: 600; font-size: 15px; box-shadow: 0 4px 12px rgba(37,99,235,0.3);'>Reset My Password</a>"
                + "        </div>"
                + "        <p style='font-size: 12px; line-height: 1.6; color: #64748b; margin-top: 32px; border-top: 1px solid #334155; padding-top: 16px;'>"
                + "          If you did not request a password reset, you can safely ignore this email. Your password will remain unchanged.<br><br>"
                + "          If the button does not work, copy and paste this link into your browser:<br>"
                + "          <a href='" + resetUrl + "' style='color: #38bdf8; word-break: break-all;'>" + resetUrl + "</a>"
                + "        </p>"
                + "      </td>"
                + "    </tr>"
                + "  </table>"
                + "</body>"
                + "</html>";
    }
}
