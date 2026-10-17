import React, { useState } from 'react';
import { 
  Mail, 
  KeyRound, 
  Lock, 
  AlertCircle, 
  CheckCircle2, 
  ArrowRight, 
  Eye, 
  EyeOff, 
  GraduationCap,
  ShieldCheck,
  Sparkles
} from 'lucide-react';
import { api } from '../api/client';

export default function StudentLogin({ onSwitchToRegister, onLoginSuccess }) {
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!identifier.trim()) {
      setError('Please enter your email or Roll Number / PRN.');
      return;
    }
    if (!password) {
      setError('Please enter your password.');
      return;
    }

    setIsLoading(true);
    try {
      const resp = await api.login({ identifier: identifier.trim(), password });
      setSuccess(resp.message || 'Login successful!');
      if (onLoginSuccess) {
        setTimeout(() => {
          onLoginSuccess(resp);
        }, 600);
      }
    } catch (err) {
      setError(err.message || 'Authentication failed. Please verify your credentials.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleQuickDemo = (demoId, demoPw) => {
    setIdentifier(demoId);
    setPassword(demoPw);
    setError('');
  };

  return (
    <div className="auth-card-container">
      <div className="auth-card">
        {/* Header */}
        <div className="auth-card-header">
          <div className="auth-badge">
            <GraduationCap size={20} className="text-accent" />
            <span>IntelliCampus Authentication</span>
          </div>
          <h2 className="auth-title">Student Sign In</h2>
          <p className="auth-subtitle">
            Enter your registered Email or Roll Number / PRN to access your dashboard.
          </p>
        </div>

        {error && (
          <div className="alert-banner alert-danger">
            <AlertCircle size={18} />
            <span>{error}</span>
          </div>
        )}

        {success && (
          <div className="alert-banner alert-success">
            <CheckCircle2 size={18} />
            <span>{success}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="auth-form" noValidate>
          <div className="form-group">
            <label htmlFor="login-identifier">College Email or Roll No / PRN</label>
            <div className="input-wrap">
              <Mail size={16} className="input-icon" />
              <input
                id="login-identifier"
                type="text"
                value={identifier}
                onChange={(e) => {
                  setIdentifier(e.target.value);
                  setError('');
                }}
                placeholder="e.g. aarav.cs24@intellicampus.edu or CS-104"
                autoComplete="username"
              />
            </div>
          </div>

          <div className="form-group">
            <div className="label-row" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <label htmlFor="login-password">Password</label>
            </div>
            <div className="input-wrap">
              <KeyRound size={16} className="input-icon" />
              <input
                id="login-password"
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value);
                  setError('');
                }}
                placeholder="••••••••"
                autoComplete="current-password"
              />
              <button
                type="button"
                className="password-toggle-btn"
                onClick={() => setShowPassword(!showPassword)}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </div>

          {/* Quick Demo Pre-fills */}
          <div className="demo-credentials-box">
            <span className="demo-hint-title">
              <Sparkles size={14} className="text-accent" />
              Quick Fill Demo Accounts:
            </span>
            <div className="demo-pill-group">
              <button
                type="button"
                className="demo-pill"
                onClick={() => handleQuickDemo('aarav.cs24@intellicampus.edu', 'Welcome2026!')}
              >
                Aarav Shah (CS-104)
              </button>
              <button
                type="button"
                className="demo-pill"
                onClick={() => handleQuickDemo('CS-105', 'Welcome2026!')}
              >
                Priya Nair (CS-105)
              </button>
            </div>
          </div>

          {/* Submit Button */}
          <button
            type="submit"
            disabled={isLoading}
            className="btn-primary auth-submit-btn"
          >
            {isLoading ? (
              <span>Authenticating...</span>
            ) : (
              <>
                <span>Sign In to IntelliCampus</span>
                <ArrowRight size={18} />
              </>
            )}
          </button>
        </form>

        {/* Footer switch to register */}
        <div className="auth-card-footer">
          <span>New student to the university?</span>
          <button
            type="button"
            onClick={onSwitchToRegister}
            className="link-btn"
          >
            Register Student Account
          </button>
        </div>
      </div>
    </div>
  );
}
