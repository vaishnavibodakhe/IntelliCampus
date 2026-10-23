package com.intellicampus.auth.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service("authEmailService")
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String fromEmail;
    private final String mailHost;
    private final int mailPort;
    private final String mailPassword;

    public EmailService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${app.frontend.url:${APP_FRONTEND_URL:http://localhost:5173}}") String frontendUrl,
            @Value("${spring.mail.username:${MAIL_USERNAME:}}") String fromEmail,
            @Value("${spring.mail.host:${MAIL_HOST:smtp.gmail.com}}") String mailHost,
            @Value("${spring.mail.port:${MAIL_PORT:587}}") int mailPort,
            @Value("${spring.mail.password:${MAIL_PASSWORD:}}") String mailPassword) {
        this.mailSender = mailSender;
        this.frontendUrl = (frontendUrl != null ? frontendUrl : "http://localhost:5173").replaceAll("/+$", "");
        this.fromEmail = fromEmail != null ? fromEmail.trim() : "";
        this.mailHost = mailHost != null ? mailHost.trim() : "smtp.gmail.com";
        this.mailPort = mailPort;
        this.mailPassword = mailPassword != null ? mailPassword.trim() : "";
    }

    @PostConstruct
    public void logEmailConfig() {
        boolean configured = isMailConfigured();
        logger.info("[SMTP CONFIG] SMTP configured: {}, host: {}:{}", configured, mailHost, mailPort);
        runStartupSmtpCheck();
    }

    public void runStartupSmtpCheck() {
        if (!isMailConfigured()) {
            logger.info("SMTP auth check: FAILED (missing credentials in .env)");
            return;
        }

        if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl senderImpl) {
            try {
                senderImpl.testConnection();
                logger.info("SMTP auth check: OK");
            } catch (Exception ex) {
                String code = extractSmtpCode(ex);
                logger.warn("SMTP auth check: FAILED ({})", code);
            }
        } else {
            logger.info("SMTP auth check: SKIPPED (Custom mail sender)");
        }
    }

    private String extractSmtpCode(Exception ex) {
        String msg = ex.getMessage();
        if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            msg = ex.getCause().getMessage();
        }
        if (msg == null) return ex.getClass().getSimpleName();
        if (msg.contains("535")) return "535 Authentication Failed";
        if (msg.contains("534")) return "534 Application-specific password required";
        if (msg.contains("Timed out") || msg.toLowerCase().contains("timeout")) return "Connection Timeout";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{3})\\b").matcher(msg);
        if (m.find()) {
            return m.group(1);
        }
        return msg.length() > 50 ? msg.substring(0, 50).trim() : msg.trim();
    }

    /**
     * Dispatches a 6-digit OTP to the registered user's email address via SMTP.
     */
    public void sendPrincipalRegistrationOtp(String toEmail, String otp) {
        if (!isMailConfigured()) {
            logger.warn("========================================================================");
            logger.warn("[DEV MODE OTP] SMTP is not configured! Principal registration OTP for '{}': {}", toEmail, otp);
            logger.warn("Use this 6-digit OTP to complete your first-run Principal setup.");
            logger.warn("========================================================================");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("IntelliCampus: Principal Account Activation OTP");
            message.setText("Dear Principal,\n\nYour IntelliCampus account registration OTP is: " + otp
                    + "\n\nThis verification code will expire in 15 minutes. Please enter it in the setup screen to activate your administrator console.\n\nRegards,\nIntelliCampus Governance System");
            mailSender.send(message);
            logger.info("Principal registration OTP email successfully sent to '{}'", toEmail);
        } catch (Exception e) {
            logger.warn("========================================================================");
            logger.warn("[FALLBACK OTP] SMTP send failed ({}). Registration OTP for '{}': {}", e.getMessage(), toEmail, otp);
            logger.warn("========================================================================");
        }
    }

    /**
     * Dispatches a 6-digit OTP to the registered user's email address via SMTP.
     */
    public void sendPasswordResetOtp(String toEmail, String otp) {
        if (!isMailConfigured()) {
            logger.error("SMTP service is not configured. Cannot send password reset OTP to {}", toEmail);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "SMTP service is not configured. Please set MAIL_USERNAME and MAIL_PASSWORD in your .env file.");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("IntelliCampus password reset OTP");
            message.setText("Your IntelliCampus password reset OTP is " + otp + ". Valid for 10 minutes. Do not share it.");
            mailSender.send(message);
            logger.info("Password reset OTP email successfully sent to '{}'", toEmail);
        } catch (MailAuthenticationException e) {
            String reason = e.getMessage();
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                reason = e.getCause().getMessage();
            }
            logger.error("SMTP delivery failed: Authentication failure for user '{}' - {}", fromEmail, reason);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Email service authentication failed. Check the Gmail App Password.", e);
        } catch (MailSendException e) {
            String errorDetail = e.getMessage();
            if (e.getRootCause() != null && e.getRootCause().getMessage() != null) {
                errorDetail = e.getRootCause().getMessage();
            } else if (e.getCause() != null && e.getCause().getMessage() != null) {
                errorDetail = e.getCause().getMessage();
            }
            logger.error("SMTP delivery failed: {}", errorDetail, e);
            if (errorDetail != null && (errorDetail.contains("535")
                    || errorDetail.toLowerCase().contains("authentication failed")
                    || errorDetail.toLowerCase().contains("bad credentials")
                    || errorDetail.toLowerCase().contains("username and password not accepted"))) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Email service authentication failed. Check the Gmail App Password.", e);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to deliver OTP email via SMTP: " + (errorDetail != null ? errorDetail : e.getMessage()), e);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            String reason = e.getMessage();
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                reason = e.getCause().getMessage();
            }
            logger.error("SMTP delivery failed: {}", reason, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to deliver OTP email via SMTP: " + e.getMessage(), e);
        }
    }

    /**
     * Legacy token-based reset email dispatch.
     */
    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        String resetLink = frontendUrl + "/?view=reset-password&token=" + rawToken;

        if (mailSender == null) {
            logger.info("=================================================================");
            logger.info("[DEV EMAIL] SMTP not configured. Password Reset Link for {}:", toEmail);
            logger.info("[DEV EMAIL] {}", resetLink);
            logger.info("=================================================================");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (!fromEmail.isEmpty()) {
                message.setFrom(fromEmail);
            }
            message.setTo(toEmail);
            message.setSubject("IntelliCampus - Password Reset Request");
            message.setText("Hello,\n\nYou recently requested to reset your password for your IntelliCampus account.\n\n"
                    + "Please click the link below or copy and paste it into your browser to set a new password:\n"
                    + resetLink + "\n\n"
                    + "This link will expire in 15 minutes and can only be used once.\n\n"
                    + "If you did not request this, please disregard this email or contact campus IT security immediately.\n\n"
                    + "Regards,\nIntelliCampus Security Administration");
            mailSender.send(message);
            logger.info("Password reset email successfully dispatched to {}", toEmail);
        } catch (Exception e) {
            logger.warn("Failed to send SMTP email to {}. Falling back to console logging: {}", toEmail, e.getMessage());
            logger.info("[DEV EMAIL FALLBACK] Password Reset Link for {}: {}", toEmail, resetLink);
        }
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public boolean isMailConfigured() {
        return mailSender != null && !fromEmail.isEmpty() && !mailPassword.isEmpty();
    }

    public void sendStaffWelcomeEmail(String toEmail, String name, String tempPassword) {
        if (!isMailConfigured()) {
            logger.info("SMTP service not configured. Staff welcome email skipped for {}", toEmail);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (!fromEmail.isEmpty()) {
                message.setFrom(fromEmail);
            }
            message.setTo(toEmail);
            message.setSubject("Welcome to IntelliCampus - Your Temporary Credentials");
            message.setText("Hello " + name + ",\n\n"
                    + "An institutional staff account has been created for you on IntelliCampus.\n\n"
                    + "Your temporary credentials:\n"
                    + "Username/Email: " + toEmail + "\n"
                    + "Temporary Password: " + tempPassword + "\n\n"
                    + "For security reasons, you will be required to change this password immediately upon your first login.\n"
                    + "Login portal: " + frontendUrl + "\n\n"
                    + "Regards,\nIntelliCampus Administration");
            mailSender.send(message);
            logger.info("Staff welcome credentials successfully dispatched via email to {}", toEmail);
        } catch (Exception e) {
            logger.error("Failed to deliver staff welcome credentials email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendStaffPasswordResetEmail(String toEmail, String name, String tempPassword) {
        if (!isMailConfigured()) {
            logger.info("SMTP service not configured. Staff password reset email skipped for {}", toEmail);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (!fromEmail.isEmpty()) {
                message.setFrom(fromEmail);
            }
            message.setTo(toEmail);
            message.setSubject("IntelliCampus - Temporary Password Reset");
            message.setText("Hello " + name + ",\n\n"
                    + "An administrator has reset your IntelliCampus account password.\n\n"
                    + "Your temporary password is: " + tempPassword + "\n\n"
                    + "You will be required to change this password immediately upon your next login.\n"
                    + "Login portal: " + frontendUrl + "\n\n"
                    + "Regards,\nIntelliCampus Administration");
            mailSender.send(message);
            logger.info("Staff temporary password reset successfully dispatched via email to {}", toEmail);
        } catch (Exception e) {
            logger.error("Failed to deliver staff temporary password reset email to {}: {}", toEmail, e.getMessage());
        }
    }
}
