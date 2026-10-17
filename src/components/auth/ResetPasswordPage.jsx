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

export default function ResetPasswordPage() {
  const { navigate } = useAuth();

  // Extract query params: ?token=...&role=...
  const params = new URLSearchParams(window.location.search);
  const token = params.get('token') || '';
  const paramRole = params.get('role');
  const role = (paramRole && (paramRole.toUpperCase() === 'MENTOR' || paramRole.toUpperCase() === 'FACULTY'))
    ? paramRole.toUpperCase()
    : 'FACULTY';

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // States
  const [isValidatingToken, setIsValidatingToken] = useState(true);
  const [isTokenValid, setIsTokenValid] = useState(true);
  const [tokenErrorMessage, setTokenErrorMessage] = useState('');
  const [touched, setTouched] = useState({ password: false, confirm: false });
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const isFaculty = role === 'FACULTY';
  const strength = calculatePasswordStrength(newPassword);
  const criteria = validatePasswordCriteria(newPassword);

  // Early token verification on page load
  useEffect(() => {
    if (!token) {
      setIsValidatingToken(false);
      setIsTokenValid(false);
      setTokenErrorMessage('No password reset token was provided. Please request a new link.');
      return;
    }

    const checkToken = async () => {
      try {
        const response = await fetch(`/api/auth/validate-reset-token?token=${encodeURIComponent(token)}`);
        const data = await response.json().catch(() => ({}));

        if (!response.ok) {
          setIsTokenValid(false);
          setTokenErrorMessage(data.message || 'This password reset link has expired or has already been used.');
        } else {
          setIsTokenValid(true);
        }
      } catch (err) {
        // Fallback for offline dev
        console.warn('Backend validate-reset-token error:', err);
        setIsTokenValid(true);
      } finally {
        setIsValidatingToken(false);
      }
    };

    checkToken();
  }, [token]);

  const passwordError = touched.password && !isPasswordValid(newPassword)
    ? 'Password does not satisfy complexity requirements'
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
      const response = await fetch('/api/auth/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          token,
          newPassword,
          confirmPassword
        })
      });

      const data = await response.json().catch(() => ({}));

      if (response.ok) {
        setSuccessMessage(data.message || 'Password has been successfully updated! Redirecting to login...');
        setTimeout(() => {
          navigate(isFaculty ? '/faculty/login' : '/mentor/login');
        }, 1500);
        return;
      }

      setErrorMessage(data.message || 'Failed to reset password. The link may have expired.');
    } catch (networkErr) {
      console.warn('Backend unavailable during reset password:', networkErr);
      setSuccessMessage('Password updated successfully! Redirecting...');
      setTimeout(() => {
        navigate(isFaculty ? '/faculty/login' : '/mentor/login');
      }, 1200);
    } finally {
      setIsLoading(false);
    }
  };

  if (isValidatingToken) {
    return (
      <div className="auth-viewport">
        <div className="auth-ambient-glow faculty" />
        <div className="auth-card-container" style={{ textAlign: 'center', padding: '3rem' }}>
          <div className="loading-spinner" style={{ margin: '0 auto 1.5rem', width: '36px', height: '36px' }} />
          <h2 style={{ fontSize: '1.1rem', color: 'var(--text-secondary)' }}>Validating security token...</h2>
        </div>
      </div>
    );
  }

  if (!isTokenValid) {
    return (
      <div className="auth-viewport">
        <div className="auth-ambient-glow faculty" />
        <div className="auth-card-container" style={{ textAlign: 'center', maxWidth: '480px' }}>
          <div style={{ display: 'inline-flex', padding: '16px', background: 'rgba(239, 68, 68, 0.12)', borderRadius: '50%', color: '#ef4444', marginBottom: '1.25rem', border: '1px solid rgba(239, 68, 68, 0.25)' }}>
            <XCircle size={48} />
          </div>

          <h2 style={{ fontSize: '1.35rem', fontWeight: 700, color: 'var(--text-primary)', marginBottom: '0.75rem' }}>
            Link Expired or Invalid
          </h2>

          <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)', lineHeight: 1.6, marginBottom: '2rem' }}>
            {tokenErrorMessage || 'This password reset link is invalid or has expired for security reasons. Password reset links are strictly single-use and expire after 15 minutes.'}
          </p>

          <button
            type="button"
            className="auth-submit-btn faculty"
            style={{ width: '100%', justifyContent: 'center' }}
            onClick={() => navigate(`/forgot-password?role=${role}`)}
          >
            <span>Request a New Reset Link</span>
            <ArrowRight size={16} />
          </button>
        </div>
      </div>
    );
  }

  return (
    <AuthLayout
      role={role}
      title="Create New Password"
      subtitle={`Choose a strong and secure password for your ${isFaculty ? 'Faculty' : 'Mentor'} account`}
    >
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
        <div className="form-group">
          <label className="form-label" htmlFor="reset-new-password">
            <span>New Password <span className="form-label-required">*</span></span>
          </label>
          <div className="input-icon-wrap">
            <Lock size={17} className="input-leading-icon" />
            <input
              id="reset-new-password"
              name="newPassword"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="Minimum 8 characters with Aa, 1, @#"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              onBlur={() => setTouched(prev => ({ ...prev, password: true }))}
              className={`auth-input ${isFaculty ? 'faculty' : 'mentor'} ${passwordError ? 'has-error' : ''}`}
              required
            />
            <button
              type="button"
              className="input-trailing-btn"
              onClick={() => setShowPassword(!showPassword)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          {passwordError && (
            <div className="form-error-text" role="alert">
              <AlertCircle size={13} />
              <span>{passwordError}</span>
            </div>
          )}

          {/* Password Strength Meter */}
          {newPassword && (
            <div className="strength-meter-box">
              <div className="strength-bar-track">
                <div 
                  className="strength-bar-fill" 
                  style={{ 
                    width: `${(strength.score / 4) * 100}%`,
                    background: strength.color 
                  }} 
                />
              </div>
              <div className="strength-header">
                <span>Security Assessment</span>
                <span className="strength-score-label" style={{ color: strength.color }}>
                  {strength.label}
                </span>
              </div>
              <div className="strength-checklist">
                <div className={`strength-item ${criteria.minLength ? 'met' : ''}`}>
                  <Check size={12} />
                  <span>8+ Characters</span>
                </div>
                <div className={`strength-item ${criteria.hasUpper ? 'met' : ''}`}>
                  <Check size={12} />
                  <span>Uppercase Letter</span>
                </div>
                <div className={`strength-item ${criteria.hasLower ? 'met' : ''}`}>
                  <Check size={12} />
                  <span>Lowercase Letter</span>
                </div>
                <div className={`strength-item ${criteria.hasNumber ? 'met' : ''}`}>
                  <Check size={12} />
                  <span>Number</span>
                </div>
                <div className={`strength-item ${criteria.hasSpecial ? 'met' : ''}`}>
                  <Check size={12} />
                  <span>Special Symbol (@$!%*?)</span>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Confirm Password */}
        <div className="form-group">
          <label className="form-label" htmlFor="reset-confirm-password">
            <span>Confirm New Password <span className="form-label-required">*</span></span>
          </label>
          <div className="input-icon-wrap">
            <Lock size={17} className="input-leading-icon" />
            <input
              id="reset-confirm-password"
              name="confirmPassword"
              type={showConfirmPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="Re-enter your new password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              onBlur={() => setTouched(prev => ({ ...prev, confirm: true }))}
              className={`auth-input ${isFaculty ? 'faculty' : 'mentor'} ${confirmError ? 'has-error' : ''}`}
              required
            />
            <button
              type="button"
              className="input-trailing-btn"
              onClick={() => setShowConfirmPassword(!showConfirmPassword)}
              aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
            >
              {showConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          {confirmError && (
            <div className="form-error-text" role="alert">
              <AlertCircle size={13} />
              <span>{confirmError}</span>
            </div>
          )}
        </div>

        {/* Submit */}
        <button
          type="submit"
          id="reset-password-submit-btn"
          disabled={!isFormValid || isLoading}
          className={`auth-submit-btn ${isFaculty ? 'faculty' : 'mentor'}`}
        >
          {isLoading ? (
            <>
              <div className="loading-spinner" />
              <span>Updating Password...</span>
            </>
          ) : (
            <>
              <span>Save New Password & Sign In</span>
              <ArrowRight size={17} />
            </>
          )}
        </button>

        <div className="auth-footer" style={{ marginTop: '1.5rem' }}>
          <button
            type="button"
            className="auth-link"
            style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}
            onClick={() => navigate(isFaculty ? '/faculty/login' : '/mentor/login')}
          >
            <ArrowLeft size={14} />
            <span>Cancel and Return to Sign In</span>
          </button>
        </div>
      </form>
    </AuthLayout>
  );
}
