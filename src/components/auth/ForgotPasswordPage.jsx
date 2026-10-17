import React, { useState } from 'react';
import AuthLayout from './AuthLayout';
import { 
  Mail, 
  ArrowLeft, 
  ArrowRight, 
  CheckCircle2, 
  AlertCircle 
} from 'lucide-react';
import { isValidEmail } from './authUtils';

export default function ForgotPasswordPage({
  initialRole = 'FACULTY',
  onNavigateLogin
}) {
  const [role, setRole] = useState(() => {
    // Check if role is in URL query parameters
    const params = new URLSearchParams(window.location.search);
    const r = params.get('role');
    if (r && (r.toUpperCase() === 'MENTOR' || r.toUpperCase() === 'FACULTY')) {
      return r.toUpperCase();
    }
    return initialRole;
  });

  const [email, setEmail] = useState('');
  const [touched, setTouched] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const emailError = touched && !isValidEmail(email)
    ? 'Please enter a valid institutional email address'
    : '';

  const isFaculty = role === 'FACULTY';

  const handleSubmit = async (e) => {
    e.preventDefault();
    setTouched(true);

    if (!isValidEmail(email) || isLoading) return;

    setIsLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const response = await fetch('/api/auth/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: email.trim().toLowerCase(),
          role
        })
      });

      const data = await response.json().catch(() => ({}));

      // In accordance with security specifications, always show neutral success feedback
      setSuccessMessage(
        data.message || 'If this email is registered in our system, a password reset link has been dispatched to your inbox.'
      );
      setIsSubmitted(true);
    } catch (networkErr) {
      console.warn('Backend unavailable, showing fallback message:', networkErr);
      setSuccessMessage(
        'If this email is registered in our system, a password reset link has been dispatched to your inbox.'
      );
      setIsSubmitted(true);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthLayout
      role={role}
      onRoleChange={(newRole) => {
        setRole(newRole);
        setErrorMessage('');
        setSuccessMessage('');
        setIsSubmitted(false);
      }}
      title="Reset Your Password"
      subtitle={`Enter your institutional email to receive a password reset link for the ${isFaculty ? 'Faculty' : 'Mentor'} portal`}
    >
      {/* Alert Banners */}
      {errorMessage && (
        <div className="auth-alert error" role="alert">
          <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '1px' }} />
          <span>{errorMessage}</span>
        </div>
      )}

      {isSubmitted ? (
        <div style={{ textAlign: 'center', padding: '1rem 0' }}>
          <div style={{ display: 'inline-flex', padding: '16px', background: 'rgba(16, 185, 129, 0.12)', borderRadius: '50%', color: '#10b981', marginBottom: '1.25rem', border: '1px solid rgba(16, 185, 129, 0.25)' }}>
            <CheckCircle2 size={44} />
          </div>

          <h2 style={{ fontSize: '1.25rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '0.75rem' }}>
            Check Your Email
          </h2>

          <p style={{ fontSize: '0.88rem', color: 'var(--text-muted)', lineHeight: 1.6, marginBottom: '1.75rem' }}>
            {successMessage}
          </p>

          <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', background: 'var(--bg-surface)', padding: '0.75rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-medium)', marginBottom: '1.75rem' }}>
            The link is valid for <strong>15 minutes</strong> and can only be used once. Please also inspect your spam folder if the email does not appear in your primary inbox.
          </p>

          <button
            type="button"
            className={`auth-submit-btn ${isFaculty ? 'faculty' : 'mentor'}`}
            style={{ width: '100%', justifyContent: 'center' }}
            onClick={() => onNavigateLogin && onNavigateLogin(role)}
          >
            <ArrowLeft size={16} />
            <span>Return to {isFaculty ? 'Faculty' : 'Mentor'} Sign In</span>
          </button>
        </div>
      ) : (
        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="form-group">
            <label className="form-label" htmlFor="forgot-email">
              <span>Official Institutional Email <span className="form-label-required">*</span></span>
            </label>
            <div className="input-icon-wrap">
              <Mail size={17} className="input-leading-icon" />
              <input
                id="forgot-email"
                name="email"
                type="email"
                autoComplete="email"
                placeholder={isFaculty ? 'prof.name@intellicampus.edu' : 'mentor.name@intellicampus.edu'}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                onBlur={() => setTouched(true)}
                className={`auth-input ${isFaculty ? 'faculty' : 'mentor'} ${emailError ? 'has-error' : ''}`}
                aria-invalid={!!emailError}
                aria-describedby={emailError ? 'email-error' : undefined}
                required
              />
            </div>
            {emailError && (
              <div id="email-error" className="form-error-text" role="alert">
                <AlertCircle size={13} />
                <span>{emailError}</span>
              </div>
            )}
          </div>

          <button
            type="submit"
            id="forgot-password-submit-btn"
            disabled={!isValidEmail(email) || isLoading}
            className={`auth-submit-btn ${isFaculty ? 'faculty' : 'mentor'}`}
          >
            {isLoading ? (
              <>
                <div className="loading-spinner" />
                <span>Dispatching Reset Link...</span>
              </>
            ) : (
              <>
                <span>Send Password Reset Link</span>
                <ArrowRight size={17} />
              </>
            )}
          </button>

          <div className="auth-footer" style={{ marginTop: '1.5rem' }}>
            <button
              type="button"
              className="auth-link"
              style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}
              onClick={() => onNavigateLogin && onNavigateLogin(role)}
            >
              <ArrowLeft size={14} />
              <span>Back to Sign In</span>
            </button>
          </div>
        </form>
      )}
    </AuthLayout>
  );
}
