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
  const resolvedEndpoint = apiEndpoint || (isFaculty ? '/api/faculty/forgot-password' : '/api/mentor/forgot-password');

  const [email, setEmail] = useState('');
  const [touched, setTouched] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const emailError = touched && !isValidEmail(email)
    ? 'Please enter a valid institutional email address'
    : '';

  const handleSubmit = async (e) => {
    e.preventDefault();
    setTouched(true);

    if (!isValidEmail(email) || isLoading) return;

    setIsLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const response = await fetch(resolvedEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: email.trim().toLowerCase(),
        }),
      });

      const data = await response.json().catch(() => ({}));

      // Security standard: generic message prevents user enumeration
      setSuccessMessage(
        data.message || 'If this email exists, a reset link has been sent.'
      );
      setIsSubmitted(true);
    } catch (networkErr) {
      console.error('[Forgot Password Error]:', networkErr);
      setErrorMessage('Failed to connect to the reset service. Please ensure server is active.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleReturnToLogin = () => {
    if (onNavigateLogin) {
      onNavigateLogin();
    } else {
      navigate(isFaculty ? '/faculty/login' : '/mentor/login');
    }
  };

  return (
    <AuthLayout
      role={resolvedRole}
      title="Reset Your Password"
      subtitle={`Enter your official institutional email to receive a password reset link for the ${isFaculty ? 'Faculty' : 'Mentor'} portal`}
    >
      {/* Alert Error Banner */}
      {errorMessage && (
        <div className="auth-alert error" role="alert">
          <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '1px' }} />
          <span>{errorMessage}</span>
        </div>
      )}

      {isSubmitted ? (
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
            Check Your Email
          </h2>

          <p style={{ fontSize: '0.88rem', color: 'var(--text-muted)', lineHeight: 1.6, marginBottom: '1.75rem' }}>
            {successMessage}
          </p>

          <p
            style={{
              fontSize: '0.8rem',
              color: 'var(--text-secondary)',
              background: 'var(--bg-surface)',
              padding: '0.75rem',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--border-medium)',
              marginBottom: '1.75rem',
            }}
          >
            The link is valid for <strong>15 minutes</strong> and can only be used once. Please also inspect your spam/junk folder.
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
      ) : (
        <form className="auth-form" onSubmit={handleSubmit} noValidate>
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
                onBlur={() => setTouched(true)}
                className={`auth-input ${roleClass} ${emailError ? 'has-error' : ''}`}
                aria-invalid={!!emailError}
                aria-describedby={emailError ? `forgot-email-error-${roleClass}` : undefined}
                required
              />
            </div>
            {emailError && (
              <p id={`forgot-email-error-${roleClass}`} className="auth-error" role="alert">
                <AlertCircle size={13} />
                <span>{emailError}</span>
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
