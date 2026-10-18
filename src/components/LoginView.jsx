import React, { useState } from 'react';
import { LogIn, KeyRound, User, AlertCircle, Sparkles, CheckCircle2 } from 'lucide-react';
import { authenticateUser } from '../utils/auth';

export default function LoginView({ users = [], onLogin }) {
  const [loginCode, setLoginCode] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  // Role-less list of sample demo login codes from mockData
  const demoCodes = [
    'student.aarav',
    'student.priya',
    'student.rohan',
    'student.kavya',
    'prof.verma',
    'dr.mehta',
    'hod.sharma',
    'admin.patel'
  ];

  const handleSubmit = (e) => {
    e.preventDefault();
    setError('');

    const result = authenticateUser(users, loginCode, password);
    if (!result.success) {
      setError(result.error);
      return;
    }

    if (onLogin) {
      onLogin(result.user);
    }
  };

  const handleSelectDemoCode = (code) => {
    setLoginCode(code);
    setPassword('demo123');
    setError('');
  };

  return (
    <div className="login-viewport animate-fade-in">
      <div className="login-card glass-card">
        {/* Header Branding */}
        <div className="login-header">
          <div className="login-icon-badge">
            <Sparkles size={24} />
          </div>
          <h2 className="login-title">Sign In to IntelliCampus</h2>
          <p className="login-subtitle">
            Enter your institutional login code and password to access your portal.
          </p>
        </div>

        {/* Error Alert Message */}
        {error && (
          <div className="login-error-alert" role="alert">
            <AlertCircle size={18} className="flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Credentials Form */}
        <form onSubmit={handleSubmit} className="login-form">
          <div className="form-group">
            <label htmlFor="loginCode" className="form-label">
              <User size={14} className="inline mr-1" /> Login Code
            </label>
            <input
              id="loginCode"
              type="text"
              className="login-input"
              placeholder="e.g. student.aarav"
              value={loginCode}
              onChange={(e) => {
                setLoginCode(e.target.value);
                if (error) setError('');
              }}
              autoComplete="username"
              autoFocus
            />
          </div>

          <div className="form-group">
            <label htmlFor="password" className="form-label">
              <KeyRound size={14} className="inline mr-1" /> Password
            </label>
            <input
              id="password"
              type="password"
              className="login-input"
              placeholder="Enter your password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                if (error) setError('');
              }}
              autoComplete="current-password"
            />
          </div>

          <button type="submit" className="login-submit-btn">
            <LogIn size={18} />
            <span>Login</span>
          </button>
        </form>

        {/* Role-less Demo Hint */}
        <div className="login-demo-section">
          <div className="demo-header-label">
            <span className="dot online" style={{ marginRight: '6px' }} />
            Demo Accounts (Click code to auto-fill):
          </div>
          <div className="demo-chips-grid">
            {demoCodes.map((code) => (
              <button
                key={code}
                type="button"
                className={`demo-code-chip ${loginCode.toLowerCase() === code ? 'active' : ''}`}
                onClick={() => handleSelectDemoCode(code)}
                title={`Click to fill login code ${code}`}
              >
                {code}
              </button>
            ))}
          </div>
          <p className="demo-hint-text">
            * For this demo, any non-empty password is accepted.
          </p>
        </div>
      </div>
    </div>
  );
}
