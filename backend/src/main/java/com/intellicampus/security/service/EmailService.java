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

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.mail-from:noreply@intellicampus.edu}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    public EmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Dispatches a 6-digit OTP to the registered user's email address via SMTP.
     * Matches the signature and behavior of origin/main.
     */
    public void sendPasswordResetOtp(String toEmail, String otp) {
        boolean isSmtpConfigured = mailSender != null && smtpUsername != null && !smtpUsername.trim().isEmpty();

        if (!isSmtpConfigured) {
            // Development fallback only when SMTP_USERNAME is empty: print the OTP in the backend console with a clear warning
            log.warn("=================================================================");
            log.warn("[DEV EMAIL FALLBACK] SMTP not configured (SMTP_USERNAME is empty).");
            log.warn("[DEV EMAIL FALLBACK] Password Reset OTP for {}: {}", toEmail, otp);
            log.warn("=================================================================");
            return;
        }

        try {
            org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
            if (mailFrom != null && !mailFrom.isBlank()) {
                message.setFrom(mailFrom);
            }
            message.setTo(toEmail);
            message.setSubject("IntelliCampus password reset OTP");
            message.setText("Your IntelliCampus password reset code is " + otp + ". It is valid for 10 minutes. If you did not request this, ignore this email.");

            mailSender.send(message);
            log.info("Password reset OTP email successfully sent to '{}'", toEmail);
        } catch (Exception ex) {
            log.error("Failed to send password reset OTP email to '{}': {}. Falling back to console logging for development.", toEmail, ex.getMessage());
            log.warn("=================================================================");
            log.warn("[DEV EMAIL FALLBACK] SMTP delivery failed ({}).", ex.getMessage());
            log.warn("[DEV EMAIL FALLBACK] Password Reset OTP for {}: {}", toEmail, otp);
            log.warn("=================================================================");
        }
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
