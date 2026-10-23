import React, { useState, useEffect } from 'react';
import { 
  ShieldCheck, 
  Lock, 
  Mail, 
  User, 
  KeyRound, 
  Eye, 
  EyeOff, 
  ArrowRight, 
  AlertCircle, 
  AlertTriangle, 
  CheckCircle2, 
  LogIn, 
  UserPlus, 
  Sparkles, 
  RotateCw,
  Key
} from 'lucide-react';
import {
  loginPrincipalApi,
  getPrincipalSetupStatusApi,
  registerPrincipalApi,
  verifyPrincipalOtpApi,
  forgotPasswordApi,
  resetPasswordApi,
  saveStaffSession,
  validateStaffLoginForm,
  validatePrincipalRegisterForm,
  validateForgotPasswordForm,
  validateOtpResetPasswordForm,
  isValidStrongPassword
} from '../../utils/staffAuthService';
import './StaffAuth.css';

export default function PrincipalAuthView({ onAuthSuccess, noticeMessage = '', onSwitchRole }) {
  const [setupRequired, setSetupRequired] = useState(false);
  const [checkingSetup, setCheckingSetup] = useState(true);
  const [activeTab, setActiveTab] = useState('login'); // 'login' | 'register' | 'otp' | 'forgot'
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState('');
  const [apiSuccess, setApiSuccess] = useState('');
  const [errors, setErrors] = useState({});

  // Login form state
  const [loginForm, setLoginForm] = useState({
    email: '',
    password: ''
  });

  // Principal registration form state
  const [registerForm, setRegisterForm] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: '',
    setupToken: ''
  });

  // OTP Verification state (for registration activation & login 2FA)
  const [otpEmail, setOtpEmail] = useState('');
  const [otpCode, setOtpCode] = useState('');

  // Forgot password state
  const [forgotStep, setForgotStep] = useState('email'); // 'email' | 'otp'
  const [forgotEmail, setForgotEmail] = useState('');
  const [resetOtp, setResetOtp] = useState('');
  const [resetNewPassword, setResetNewPassword] = useState('');
  const [resetConfirmPassword, setResetConfirmPassword] = useState('');

  // Check setup status on mount
  useEffect(() => {
    let isMounted = true;
    async function checkStatus() {
      try {
        const res = await getPrincipalSetupStatusApi();
        if (isMounted) {
          setSetupRequired(Boolean(res.setupRequired));
          // If setup is required and user hasn't explicitly chosen a tab, default to register or keep login
        }
      } catch (err) {
        console.warn('Could not determine setup status:', err);
      } finally {
        if (isMounted) setCheckingSetup(false);
      }
    }
    checkStatus();
    return () => { isMounted = false; };
  }, []);

  const handleTabSwitch = (tab) => {
    setActiveTab(tab);
    setApiError('');
    setApiSuccess('');
    setErrors({});
  };

  // -------------------------------------------------------------------------
  // 1. LOGIN HANDLER
  // -------------------------------------------------------------------------
  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    setApiSuccess('');

    const validation = validateStaffLoginForm(loginForm);
    if (!validation.isValid) {
      setErrors(validation.errors);
      return;
    }
    setErrors({});
    setLoading(true);

    try {
      const response = await loginPrincipalApi(loginForm);

      // Check if 2FA OTP is required
      if (response.requiresOtp || response.status === 'OTP_REQUIRED') {
        setOtpEmail(loginForm.email.trim().toLowerCase());
        setActiveTab('otp');
        setApiSuccess(response.message || 'Please enter the 6-digit OTP sent to your registered email.');
        return;
      }

      saveStaffSession(response);
      if (onAuthSuccess) {
        onAuthSuccess(response);
      }
    } catch (err) {
      const msg = err.message || 'Failed to authenticate.';
      setApiError(msg);
      // If error indicates unverified pending registration
      if (msg.toLowerCase().includes('pending') || msg.toLowerCase().includes('otp')) {
        setOtpEmail(loginForm.email.trim().toLowerCase());
        setApiError(msg + ' Please enter the verification OTP sent to your email.');
      }
    } finally {
      setLoading(false);
    }
  };

  // -------------------------------------------------------------------------
  // 2. REGISTRATION HANDLER
  // -------------------------------------------------------------------------
  const handleRegisterSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    setApiSuccess('');

    const validation = validatePrincipalRegisterForm(registerForm);
    if (!validation.isValid) {
      setErrors(validation.errors);
      return;
    }
    setErrors({});
    setLoading(true);

    try {
      const res = await registerPrincipalApi(registerForm);
      setOtpEmail(registerForm.email.trim().toLowerCase());
      setActiveTab('otp');
      setApiSuccess(res.message || 'Registration initiated! A 6-digit verification code has been dispatched to your email.');
    } catch (err) {
      setApiError(err.message || 'Registration failed.');
      // If account already exists (403), re-check setup status to hide option
      if (err.status === 403 || err.message?.includes('already exists')) {
        setSetupRequired(false);
        setActiveTab('login');
      }
    } finally {
      setLoading(false);
    }
  };

  // -------------------------------------------------------------------------
  // 3. OTP VERIFICATION HANDLER (Registration Activation / 2FA)
  // -------------------------------------------------------------------------
  const handleOtpSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    setApiSuccess('');

    if (!otpCode || otpCode.trim().length !== 6) {
      setErrors({ otp: 'Please enter a valid 6-digit OTP code' });
      return;
    }
    setErrors({});
    setLoading(true);

    try {
      const response = await verifyPrincipalOtpApi({
        email: otpEmail,
        otp: otpCode.trim()
      });

      setSetupRequired(false);
      saveStaffSession(response);
      setApiSuccess('Principal account verified and activated successfully! Redirecting...');
      if (onAuthSuccess) {
        setTimeout(() => onAuthSuccess(response), 600);
      }
    } catch (err) {
      setApiError(err.message || 'OTP verification failed. Please check the code and try again.');
    } finally {
      setLoading(false);
    }
  };

  // -------------------------------------------------------------------------
  // 4. FORGOT PASSWORD HANDLERS
  // -------------------------------------------------------------------------
  const handleForgotEmailSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    setApiSuccess('');

    const validation = validateForgotPasswordForm({ email: forgotEmail });
    if (!validation.isValid) {
      setErrors(validation.errors);
      return;
    }
    setErrors({});
    setLoading(true);

    try {
      const data = await forgotPasswordApi({ email: forgotEmail, role: 'PRINCIPAL' });
      setApiSuccess(data.message || 'If registered, a 6-digit OTP has been dispatched to your email.');
      setForgotStep('otp');
    } catch (err) {
      setApiError(err.message || 'Failed to dispatch password reset OTP.');
    } finally {
      setLoading(false);
    }
  };

  const handleForgotOtpResetSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    setApiSuccess('');

    const validation = validateOtpResetPasswordForm({
      email: forgotEmail,
      otp: resetOtp,
      newPassword: resetNewPassword,
      confirmPassword: resetConfirmPassword
    });
    if (!validation.isValid) {
      setErrors(validation.errors);
      return;
    }
    setErrors({});
    setLoading(true);

    try {
      const data = await resetPasswordApi({
        email: forgotEmail,
        otp: resetOtp,
        newPassword: resetNewPassword,
        confirmPassword: resetConfirmPassword,
        role: 'PRINCIPAL'
      });
      setApiSuccess(data.message || 'Password reset successfully! You can now sign in with your new password.');
      setLoginForm(prev => ({ ...prev, email: forgotEmail, password: '' }));
      setActiveTab('login');
      setForgotStep('email');
    } catch (err) {
      setApiError(err.message || 'Failed to reset password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="staff-auth-wrapper staff-auth-container" id="principal-auth-container">
      <div className="staff-auth-card" style={{ borderColor: 'rgba(99, 102, 241, 0.35)' }}>
        
        {/* Header */}
        <div className="staff-auth-header">
          <div 
            className="staff-auth-icon-wrap"
            style={{
              background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.25), rgba(99, 102, 241, 0.08))',
              borderColor: 'rgba(99, 102, 241, 0.4)',
              color: '#818cf8'
            }}
          >
            <ShieldCheck size={32} />
          </div>
          <h2 className="staff-auth-title">Principal Operations Console</h2>
          <p className="staff-auth-subtitle">
            {activeTab === 'register' 
              ? 'First-run institution setup: register the institutional Principal account'
              : activeTab === 'otp'
                ? 'Email verification code required to activate Principal access'
                : 'Executive academic administration & institution governance'}
          </p>
        </div>

        {/* Notice Message Banner */}
        {noticeMessage && (
          <div 
            className="staff-alert staff-alert-warning" 
            style={{ 
              marginBottom: '1.25rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.6rem',
              background: 'rgba(245, 158, 11, 0.12)',
              border: '1px solid rgba(245, 158, 11, 0.35)',
              color: '#fbbf24',
              padding: '0.75rem 1rem',
              borderRadius: '8px'
            }}
          >
            <AlertTriangle size={18} style={{ flexShrink: 0 }} />
            <div style={{ fontSize: '0.9rem', fontWeight: 500 }}>{noticeMessage}</div>
          </div>
        )}

        {/* API Error Alert */}
        {apiError && (
          <div className="staff-alert staff-alert-error" style={{ marginBottom: '1.25rem' }}>
            <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
            <div>{apiError}</div>
          </div>
        )}

        {/* API Success Alert */}
        {apiSuccess && (
          <div className="staff-alert staff-alert-success" style={{ marginBottom: '1.25rem' }}>
            <CheckCircle2 size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
            <div>{apiSuccess}</div>
          </div>
        )}

        {/* Navigation Tabs (Register is ONLY visible when setupRequired is true) */}
        <div 
          className="staff-auth-tabs" 
          style={{
            display: 'flex',
            borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
            marginBottom: '1.5rem',
            gap: '0.5rem'
          }}
        >
          <button
            type="button"
            id="tab-principal-login"
            onClick={() => handleTabSwitch('login')}
            className={`staff-tab-btn ${activeTab === 'login' ? 'active' : ''}`}
            style={{
              flex: 1,
              padding: '0.65rem 0.5rem',
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'login' ? '2px solid #6366f1' : '2px solid transparent',
              color: activeTab === 'login' ? '#f8fafc' : '#94a3b8',
              fontWeight: activeTab === 'login' ? 600 : 500,
              fontSize: '0.88rem',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '0.4rem',
              transition: 'all 0.2s'
            }}
          >
            <LogIn size={15} />
            Sign In
          </button>

          {/* First-time setup registration tab - HIDDEN once Principal exists */}
          {setupRequired && (
            <button
              type="button"
              id="tab-principal-register"
              onClick={() => handleTabSwitch('register')}
              className={`staff-tab-btn ${activeTab === 'register' ? 'active' : ''}`}
              style={{
                flex: 1,
                padding: '0.65rem 0.5rem',
                background: 'transparent',
                border: 'none',
                borderBottom: activeTab === 'register' ? '2px solid #6366f1' : '2px solid transparent',
                color: activeTab === 'register' ? '#f8fafc' : '#94a3b8',
                fontWeight: activeTab === 'register' ? 600 : 500,
                fontSize: '0.88rem',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '0.4rem',
                transition: 'all 0.2s'
              }}
            >
              <UserPlus size={15} />
              <span>First-Time Setup</span>
              <span 
                style={{
                  background: '#6366f1',
                  color: '#fff',
                  fontSize: '0.65rem',
                  padding: '2px 6px',
                  borderRadius: '10px',
                  fontWeight: 700
                }}
              >
                1st
              </span>
            </button>
          )}

          <button
            type="button"
            id="tab-principal-forgot"
            onClick={() => handleTabSwitch('forgot')}
            className={`staff-tab-btn ${activeTab === 'forgot' ? 'active' : ''}`}
            style={{
              flex: 1,
              padding: '0.65rem 0.5rem',
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'forgot' ? '2px solid #6366f1' : '2px solid transparent',
              color: activeTab === 'forgot' ? '#f8fafc' : '#94a3b8',
              fontWeight: activeTab === 'forgot' ? 600 : 500,
              fontSize: '0.88rem',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '0.4rem',
              transition: 'all 0.2s'
            }}
          >
            <KeyRound size={15} />
            Reset Password
          </button>
        </div>

        {/* ----------------------------------------------------------------- */}
        {/* TAB 1: SIGN IN FORM                                               */}
        {/* ----------------------------------------------------------------- */}
        {activeTab === 'login' && (
          <form onSubmit={handleLoginSubmit} className="staff-auth-form" noValidate>
            <div className="staff-field-group">
              <label className="staff-field-label" htmlFor="principal-login-email">
                Official Principal Email
              </label>
              <div className="staff-input-wrapper">
                <Mail className="staff-input-icon" size={18} />
                <input
                  id="principal-login-email"
                  type="email"
                  className={`staff-input ${errors.email ? 'error' : ''}`}
                  placeholder="e.g. principal@college.edu"
                  value={loginForm.email}
                  onChange={(e) => setLoginForm({ ...loginForm, email: e.target.value })}
                  disabled={loading}
                  autoComplete="email"
                />
              </div>
              {errors.email && <span className="staff-field-error">{errors.email}</span>}
            </div>

            <div className="staff-field-group">
              <div className="staff-field-label">
                <label htmlFor="principal-login-password">Password</label>
                <button
                  type="button"
                  className="staff-link-btn"
                  onClick={() => handleTabSwitch('forgot')}
                  style={{ fontSize: '0.8rem', color: '#818cf8', background: 'none', border: 'none', cursor: 'pointer' }}
                >
                  Forgot password?
                </button>
              </div>
              <div className="staff-input-wrapper">
                <Lock className="staff-input-icon" size={18} />
                <input
                  id="principal-login-password"
                  type={showPassword ? 'text' : 'password'}
                  className={`staff-input ${errors.password ? 'error' : ''}`}
                  placeholder="Enter your password"
                  value={loginForm.password}
                  onChange={(e) => setLoginForm({ ...loginForm, password: e.target.value })}
                  disabled={loading}
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  className="staff-password-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {errors.password && <span className="staff-field-error">{errors.password}</span>}
            </div>

            <button
              type="submit"
              id="principal-login-submit"
              className="staff-submit-btn"
              disabled={loading}
              style={{ background: 'linear-gradient(135deg, #6366f1, #4f46e5)' }}
            >
              {loading ? (
                <>
                  <div className="staff-spinner" />
                  <span>Authenticating...</span>
                </>
              ) : (
                <>
                  <span>Sign In to Principal Console</span>
                  <ArrowRight size={16} />
                </>
              )}
            </button>

            {/* First-time setup callout - only shown if setup is required */}
            {setupRequired && (
              <div 
                style={{
                  marginTop: '1.25rem',
                  padding: '0.9rem',
                  borderRadius: '10px',
                  background: 'rgba(99, 102, 241, 0.08)',
                  border: '1px dashed rgba(99, 102, 241, 0.3)',
                  textAlign: 'center'
                }}
              >
                <p style={{ margin: '0 0 0.5rem 0', fontSize: '0.85rem', color: '#cbd5e1' }}>
                  Setting up a fresh campus installation?
                </p>
                <button
                  type="button"
                  id="link-register-principal"
                  onClick={() => handleTabSwitch('register')}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#818cf8',
                    fontWeight: 600,
                    fontSize: '0.88rem',
                    cursor: 'pointer',
                    textDecoration: 'underline'
                  }}
                >
                  Register as Principal (First-Time Setup) &rarr;
                </button>
              </div>
            )}
          </form>
        )}

        {/* ----------------------------------------------------------------- */}
        {/* TAB 2: FIRST-TIME SETUP REGISTRATION                              */}
        {/* ----------------------------------------------------------------- */}
        {activeTab === 'register' && setupRequired && (
          <form onSubmit={handleRegisterSubmit} className="staff-auth-form" noValidate id="principal-register-form">
            <div 
              style={{
                background: 'rgba(99, 102, 241, 0.1)',
                border: '1px solid rgba(99, 102, 241, 0.25)',
                borderRadius: '8px',
                padding: '0.75rem 1rem',
                fontSize: '0.85rem',
                color: '#c7d2fe',
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem'
              }}
            >
              <Sparkles size={16} style={{ color: '#818cf8', flexShrink: 0 }} />
              <div>
                <strong>First-Time Setup:</strong> Self-registration is allowed only once to establish the institutional Principal.
              </div>
            </div>

            <div className="staff-field-group">
              <label className="staff-field-label" htmlFor="principal-reg-name">
                Full Name
              </label>
              <div className="staff-input-wrapper">
                <User className="staff-input-icon" size={18} />
                <input
                  id="principal-reg-name"
                  type="text"
                  className={`staff-input ${errors.name ? 'error' : ''}`}
                  placeholder="e.g. Dr. A. Sharma"
                  value={registerForm.name}
                  onChange={(e) => setRegisterForm({ ...registerForm, name: e.target.value })}
                  disabled={loading}
                />
              </div>
              {errors.name && <span className="staff-field-error">{errors.name}</span>}
            </div>

            <div className="staff-field-group">
              <label className="staff-field-label" htmlFor="principal-reg-email">
                Institutional Email Address
              </label>
              <div className="staff-input-wrapper">
                <Mail className="staff-input-icon" size={18} />
                <input
                  id="principal-reg-email"
                  type="email"
                  className={`staff-input ${errors.email ? 'error' : ''}`}
                  placeholder="e.g. principal@institution.edu"
                  value={registerForm.email}
                  onChange={(e) => setRegisterForm({ ...registerForm, email: e.target.value })}
                  disabled={loading}
                />
              </div>
              {errors.email && <span className="staff-field-error">{errors.email}</span>}
            </div>

            <div className="staff-field-group">
              <label className="staff-field-label" htmlFor="principal-reg-password">
                Password
              </label>
              <div className="staff-input-wrapper">
                <Lock className="staff-input-icon" size={18} />
                <input
                  id="principal-reg-password"
                  type={showPassword ? 'text' : 'password'}
                  className={`staff-input ${errors.password ? 'error' : ''}`}
                  placeholder="Min 8 characters (upper, lower, digit, symbol)"
                  value={registerForm.password}
                  onChange={(e) => setRegisterForm({ ...registerForm, password: e.target.value })}
                  disabled={loading}
                />
                <button
                  type="button"
                  className="staff-password-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {errors.password && <span className="staff-field-error">{errors.password}</span>}

              {/* Password strength checklist */}
              <div 
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: '0.3rem',
                  fontSize: '0.74rem',
                  marginTop: '0.4rem',
                  color: '#94a3b8'
                }}
              >
                <div style={{ color: registerForm.password.length >= 8 ? '#10b981' : '#64748b' }}>
                  {registerForm.password.length >= 8 ? '✓' : '•'} 8+ characters
                </div>
                <div style={{ color: /[A-Z]/.test(registerForm.password) ? '#10b981' : '#64748b' }}>
                  {/[A-Z]/.test(registerForm.password) ? '✓' : '•'} Uppercase letter
                </div>
                <div style={{ color: /[a-z]/.test(registerForm.password) ? '#10b981' : '#64748b' }}>
                  {/[a-z]/.test(registerForm.password) ? '✓' : '•'} Lowercase letter
                </div>
                <div style={{ color: /[0-9]/.test(registerForm.password) ? '#10b981' : '#64748b' }}>
                  {/[0-9]/.test(registerForm.password) ? '✓' : '•'} Number & symbol
                </div>
              </div>
            </div>

            <div className="staff-field-group">
              <label className="staff-field-label" htmlFor="principal-reg-confirm">
                Confirm Password
              </label>
              <div className="staff-input-wrapper">
                <Lock className="staff-input-icon" size={18} />
                <input
                  id="principal-reg-confirm"
                  type={showPassword ? 'text' : 'password'}
                  className={`staff-input ${errors.confirmPassword ? 'error' : ''}`}
                  placeholder="Re-enter your password"
                  value={registerForm.confirmPassword}
                  onChange={(e) => setRegisterForm({ ...registerForm, confirmPassword: e.target.value })}
                  disabled={loading}
                />
              </div>
              {errors.confirmPassword && <span className="staff-field-error">{errors.confirmPassword}</span>}
            </div>

            <div className="staff-field-group">
              <label className="staff-field-label" htmlFor="principal-reg-token">
                Setup Token <span style={{ color: '#64748b', fontWeight: 400 }}>(Optional - if configured in .env)</span>
              </label>
              <div className="staff-input-wrapper">
                <Key className="staff-input-icon" size={18} />
                <input
                  id="principal-reg-token"
                  type="text"
                  className="staff-input"
                  placeholder="SETUP_TOKEN (leave blank if not set)"
                  value={registerForm.setupToken}
                  onChange={(e) => setRegisterForm({ ...registerForm, setupToken: e.target.value })}
                  disabled={loading}
                />
              </div>
            </div>

            <button
              type="submit"
              id="principal-register-submit"
              className="staff-submit-btn"
              disabled={loading}
              style={{ background: 'linear-gradient(135deg, #6366f1, #4f46e5)' }}
            >
              {loading ? (
                <>
                  <div className="staff-spinner" />
                  <span>Creating Account & Sending OTP...</span>
                </>
              ) : (
                <>
                  <span>Register & Request Verification OTP</span>
                  <ArrowRight size={16} />
                </>
              )}
            </button>
          </form>
        )}

        {/* ----------------------------------------------------------------- */}
        {/* TAB 3: OTP VERIFICATION (Account Activation)                      */}
        {/* ----------------------------------------------------------------- */}
        {activeTab === 'otp' && (
          <form onSubmit={handleOtpSubmit} className="staff-auth-form" noValidate id="principal-otp-form">
            <div 
              style={{
                background: 'rgba(99, 102, 241, 0.1)',
                border: '1px solid rgba(99, 102, 241, 0.25)',
                borderRadius: '8px',
                padding: '0.85rem 1rem',
                fontSize: '0.88rem',
                color: '#e2e8f0',
                lineHeight: 1.4
              }}
            >
              A 6-digit verification code has been dispatched to <strong>{otpEmail}</strong>.
              <div style={{ marginTop: '0.4rem', fontSize: '0.8rem', color: '#94a3b8' }}>
                * Code expires in 15 minutes. In development without SMTP, check your server console log.
              </div>
            </div>

            <div className="staff-field-group">
              <label className="staff-field-label" htmlFor="principal-otp-code">
                6-Digit Verification Code
              </label>
              <div className="staff-input-wrapper">
                <KeyRound className="staff-input-icon" size={18} />
                <input
                  id="principal-otp-code"
                  type="text"
                  maxLength={6}
                  className={`staff-input ${errors.otp ? 'error' : ''}`}
                  placeholder="000000"
                  value={otpCode}
                  onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                  disabled={loading}
                  style={{ letterSpacing: '0.3em', fontSize: '1.2rem', textAlign: 'center' }}
                  autoFocus
                />
              </div>
              {errors.otp && <span className="staff-field-error">{errors.otp}</span>}
            </div>

            <button
              type="submit"
              id="principal-otp-submit"
              className="staff-submit-btn"
              disabled={loading}
              style={{ background: 'linear-gradient(135deg, #10b981, #059669)' }}
            >
              {loading ? (
                <>
                  <div className="staff-spinner" />
                  <span>Verifying & Activating...</span>
                </>
              ) : (
                <>
                  <span>Verify OTP & Activate Console</span>
                  <CheckCircle2 size={16} />
                </>
              )}
            </button>

            <button
              type="button"
              onClick={() => setActiveTab('login')}
              style={{
                background: 'none',
                border: 'none',
                color: '#94a3b8',
                fontSize: '0.85rem',
                cursor: 'pointer',
                textAlign: 'center',
                marginTop: '0.5rem'
              }}
            >
              &larr; Back to Sign In
            </button>
          </form>
        )}

        {/* ----------------------------------------------------------------- */}
        {/* TAB 4: FORGOT / RESET PASSWORD                                    */}
        {/* ----------------------------------------------------------------- */}
        {activeTab === 'forgot' && (
          <div>
            {forgotStep === 'email' ? (
              <form onSubmit={handleForgotEmailSubmit} className="staff-auth-form" noValidate>
                <p style={{ margin: '0 0 1rem 0', fontSize: '0.85rem', color: '#94a3b8' }}>
                  Enter your registered Principal email to receive a 6-digit password reset code.
                </p>

                <div className="staff-field-group">
                  <label className="staff-field-label" htmlFor="principal-forgot-email">
                    Registered Email
                  </label>
                  <div className="staff-input-wrapper">
                    <Mail className="staff-input-icon" size={18} />
                    <input
                      id="principal-forgot-email"
                      type="email"
                      className={`staff-input ${errors.email ? 'error' : ''}`}
                      placeholder="principal@college.edu"
                      value={forgotEmail}
                      onChange={(e) => setForgotEmail(e.target.value)}
                      disabled={loading}
                    />
                  </div>
                  {errors.email && <span className="staff-field-error">{errors.email}</span>}
                </div>

                <button
                  type="submit"
                  className="staff-submit-btn"
                  disabled={loading}
                  style={{ background: 'linear-gradient(135deg, #6366f1, #4f46e5)' }}
                >
                  {loading ? 'Sending OTP...' : 'Send Password Reset OTP'}
                </button>
              </form>
            ) : (
              <form onSubmit={handleForgotOtpResetSubmit} className="staff-auth-form" noValidate>
                <div className="staff-field-group">
                  <label className="staff-field-label" htmlFor="principal-reset-otp">
                    6-Digit OTP Code
                  </label>
                  <div className="staff-input-wrapper">
                    <KeyRound className="staff-input-icon" size={18} />
                    <input
                      id="principal-reset-otp"
                      type="text"
                      maxLength={6}
                      className={`staff-input ${errors.otp ? 'error' : ''}`}
                      placeholder="000000"
                      value={resetOtp}
                      onChange={(e) => setResetOtp(e.target.value.replace(/\D/g, ''))}
                      disabled={loading}
                      style={{ letterSpacing: '0.25em', textAlign: 'center' }}
                    />
                  </div>
                  {errors.otp && <span className="staff-field-error">{errors.otp}</span>}
                </div>

                <div className="staff-field-group">
                  <label className="staff-field-label" htmlFor="principal-reset-pass">
                    New Password
                  </label>
                  <div className="staff-input-wrapper">
                    <Lock className="staff-input-icon" size={18} />
                    <input
                      id="principal-reset-pass"
                      type="password"
                      className={`staff-input ${errors.newPassword ? 'error' : ''}`}
                      placeholder="New strong password"
                      value={resetNewPassword}
                      onChange={(e) => setResetNewPassword(e.target.value)}
                      disabled={loading}
                    />
                  </div>
                  {errors.newPassword && <span className="staff-field-error">{errors.newPassword}</span>}
                </div>

                <div className="staff-field-group">
                  <label className="staff-field-label" htmlFor="principal-reset-confirm">
                    Confirm New Password
                  </label>
                  <div className="staff-input-wrapper">
                    <Lock className="staff-input-icon" size={18} />
                    <input
                      id="principal-reset-confirm"
                      type="password"
                      className={`staff-input ${errors.confirmPassword ? 'error' : ''}`}
                      placeholder="Confirm new password"
                      value={resetConfirmPassword}
                      onChange={(e) => setResetConfirmPassword(e.target.value)}
                      disabled={loading}
                    />
                  </div>
                  {errors.confirmPassword && <span className="staff-field-error">{errors.confirmPassword}</span>}
                </div>

                <button
                  type="submit"
                  className="staff-submit-btn"
                  disabled={loading}
                  style={{ background: 'linear-gradient(135deg, #10b981, #059669)' }}
                >
                  {loading ? 'Updating Password...' : 'Reset Password & Sign In'}
                </button>
              </form>
            )}
          </div>
        )}

      </div>
    </div>
  );
}
