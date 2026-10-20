import React, { useState, useEffect } from 'react';
import AuthLayout from './AuthLayout';
import { 
  Mail, 
  KeyRound,
  Lock,
  Eye,
  EyeOff,
  ArrowLeft, 
  ArrowRight, 
  CheckCircle2, 
  AlertCircle,
  RotateCcw,
  Check
} from 'lucide-react';
import { 
  isValidEmail, 
  isValidOtp,
  isPasswordValid,
  validatePasswordCriteria, 
  calculatePasswordStrength 
} from './authUtils';
import { useAuth } from '../../context/AuthContext';

export default function ForgotPasswordPage({
  role = 'FACULTY',
  apiEndpoint,
  onNavigateLogin
}) {
  const { navigate } = useAuth();
  const isFaculty = role.toUpperCase() === 'FACULTY';
  const resolvedRole = isFaculty ? 'FACULTY' : 'MENTOR';
  const roleClass = isFaculty ? 'faculty' : 'mentor';

  // Step state for Mentor: 1 = Email, 2 = Verify OTP, 3 = Reset Password, 4 = Success
  const [step, setStep] = useState(1);

  // Form Fields
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [resetToken, setResetToken] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // UI / Status states
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [touched, setTouched] = useState({});
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  // 60-Second Cooldown Countdown for OTP Resend
  const [countdown, setCountdown] = useState(60);
  const [canResend, setCanResend] = useState(false);

  useEffect(() => {
    let timer;
    if (step === 2 && countdown > 0) {
      timer = setInterval(() => {
        setCountdown((prev) => {
          if (prev <= 1) {
            setCanResend(true);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    }
    return () => {
      if (timer) clearInterval(timer);
    };
  }, [step, countdown]);

  const strength = calculatePasswordStrength(newPassword);
  const criteria = validatePasswordCriteria(newPassword);

  const handleReturnToLogin = () => {
    if (onNavigateLogin) {
      onNavigateLogin();
    } else {
      navigate(isFaculty ? '/faculty/login' : '/mentor/login');
    }
  };

  // =========================================================================
  // STEP 1: REQUEST OTP / RESET LINK
  // =========================================================================
  const handleRequestOtp = async (e) => {
    if (e) e.preventDefault();
    setTouched((prev) => ({ ...prev, email: true }));

    if (!isValidEmail(email) || isLoading) return;

    setIsLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    const targetUrl = apiEndpoint || (isFaculty ? '/api/faculty/forgot-password' : '/api/mentor/forgot-password');

    try {
      const response = await fetch(targetUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.trim().toLowerCase() }),
      });

      const data = await response.json().catch(() => ({}));

      if (!response.ok && data.message && !isFaculty) {
        setErrorMessage(data.message);
        return;
      }

      const defaultMsg = isFaculty
        ? 'If this email is registered in our system, a password reset link has been dispatched to your inbox.'
        : 'If this email is registered in our system, a 6-digit password reset code has been dispatched to your inbox.';

      setSuccessMessage(data.message || defaultMsg);

      if (isFaculty) {
        // Faculty uses the single-step link flow
        setStep(4);
      } else {
        // Mentor transitions to Step 2 (Verify OTP)
        setStep(2);
        setCountdown(60);
        setCanResend(false);
      }
    } catch (networkErr) {
      console.error('[Forgot Password Error]:', networkErr);
      setErrorMessage('Failed to connect to authentication server. Please verify backend status.');
    } finally {
      setIsLoading(false);
    }
  };

  // =========================================================================
  // STEP 2: VERIFY 6-DIGIT OTP
  // =========================================================================
  const handleVerifyOtp = async (e) => {
    if (e) e.preventDefault();
    setTouched((prev) => ({ ...prev, otp: true }));

    if (!isValidOtp(otp) || isLoading) return;

    setIsLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const response = await fetch('/api/mentor/verify-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: email.trim().toLowerCase(),
          otp: otp.trim()
        }),
      });

      const data = await response.json().catch(() => ({}));

      if (response.ok && data.success) {
        const token = (data.data && data.data.resetToken) || data.resetToken;
        if (!token) {
          setErrorMessage('Verification succeeded but reset token was not provided.');
          return;
        }
        setResetToken(token);
        setSuccessMessage('OTP code verified. Please set your new password.');
        setStep(3);
        return;
      }

      const extractedError = data.message || data.error || 'Invalid or expired OTP code.';
      setErrorMessage(extractedError);
    } catch (networkErr) {
      console.error('[Verify OTP Error]:', networkErr);
      setErrorMessage('Failed to connect to verification server. Please verify backend status.');
    } finally {
      setIsLoading(false);
    }
  };

  // Resend OTP handler for Step 2
  const handleResendOtp = async () => {
    if (!canResend || isLoading) return;
    setIsLoading(true);
    setErrorMessage('');
    try {
      const response = await fetch('/api/mentor/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.trim().toLowerCase() }),
      });
      const data = await response.json().catch(() => ({}));
      setSuccessMessage(data.message || 'A fresh 6-digit OTP code has been dispatched to your email.');
      setCountdown(60);
      setCanResend(false);
    } catch (networkErr) {
      setErrorMessage('Unable to resend OTP at this time. Please retry shortly.');
    } finally {
      setIsLoading(false);
    }
  };

  // =========================================================================
  // STEP 3: RESET PASSWORD
  // =========================================================================
  const handleResetPassword = async (e) => {
    if (e) e.preventDefault();
    setTouched((prev) => ({ ...prev, newPassword: true, confirmPassword: true }));

    if (!isPasswordValid(newPassword) || newPassword !== confirmPassword || isLoading) return;

    setIsLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const response = await fetch('/api/mentor/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          token: resetToken,
          newPassword,
          confirmPassword
        }),
      });

      const data = await response.json().catch(() => ({}));

      if (response.ok && data.success) {
        setSuccessMessage(data.message || 'Password successfully reset! Redirecting to login...');
        setStep(4);
        setTimeout(() => {
          handleReturnToLogin();
        }, 1800);
        return;
      }

      const extractedError = data.message || data.error || 'Failed to update password. Please request a new OTP.';
      setErrorMessage(extractedError);
    } catch (networkErr) {
      console.error('[Reset Password Error]:', networkErr);
      setErrorMessage('Failed to connect to reset service. Please ensure backend is active.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthLayout
      role={resolvedRole}
      title={step === 3 ? "Set New Password" : step === 2 ? "Verify Reset OTP" : "Reset Your Password"}
      subtitle={
        step === 3
          ? "Create a strong new password for your mentor account"
          : step === 2
          ? `Enter the 6-digit OTP code sent to ${email}`
          : `Enter your official institutional email to recover access to the ${isFaculty ? 'Faculty' : 'Mentor'} portal`
      }
    >
      {/* Alert Error Banner */}
      {errorMessage && (
        <div className="auth-alert error" role="alert" style={{ marginBottom: '1.25rem' }}>
          <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '1px' }} />
          <span>{errorMessage}</span>
        </div>
      )}

      {/* Alert Success Banner */}
      {successMessage && step !== 4 && (
        <div className="auth-alert success" role="alert" style={{ marginBottom: '1.25rem' }}>
          <CheckCircle2 size={18} style={{ flexShrink: 0, marginTop: '1px' }} />
          <span>{successMessage}</span>
        </div>
      )}

      {/* STEP 4: SUCCESS VIEW */}
      {step === 4 ? (
        <div style={{ textAlign: 'center', padding: '1rem 0' }}>
          <div
            style={{
              display: 'inline-flex',
              padding: '16px',
              background: isFaculty ? 'rgba(6, 182, 212, 0.12)' : 'rgba(16, 185, 129, 0.12)',
              borderRadius: '50%',
              color: isFaculty ? 'var(--accent-cyan)' : 'var(--accent-emerald)',
              marginBottom: '1.25rem',
              border: `1px solid ${isFaculty ? 'rgba(6, 182, 212, 0.3)' : 'rgba(16, 185, 129, 0.3)'}`,
            }}
          >
            <CheckCircle2 size={44} />
          </div>

          <h2 style={{ fontSize: '1.25rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '0.75rem' }}>
            {isFaculty ? 'Check Your Email' : 'Password Reset Complete!'}
          </h2>

          <p style={{ fontSize: '0.88rem', color: 'var(--text-muted)', lineHeight: 1.6, marginBottom: '1.75rem' }}>
            {successMessage}
          </p>

          <button
            type="button"
            className={`auth-submit-btn ${roleClass}`}
            style={{ width: '100%', justifyContent: 'center' }}
            onClick={handleReturnToLogin}
          >
            <ArrowLeft size={16} />
            <span>Return to {isFaculty ? 'Faculty' : 'Mentor'} Sign In</span>
          </button>
        </div>
      ) : step === 3 ? (
        /* STEP 3 (MENTOR): ENTER NEW PASSWORD & CONFIRM PASSWORD */
        <form className="auth-form" onSubmit={handleResetPassword} noValidate>
          <div className="auth-field">
            <label htmlFor="mentor-new-password">
              <span>New Password <span className="auth-label-required">*</span></span>
            </label>
            <div className="auth-input-wrap">
              <span className="auth-input-icon">
                <Lock size={18} />
              </span>
              <input
                id="mentor-new-password"
                type={showNewPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="Min. 8 characters"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                onBlur={() => setTouched((prev) => ({ ...prev, newPassword: true }))}
                className={`auth-input ${roleClass} ${touched.newPassword && !isPasswordValid(newPassword) ? 'has-error' : ''}`}
                required
              />
              <button
                type="button"
                className="auth-eye-btn"
                onClick={() => setShowNewPassword(!showNewPassword)}
                aria-label={showNewPassword ? 'Hide password' : 'Show password'}
              >
                {showNewPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {touched.newPassword && !isPasswordValid(newPassword) && (
              <p className="auth-error" role="alert">
                <AlertCircle size={13} />
                <span>Password must be 8+ chars with uppercase, lowercase, number, and special character</span>
              </p>
            )}
          </div>

          <div className="auth-field">
            <label htmlFor="mentor-confirm-password">
              <span>Confirm New Password <span className="auth-label-required">*</span></span>
            </label>
            <div className="auth-input-wrap">
              <span className="auth-input-icon">
                <Lock size={18} />
              </span>
              <input
                id="mentor-confirm-password"
                type={showConfirmPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="Re-enter new password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                onBlur={() => setTouched((prev) => ({ ...prev, confirmPassword: true }))}
                className={`auth-input ${roleClass} ${touched.confirmPassword && newPassword !== confirmPassword ? 'has-error' : ''}`}
                required
              />
              <button
                type="button"
                className="auth-eye-btn"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
              >
                {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {touched.confirmPassword && newPassword !== confirmPassword && (
              <p className="auth-error" role="alert">
                <AlertCircle size={13} />
                <span>Passwords do not match</span>
              </p>
            )}
          </div>

          {/* Password Strength Checklist */}
          {newPassword && (
            <div className="password-strength-box" style={{ marginBottom: '1.25rem' }}>
              <div className="strength-header">
                <span className="strength-label">Password Strength:</span>
                <span className="strength-value" style={{ color: strength.color }}>{strength.label}</span>
              </div>
              <div className="strength-bar-track">
                <div
                  className="strength-bar-fill"
                  style={{ width: `${strength.score * 25}%`, backgroundColor: strength.color }}
                />
              </div>
              <div className="strength-criteria-list">
                <div className={`criteria-item ${criteria.minLength ? 'met' : ''}`}>
                  <Check size={12} />
                  <span>8+ characters</span>
                </div>
                <div className={`criteria-item ${criteria.hasUpper ? 'met' : ''}`}>
                  <Check size={12} />
                  <span>Uppercase letter</span>
                </div>
                <div className={`criteria-item ${criteria.hasLower ? 'met' : ''}`}>
                  <Check size={12} />
                  <span>Lowercase letter</span>
                </div>
                <div className={`criteria-item ${criteria.hasNumber ? 'met' : ''}`}>
                  <Check size={12} />
                  <span>Number (0-9)</span>
                </div>
                <div className={`criteria-item ${criteria.hasSpecial ? 'met' : ''}`}>
                  <Check size={12} />
                  <span>Special character</span>
                </div>
              </div>
            </div>
          )}

          <button
            type="submit"
            id="mentor-reset-password-btn"
            disabled={!isPasswordValid(newPassword) || newPassword !== confirmPassword || isLoading}
            className={`auth-submit-btn ${roleClass}`}
          >
            {isLoading ? (
              <>
                <div className="loading-spinner" />
                <span>Updating Password...</span>
              </>
            ) : (
              <>
                <span>Save New Password</span>
                <ArrowRight size={17} />
              </>
            )}
          </button>
        </form>
      ) : step === 2 ? (
        /* STEP 2 (MENTOR): ENTER 6-DIGIT OTP */
        <form className="auth-form" onSubmit={handleVerifyOtp} noValidate>
          <div className="auth-field">
            <label htmlFor="mentor-otp-input">
              <span>6-Digit Verification Code (OTP) <span className="auth-label-required">*</span></span>
            </label>
            <div className="auth-input-wrap">
              <span className="auth-input-icon">
                <KeyRound size={18} />
              </span>
              <input
                id="mentor-otp-input"
                name="otp"
                type="text"
                maxLength={6}
                autoFocus
                placeholder="Enter 6-digit OTP (e.g. 123456)"
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                onBlur={() => setTouched((prev) => ({ ...prev, otp: true }))}
                className={`auth-input ${roleClass} ${touched.otp && !isValidOtp(otp) ? 'has-error' : ''}`}
                style={{ letterSpacing: '4px', fontSize: '1.1rem', fontWeight: 600 }}
                required
              />
            </div>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
              Valid for 10 minutes. Maximum 5 attempts allowed.
            </span>
            {touched.otp && !isValidOtp(otp) && (
              <p className="auth-error" role="alert">
                <AlertCircle size={13} />
                <span>Please enter exactly 6 digits</span>
              </p>
            )}
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem', marginBottom: '1.25rem' }}>
            <button
              type="submit"
              id="mentor-verify-otp-btn"
              disabled={!isValidOtp(otp) || isLoading}
              className={`auth-submit-btn ${roleClass}`}
              style={{ flex: 2 }}
            >
              {isLoading ? (
                <>
                  <div className="loading-spinner" />
                  <span>Verifying Code...</span>
                </>
              ) : (
                <>
                  <span>Verify OTP</span>
                  <ArrowRight size={17} />
                </>
              )}
            </button>

            <button
              type="button"
              id="mentor-resend-otp-btn"
              disabled={!canResend || isLoading}
              onClick={handleResendOtp}
              className="auth-link-btn"
              style={{
                flex: 1,
                border: '1px solid var(--border-medium)',
                borderRadius: 'var(--radius-md)',
                background: 'var(--bg-surface)',
                color: canResend ? 'var(--text-primary)' : 'var(--text-muted)',
                cursor: canResend ? 'pointer' : 'not-allowed',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '0.4rem',
                fontSize: '0.82rem',
                padding: '0 0.75rem'
              }}
            >
              <RotateCcw size={14} />
              <span>{canResend ? 'Resend' : `${countdown}s`}</span>
            </button>
          </div>

          <div className="auth-footer">
            <button
              type="button"
              className="auth-link"
              style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}
              onClick={() => { setStep(1); setOtp(''); }}
            >
              <ArrowLeft size={14} />
              <span>Change Email Address</span>
            </button>
          </div>
        </form>
      ) : (
        /* STEP 1: ENTER EMAIL (BOTH MENTOR & FACULTY) */
        <form className="auth-form" onSubmit={handleRequestOtp} noValidate>
          <div className="auth-field">
            <label htmlFor={`forgot-email-${roleClass}`}>
              <span>Official Institutional Email <span className="auth-label-required">*</span></span>
            </label>
            <div className="auth-input-wrap">
              <span className="auth-input-icon">
                <Mail size={18} />
              </span>
              <input
                id={`forgot-email-${roleClass}`}
                name="email"
                type="email"
                autoComplete="email"
                placeholder={isFaculty ? 'prof.name@intellicampus.edu' : 'mentor.name@intellicampus.edu'}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                onBlur={() => setTouched((prev) => ({ ...prev, email: true }))}
                className={`auth-input ${roleClass} ${touched.email && !isValidEmail(email) ? 'has-error' : ''}`}
                aria-invalid={!!(touched.email && !isValidEmail(email))}
                required
              />
            </div>
            {touched.email && !isValidEmail(email) && (
              <p className="auth-error" role="alert">
                <AlertCircle size={13} />
                <span>Please enter a valid institutional email address</span>
              </p>
            )}
          </div>

          <button
            type="submit"
            id={`forgot-password-submit-btn-${roleClass}`}
            disabled={!isValidEmail(email) || isLoading}
            className={`auth-submit-btn ${roleClass}`}
          >
            {isLoading ? (
              <>
                <div className="loading-spinner" />
                <span>{isFaculty ? 'Dispatching Reset Link...' : 'Sending 6-Digit OTP...'}</span>
              </>
            ) : (
              <>
                <span>{isFaculty ? 'Send Password Reset Link' : 'Send 6-Digit OTP'}</span>
                <ArrowRight size={17} />
              </>
            )}
          </button>

          <div className="auth-footer" style={{ marginTop: '1.5rem' }}>
            <button
              type="button"
              className="auth-link"
              style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}
              onClick={handleReturnToLogin}
            >
              <ArrowLeft size={14} />
              <span>Back to {isFaculty ? 'Faculty' : 'Mentor'} Sign In</span>
            </button>
          </div>
        </form>
      )}
    </AuthLayout>
  );
}
