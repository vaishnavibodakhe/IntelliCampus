import nodemailer from 'nodemailer';

/**
 * Configure Nodemailer transporter from environment variables
 */
function createTransporter() {
  const host = process.env.EMAIL_HOST || 'smtp.gmail.com';
  const port = parseInt(process.env.EMAIL_PORT || '587', 10);
  const user = process.env.EMAIL_USER;
  const pass = process.env.EMAIL_PASS;

  if (!user || !pass || user.includes('your_email@gmail.com') || pass.includes('your_gmail')) {
    return null;
  }

  return nodemailer.createTransport({
    host,
    port,
    secure: port === 465, // true for 465, false for other ports
    auth: {
      user,
      pass,
    },
  });
}

/**
 * Send password reset email
 * @param {Object} options
 * @param {string} options.to - Recipient email address
 * @param {string} options.name - Recipient full name
 * @param {string} options.role - 'faculty' | 'mentor'
 * @param {string} options.resetUrl - Full reset link URL
 */
export async function sendPasswordResetEmail({ to, name, role, resetUrl }) {
  const isFaculty = role.toLowerCase() === 'faculty';
  const portalName = isFaculty ? 'Faculty Portal' : 'Mentor & Guidance Portal';
  const accentColor = isFaculty ? '#06b6d4' : '#10b981';
  const fromAddress = process.env.EMAIL_FROM || `"IntelliCampus Security" <${process.env.EMAIL_USER || 'no-reply@intellicampus.edu'}>`;

  const htmlContent = `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Reset Your IntelliCampus Password</title>
  <style>
    body {
      margin: 0;
      padding: 0;
      background-color: #0f172a;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      color: #e2e8f0;
    }
    .wrapper {
      width: 100%;
      table-layout: fixed;
      background-color: #0f172a;
      padding: 40px 0;
    }
    .card {
      max-width: 580px;
      margin: 0 auto;
      background-color: #1e293b;
      border: 1px solid #334155;
      border-radius: 16px;
      padding: 36px;
      box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.5);
    }
    .header {
      text-align: center;
      margin-bottom: 28px;
    }
    .badge {
      display: inline-block;
      padding: 6px 14px;
      border-radius: 9999px;
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      color: ${accentColor};
      background: rgba(${isFaculty ? '6, 182, 212' : '16, 185, 129'}, 0.12);
      border: 1px solid ${accentColor};
      margin-bottom: 12px;
    }
    .brand-title {
      font-size: 24px;
      font-weight: 800;
      color: #f8fafc;
      margin: 0 0 6px;
    }
    .content-box {
      background: #0f172a;
      border: 1px solid #1e293b;
      border-radius: 12px;
      padding: 24px;
      margin: 20px 0;
    }
    .btn-container {
      text-align: center;
      margin: 30px 0;
    }
    .btn {
      display: inline-block;
      padding: 14px 32px;
      background-color: ${accentColor};
      color: #090d16 !important;
      text-decoration: none;
      font-weight: 700;
      font-size: 15px;
      border-radius: 8px;
      letter-spacing: 0.02em;
    }
    .expiry-note {
      font-size: 13px;
      color: #94a3b8;
      line-height: 1.5;
      text-align: center;
      margin-top: 20px;
    }
    .url-fallback {
      word-break: break-all;
      font-size: 12px;
      color: #38bdf8;
      background: #111827;
      padding: 10px;
      border-radius: 6px;
      margin-top: 12px;
    }
    .footer {
      text-align: center;
      font-size: 12px;
      color: #64748b;
      margin-top: 30px;
      border-top: 1px solid #334155;
      padding-top: 20px;
    }
  </style>
</head>
<body>
  <div class="wrapper">
    <div class="card">
      <div class="header">
        <span class="badge">${portalName}</span>
        <h1 class="brand-title">IntelliCampus Security</h1>
        <p style="margin: 0; color: #94a3b8; font-size: 14px;">Password Reset Authentication Request</p>
      </div>

      <div class="content-box">
        <p style="margin-top: 0; font-size: 15px; line-height: 1.6;">Hello <strong>${name}</strong>,</p>
        <p style="font-size: 14px; line-height: 1.6; color: #cbd5e1;">
          We received a request to reset the password for your <strong>${portalName}</strong> account associated with this email address (<code>${to}</code>).
        </p>
        <p style="font-size: 14px; line-height: 1.6; color: #cbd5e1;">
          Click the button below to establish a new password for your account:
        </p>

        <div class="btn-container">
          <a href="${resetUrl}" target="_blank" class="btn">Reset Password</a>
        </div>

        <p class="expiry-note">
          ⚠️ <strong>This link is valid for 15 minutes</strong> and can only be used once. If you did not request this password reset, please disregard this email or report it to your department administrator immediately.
        </p>

        <p style="font-size: 12px; color: #94a3b8; margin-top: 20px;">
          If the button above does not work, copy and paste this link into your web browser:
        </p>
        <div class="url-fallback">${resetUrl}</div>
      </div>

      <div class="footer">
        <p style="margin: 0 0 6px;">IntelliCampus Higher Education Academic Management Platform</p>
        <p style="margin: 0;">This is an automated system notification. Please do not reply directly to this email.</p>
      </div>
    </div>
  </div>
</body>
</html>
  `;

  const transporter = createTransporter();

  if (!transporter) {
    console.log('\n=============================================================');
    console.log(`[Mailer Mock/Dev Notice] Real SMTP is not configured in .env`);
    console.log(`Email intended for: ${to} (${name}) [${role.toUpperCase()}]`);
    console.log(`Reset Link: ${resetUrl}`);
    console.log('To send real emails, set EMAIL_USER and EMAIL_PASS in your .env');
    console.log('=============================================================\n');
    return { sent: false, mock: true, resetUrl };
  }

  try {
    const info = await transporter.sendMail({
      from: fromAddress,
      to,
      subject: `[IntelliCampus] Password Reset Request - ${portalName}`,
      html: htmlContent,
      text: `Hello ${name},\n\nYou requested to reset your password for the ${portalName}.\n\nReset your password here: ${resetUrl}\n\nThis link is valid for 15 minutes.\n\nIf you did not request this, please ignore this email.`,
    });
    console.log(`[Mailer] Reset email delivered to ${to} (Message ID: ${info.messageId})`);
    return { sent: true, messageId: info.messageId };
  } catch (error) {
    console.error(`[Mailer Error] Failed to send email to ${to}:`, error.message);
    throw error;
  }
}
