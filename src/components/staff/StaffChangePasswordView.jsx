import React, { useState } from 'react';
import { 
  KeyRound, 
  Lock, 
  Eye, 
  EyeOff, 
  ArrowRight, 
  AlertCircle, 
  CheckCircle2, 
  X 
} from 'lucide-react';
import { 
  changePasswordApi, 
  validateChangePasswordForm 
} from '../../utils/staffAuthService';
import './StaffAuth.css';

export default function StaffChangePasswordView({ staffSession, onClose, onPasswordChanged }) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState('');
  const [apiSuccess, setApiSuccess] = useState('');
  const [errors, setErrors] = useState({});

  const handleSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    setApiSuccess('');

    const validation = validateChangePasswordForm({ currentPassword, newPassword, confirmPassword });
    if (!validation.isValid) {
      setErrors(validation.errors);
      return;
    }
    setErrors({});

    const token = staffSession?.accessToken || staffSession?.token;
    if (!token) {
      setApiError('Session expired. Please log in again.');
      return;
    }

    setLoading(true);
    try {
      const data = await changePasswordApi({ currentPassword, newPassword }, token);
      setApiSuccess(data.message || 'Password changed successfully!');
      setTimeout(() => {
        if (onPasswordChanged) onPasswordChanged();
        if (onClose) onClose();
      }, 1200);
    } catch (err) {
      setApiError(err.message || 'Failed to change password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="staff-auth-container" style={{ position: 'relative' }}>
      <div className="staff-auth-card" style={{ position: 'relative' }}>
        {onClose && (
          <button 
            type="button" 
            onClick={onClose} 
            className="staff-eye-btn" 
            style={{ position: 'absolute', top: '1.25rem', right: '1.25rem' }}
          >
            <X size={20} />
          </button>
        )}

        <div className="staff-auth-header">
          <div className="staff-auth-icon-wrap">
            <KeyRound size={32} />
          </div>
          <h2 className="staff-auth-title">Change Password</h2>
          <p className="staff-auth-subtitle">
            Update account password for <strong>{staffSession?.user?.email || 'authenticated user'}</strong>
          </p>
        </div>

        {apiError && (
          <div className="staff-alert staff-alert-error" style={{ marginBottom: '1.25rem' }}>
            <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
            <div>{apiError}</div>
          </div>
        )}

        {apiSuccess && (
          <div className="staff-alert staff-alert-success" style={{ marginBottom: '1.25rem' }}>
            <CheckCircle2 size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
            <div>{apiSuccess}</div>
          </div>
        )}

        <form onSubmit={handleSubmit} className="staff-auth-form">
          <div className="staff-field-group">
            <label className="staff-field-label">Current Password</label>
            <div className="staff-input-wrapper">
              <Lock className="staff-input-icon" size={18} />
              <input 
                type={showCurrent ? 'text' : 'password'}
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                placeholder="Enter current password"
                className={`staff-input ${errors.currentPassword ? 'has-error' : ''}`}
              />
              <button 
                type="button" 
                className="staff-eye-btn"
                onClick={() => setShowCurrent(!showCurrent)}
                tabIndex="-1"
              >
                {showCurrent ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {errors.currentPassword && <span className="staff-error-text">{errors.currentPassword}</span>}
          </div>

          <div className="staff-field-group">
            <label className="staff-field-label">New Password (Min 6 chars)</label>
            <div className="staff-input-wrapper">
              <Lock className="staff-input-icon" size={18} />
              <input 
                type={showNew ? 'text' : 'password'}
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="Enter new password"
                className={`staff-input ${errors.newPassword ? 'has-error' : ''}`}
              />
              <button 
                type="button" 
                className="staff-eye-btn"
                onClick={() => setShowNew(!showNew)}
                tabIndex="-1"
              >
                {showNew ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {errors.newPassword && <span className="staff-error-text">{errors.newPassword}</span>}
          </div>

          <div className="staff-field-group">
            <label className="staff-field-label">Confirm New Password</label>
            <div className="staff-input-wrapper">
              <Lock className="staff-input-icon" size={18} />
              <input 
                type={showNew ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="Confirm new password"
                className={`staff-input ${errors.confirmPassword ? 'has-error' : ''}`}
              />
            </div>
            {errors.confirmPassword && <span className="staff-error-text">{errors.confirmPassword}</span>}
          </div>

          <button 
            type="submit" 
            className="staff-submit-btn"
            disabled={loading}
          >
            {loading ? 'Updating Password...' : 'Save New Password'}
            <ArrowRight size={18} />
          </button>
        </form>
      </div>
    </div>
  );
}
