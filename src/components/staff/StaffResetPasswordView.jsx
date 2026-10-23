import React, { useState, useEffect } from 'react';
import { 
  KeyRound, 
  Lock, 
  Mail,
  Eye, 
  EyeOff, 
  ArrowRight, 
  AlertCircle, 
  CheckCircle2, 
  ArrowLeft 
} from 'lucide-react';
import { 
  resetPasswordApi, 
  validateResetPasswordForm 
} from '../../utils/staffAuthService';
import './StaffAuth.css';

export default function StaffResetPasswordView({ initialToken = '', onNavigate }) {
  const [email, setEmail] = useState('');
  const [token, setToken] = useState(initialToken);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState('');
  const [apiSuccess, setApiSuccess] = useState('');
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      const urlToken = params.get('token');
      const urlEmail = params.get('email');
      if (urlToken && !token) {
        setToken(urlToken);
      }
      if (urlEmail && !email) {
        setEmail(urlEmail);
      }
    }
  }, [token, email]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    setApiSuccess('');

    const newErrors = {};
    if (!token || !token.trim()) {
      newErrors.token = '6-digit OTP or reset token is required';
    }
    if (!newPassword) {
      newErrors.newPassword = 'New password is required';
    } else if (newPassword.length < 8) {
      newErrors.newPassword = 'Password must be at least 8 characters and include uppercase, lowercase, number, and special character';
    }
    if (newPassword && confirmPassword && newPassword !== confirmPassword) {
      newErrors.confirmPassword = 'Passwords do not match';
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }
    setErrors({});

    setLoading(true);
    try {
      const data = await resetPasswordApi({ 
        email: email.trim(), 
        otp: token.trim(), 
        token: token.trim(), 
        newPassword, 
        confirmPassword 
      });
      setApiSuccess(data.message || 'Password reset successful! You can now sign in.');
      
      // Clear reset token from URL so it cannot be resubmitted or exposed in history
      if (typeof window !== 'undefined' && window.history?.replaceState) {
        window.history.replaceState({}, document.title, window.location.pathname);
      }
    } catch (err) {
      setApiError(err.message || 'Invalid or expired OTP.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="staff-auth-container">
      <div className="staff-auth-card">
        <div className="staff-auth-header">
          <div className="staff-auth-icon-wrap">
            <KeyRound size={32} />
          </div>
          <h2 className="staff-auth-title">Reset Staff Password</h2>
          <p className="staff-auth-subtitle">
            Enter your new secure account password below.
          </p>
        </div>

        {apiError && (
          <div className="staff-alert staff-alert-error" style={{ marginBottom: '1.25rem' }}>
            <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
            <div>{apiError}</div>
          </div>
        )}

        {apiSuccess ? (
          <div>
            <div className="staff-alert staff-alert-success" style={{ marginBottom: '1.5rem' }}>
              <CheckCircle2 size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
              <div>{apiSuccess}</div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <button 
                type="button" 
                className="staff-submit-btn"
                style={{ background: '#6366f1' }}
                onClick={() => onNavigate && onNavigate('ADMIN')}
              >
                Go to Principal login
                <ArrowRight size={18} />
              </button>

              <button 
                type="button" 
                className="staff-submit-btn"
                style={{ background: '#8b5cf6' }}
                onClick={() => onNavigate && onNavigate('HOD')}
              >
                Go to HOD login
                <ArrowRight size={18} />
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="staff-auth-form">
            <div className="staff-field-group">
              <label className="staff-field-label">Account Email</label>
              <div className="staff-input-wrapper">
                <Mail className="staff-input-icon" size={18} />
                <input 
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="Enter your registered staff email"
                  className="staff-input"
                />
              </div>
            </div>

            <div className="staff-field-group">
              <label className="staff-field-label">6-Digit Verification OTP / Reset Token</label>
              <div className="staff-input-wrapper">
                <KeyRound className="staff-input-icon" size={18} />
                <input 
                  type="text"
                  value={token}
                  onChange={(e) => setToken(e.target.value)}
                  placeholder="Enter 6-digit OTP or token"
                  className={`staff-input ${errors.token ? 'has-error' : ''}`}
                />
              </div>
              {errors.token && <span className="staff-error-text">{errors.token}</span>}
            </div>

            <div className="staff-field-group">
              <label className="staff-field-label">New Password (8+ chars, Upper, Lower, Number, Special)</label>
              <div className="staff-input-wrapper">
                <Lock className="staff-input-icon" size={18} />
                <input 
                  type={showPassword ? 'text' : 'password'}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="Enter new password"
                  className={`staff-input ${errors.newPassword ? 'has-error' : ''}`}
                  autoComplete="new-password"
                />
                <button 
                  type="button" 
                  className="staff-eye-btn"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex="-1"
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {errors.newPassword && <span className="staff-error-text">{errors.newPassword}</span>}
            </div>

            <div className="staff-field-group">
              <label className="staff-field-label">Confirm New Password</label>
              <div className="staff-input-wrapper">
                <Lock className="staff-input-icon" size={18} />
                <input 
                  type={showPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Re-enter new password"
                  className={`staff-input ${errors.confirmPassword ? 'has-error' : ''}`}
                  autoComplete="new-password"
                />
              </div>
              {errors.confirmPassword && <span className="staff-error-text">{errors.confirmPassword}</span>}
            </div>

            <button 
              type="submit" 
              className="staff-submit-btn"
              disabled={loading}
            >
              {loading ? 'Updating Password...' : 'Reset Password'}
              <ArrowRight size={18} />
            </button>
          </form>
        )}

        <div className="staff-links" style={{ justifyContent: 'center', gap: '1.5rem' }}>
          <button 
            type="button" 
            className="staff-link"
            onClick={() => onNavigate && onNavigate('ADMIN')}
          >
            <ArrowLeft size={15} />
            Principal Login
          </button>
          <button 
            type="button" 
            className="staff-link"
            onClick={() => onNavigate && onNavigate('HOD')}
          >
            <ArrowLeft size={15} />
            HOD Login
          </button>
        </div>
      </div>
    </div>
  );
}
