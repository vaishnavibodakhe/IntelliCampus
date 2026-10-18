import React, { useState, useEffect } from 'react';
import AuthLayout from './AuthLayout';
import { 
  Lock, 
  Eye, 
  EyeOff, 
  ArrowRight, 
  Check, 
  CheckCircle2, 
  AlertCircle,
  XCircle,
  ArrowLeft 
} from 'lucide-react';
import { 
  isPasswordValid, 
  validatePasswordCriteria, 
  calculatePasswordStrength 
} from './authUtils';
import { useAuth } from '../../context/AuthContext';

export default function ResetPasswordPage({ role: propRole, token: propToken }) {
  const { navigate } = useAuth();

  // Extract path and token
  const pathname = window.location.pathname;
  const isFaculty = propRole ? propRole.toUpperCase() === 'FACULTY' : !pathname.startsWith('/mentor');
  const resolvedRole = isFaculty ? 'FACULTY' : 'MENTOR';
  const roleClass = isFaculty ? 'faculty' : 'mentor';

  // Extract token from URL path (/faculty/reset-password/:token) or query (?token=...)
  const extractedToken = (() => {
    if (propToken) return propToken;
    const pathParts = pathname.split('/reset-password/');
    if (pathParts.length > 1 && pathParts[1].trim()) {
      return pathParts[1].split('/')[0].split('?')[0];
    }
    const params = new URLSearchParams(window.location.search);
    return params.get('token') || '';
  })();

  const [token] = useState(extractedToken);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // Status & validation states
  const [isValidatingToken, setIsValidatingToken] = useState(true);
  const [isTokenValid, setIsTokenValid] = useState(true);
  const [tokenErrorMessage, setTokenErrorMessage] = useState('');
  const [touched, setTouched] = useState({ password: false, confirm: false });
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const strength = calculatePasswordStrength(newPassword);
  const criteria = validatePasswordCriteria(newPassword);

  const apiBase = isFaculty ? '/api/faculty' : '/api/mentor';

  // Validate token with backend on page load
  useEffect(() => {
    if (!token) {
      setIsValidatingToken(false);
      setIsTokenValid(false);
      setTokenErrorMessage('No password reset token was provided in the URL. Please request a new reset link.');
      return;
    }

    const verifyToken = async () => {
      try {
        const response = await fetch(`${apiBase}/reset-password/${encodeURIComponent(token)}`);
        const data = await response.json().catch(() => ({}));

        if (!response.ok || !data.success) {
          setIsTokenValid(false);
          setTokenErrorMessage(data.message || 'This password reset link is invalid or has expired. Please request a new link.');
        } else {
          setIsTokenValid(true);
        }
      } catch (err) {
        console.error('[Token Verification Error]:', err);
        // Allow user to proceed if offline or temporary network issue
        setIsTokenValid(true);
      } finally {
        setIsValidatingToken(false);
      }
    };

    verifyToken();
  }, [token, apiBase]);

  const passwordError = touched.password && !isPasswordValid(newPassword)
    ? 'Password must be at least 8 characters with uppercase, lowercase, and a number'
    : '';

  const confirmError = touched.confirm && newPassword !== confirmPassword
    ? 'Passwords do not match'
    : '';

  const isFormValid = isPasswordValid(newPassword) && newPassword === confirmPassword;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setTouched({ password: true, confirm: true });

    if (!isFormValid || isLoading) return;

    setIsLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const response = await fetch(`${apiBase}/reset-password/${encodeURIComponent(token)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          password: newPassword,
          confirmPassword
        })
      });

      const data = await response.json().catch(() => ({}));

      if (response.ok && data.success) {
        setSuccessMessage(data.message || 'Password successfully updated! Redirecting to login...');
        setTimeout(() => {
          navigate(isFaculty ? '/faculty/login' : '/mentor/login');
        }, 1800);
        return;
      }

      setErrorMessage(data.message || 'Failed to reset password. Please request a new link.');
    } catch (networkErr) {
      console.error('[Reset Submit Error]:', networkErr);
      setErrorMessage('Unable to connect to the authentication server. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  // 1. Loading State
  if (isValidatingToken) {
    return (
      <AuthLayout role={resolvedRole} title="Verifying Reset Token" subtitle="Please wait while we validate your credentials">
        <div style={{ textAlign: 'center', padding: '2rem 0' }}>
          <div className="loading-spinner" style={{ margin: '0 auto 1.25rem', width: '36px', height: '36px' }} />
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>Validating reset security token...</p>
        </div>
      </AuthLayout>
    );
  }

  // 2. Invalid Token State
  if (!isTokenValid) {
    return (
      <AuthLayout role={resolvedRole} title="Link Expired or Invalid" subtitle="Security token verification failed">
        <div style={{ textAlign: 'center', padding: '1rem 0' }}>
          <div
            style={{
              display: 'inline-flex',
              padding: '16px',
              background: 'rgba(239, 68, 68, 0.12)',
              borderRadius: '50%',
              color: '#ef4444',
              marginBottom: '1.25rem',
              border: '1px solid rgba(239, 68, 68, 0.3)'
            }}
          >
            <XCircle size={44} />
          </div>

          <p style={{ fontSize: '0.92rem', color: 'var(--text-muted)', lineHeight: 1.6, marginBottom: '1.75rem' }}>
            {tokenErrorMessage}
          </p>

          <button
            type="button"
            className={`auth-submit-btn ${roleClass}`}
            style={{ width: '100%', justifyContent: 'center' }}
            onClick={() => navigate(isFaculty ? '/faculty/forgot-password' : '/mentor/forgot-password')}
          >
            <ArrowRight size={16} />
            <span>Request New Reset Link</span>
          </button>
        </div>
      </AuthLayout>
    );
  }

  // 3. Reset Password Form State
  return (
    <AuthLayout
      role={resolvedRole}
      title="Create New Password"
      subtitle={`Establish a secure new password for your ${isFaculty ? 'Faculty' : 'Mentor'} portal account`}
    >
      {/* Alert Banners */}
      {errorMessage && (
        <div className="auth-alert error" role="alert">
          <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '1px' }} />
          <span>{errorMessage}</span>
        </div>
      )}

      {successMessage && (
        <div className="auth-alert success" role="alert">
          <CheckCircle2 size={18} style={{ flexShrink: 0, marginTop: '1px' }} />
          <span>{successMessage}</span>
        </div>
      )}

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        {/* New Password */}
        <div className="auth-field">
          <label htmlFor="reset-new-password">
            <span>New Password <span className="auth-label-required">*</span></span>
          </label>
          <div className="auth-input-wrap">
            <span className="auth-input-icon">
              <Lock size={18} />
            </span>
            <input
              id="reset-new-password"
              name="newPassword"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="Min. 8 characters"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              onBlur={() => setTouched((prev) => ({ ...prev, password: true }))}
              className={`auth-input ${roleClass} ${passwordError ? 'has-error' : ''}`}
              aria-invalid={!!passwordError}
              aria-describedby={passwordError ? 'password-error' : undefined}
            />
            <button
              type="button"
              className="auth-eye-btn"
              onClick={() => setShowPassword(!showPassword)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>
          {passwordError && (
            <p id="password-error" className="auth-error" role="alert">
              <AlertCircle size={13} />
              <span>{passwordError}</span>
            </p>
          )}
        </div>

        {/* Dynamic Password Strength Meter */}
        {newPassword && (
          <div className="password-strength-box" style={{ marginBottom: '20px' }}>
            <div className="strength-header">
              <span className="strength-label">Password Strength:</span>
              <span className="strength-value" style={{ color: strength.color }}>{strength.label}</span>
            </div>
            <div className="strength-bar-track">
              <div
                className="strength-bar-fill"
                style={{ width: `${strength.score}%`, backgroundColor: strength.color }}
              />
            </div>
            <div className="strength-criteria-list">
              <div className={`criteria-item ${criteria.length ? 'met' : ''}`}>
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
                <span>Numeric digit</span>
              </div>
            </div>
          </div>
        )}

        {/* Confirm New Password */}
        <div className="auth-field">
          <label htmlFor="reset-confirm-password">
            <span>Confirm New Password <span className="auth-label-required">*</span></span>
          </label>
          <div className="auth-input-wrap">
            <span className="auth-input-icon">
              <Lock size={18} />
            </span>
            <input
              id="reset-confirm-password"
              name="confirmPassword"
              type={showConfirmPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="Repeat your new password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              onBlur={() => setTouched((prev) => ({ ...prev, confirm: true }))}
              className={`auth-input ${roleClass} ${confirmError ? 'has-error' : ''}`}
              aria-invalid={!!confirmError}
              aria-describedby={confirmError ? 'confirm-error' : undefined}
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
          {confirmError && (
            <p id="confirm-error" className="auth-error" role="alert">
              <AlertCircle size={13} />
              <span>{confirmError}</span>
            </p>
          )}
        </div>

        {/* Submit Button */}
        <button
          type="submit"
          id="reset-password-submit-btn"
          disabled={!isFormValid || isLoading}
          className={`auth-submit-btn ${roleClass}`}
          style={{ marginTop: '0.75rem' }}
        >
          {isLoading ? (
            <>
              <div className="loading-spinner" />
              <span>Updating Password...</span>
            </>
          ) : (
            <>
              <span>Save & Sign In to {isFaculty ? 'Faculty' : 'Mentor'} Portal</span>
              <ArrowRight size={17} />
            </>
          )}
        </button>
      </form>

      {/* Return to Sign In */}
      <div className="auth-footer" style={{ marginTop: '1.5rem' }}>
        <button
          type="button"
          className="auth-link"
          style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}
          onClick={() => navigate(isFaculty ? '/faculty/login' : '/mentor/login')}
        >
          <ArrowLeft size={14} />
          <span>Back to {isFaculty ? 'Faculty' : 'Mentor'} Sign In</span>
        </button>
      </div>
    </AuthLayout>
  );
}
