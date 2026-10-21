import React, { useState, useEffect } from 'react';
import { 
  BookOpen, 
  UserCheck, 
  Users, 
  Lock, 
  Mail, 
  Eye, 
  EyeOff, 
  ArrowRight, 
  AlertCircle, 
  CheckCircle2, 
  KeyRound, 
  LogIn, 
  RotateCw,
  Sparkles,
  ShieldAlert
} from 'lucide-react';
import { 
  loginStaffApi, 
  forgotPasswordApi, 
  resetPasswordApi,
  saveStaffSession, 
  validateStaffLoginForm, 
  validateForgotPasswordForm,
  validateOtpResetPasswordForm 
} from '../../utils/staffAuthService';
import './StaffAuth.css';

export default function StaffAuthView({ 
  initialRole = 'FACULTY', 
  onAuthSuccess, 
  noticeMessage = '',
  onRoleChange
}) {
  const [selectedRole, setSelectedRole] = useState(() => {
    const validRoles = ['HOD', 'FACULTY', 'MENTOR'];
    if (validRoles.includes(initialRole)) return initialRole;
    return 'FACULTY';
  });

  const [activeTab, setActiveTab] = useState('login'); // 'login' | 'forgot'
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState('');
  const [apiSuccess, setApiSuccess] = useState('');

  // Login form state
  const [loginForm, setLoginForm] = useState({
    email: '',
    password: ''
  });

  // Forgot / Set Password OTP flow state: 'email' (Step 1) | 'otp' (Step 2)
  const [forgotStep, setForgotStep] = useState('email');
  const [forgotEmail, setForgotEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [otpNewPassword, setOtpNewPassword] = useState('');
  const [otpConfirmPassword, setOtpConfirmPassword] = useState('');
  const [showOtpPassword, setShowOtpPassword] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);

  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (initialRole && ['HOD', 'FACULTY', 'MENTOR'].includes(initialRole)) {
      setSelectedRole(initialRole);
    }
  }, [initialRole]);

  // Resend OTP cooldown timer
  useEffect(() => {
    let timer = null;
    if (resendCooldown > 0) {
      timer = setInterval(() => {
        setResendCooldown((prev) => (prev > 0 ? prev - 1 : 0));
      }, 1000);
    }
    return () => {
      if (timer) clearInterval(timer);
    };
  }, [resendCooldown]);

  const handleRoleSelect = (roleKey) => {
    setSelectedRole(roleKey);
    setApiError('');
    setApiSuccess('');
    setErrors({});
    if (onRoleChange) {
      onRoleChange(roleKey);
    }
  };

  const handleTabSwitch = (tab) => {
    setActiveTab(tab);
    setApiError('');
    setApiSuccess('');
    setErrors({});
  };

  // Login submission
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
      const response = await loginStaffApi({
        email: loginForm.email,
        password: loginForm.password,
        role: selectedRole
      });

      saveStaffSession(response);

      if (onAuthSuccess) {
        onAuthSuccess(response);
      }
    } catch (err) {
      setApiError(err.message || 'Invalid email or password');
    } finally {
      setLoading(false);
    }
  };

  // Step 1: Request OTP for password reset / first-time set password
  const handleRequestOtp = async (e) => {
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
      const response = await forgotPasswordApi({
        email: forgotEmail,
        role: selectedRole
      });
      setApiSuccess(response.message || 'If an account exists, a 6-digit OTP has been sent to your email.');
      setForgotStep('otp');
      setResendCooldown(60);
    } catch (err) {
      setApiError(err.message || 'Failed to dispatch verification OTP. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Verify OTP and reset / set new password
  const handleVerifyOtpAndReset = async (e) => {
    e.preventDefault();
    setApiError('');
    setApiSuccess('');

    const validation = validateOtpResetPasswordForm({
      email: forgotEmail,
      otp,
      newPassword: otpNewPassword,
      confirmPassword: otpConfirmPassword
    });

    if (!validation.isValid) {
      setErrors(validation.errors);
      return;
    }
    setErrors({});
    setLoading(true);

    try {
      const response = await resetPasswordApi({
        email: forgotEmail,
        otp,
        newPassword: otpNewPassword,
        confirmPassword: otpConfirmPassword,
        role: selectedRole
      });

      setApiSuccess(response.message || 'Password successfully set! You may now log in.');
      setTimeout(() => {
        setActiveTab('login');
        setForgotStep('email');
        setOtp('');
        setOtpNewPassword('');
        setOtpConfirmPassword('');
        setLoginForm((prev) => ({ ...prev, email: forgotEmail, password: '' }));
      }, 2000);
    } catch (err) {
      setApiError(err.message || 'Invalid or expired OTP. Please check and try again.');
    } finally {
      setLoading(false);
    }
  };

  const roleMeta = {
    HOD: { label: 'Head of Department', icon: BookOpen, accent: '#8b5cf6', badge: 'Academic Governance' },
    FACULTY: { label: 'Faculty Member', icon: UserCheck, accent: '#06b6d4', badge: 'Classrooms & Grading' },
    MENTOR: { label: 'Student Mentor', icon: Users, accent: '#10b981', badge: 'Mentorship & Approvals' }
  };

  const CurrentRoleIcon = roleMeta[selectedRole]?.icon || UserCheck;

  return (
    <div className="staff-auth-container">
      <div className="staff-auth-card">
        {/* Header */}
        <div className="staff-auth-header">
          <div className="staff-brand-badge" style={{ borderColor: roleMeta[selectedRole]?.accent }}>
            <CurrentRoleIcon size={28} color={roleMeta[selectedRole]?.accent} />
          </div>
          <h2>IntelliCampus Staff Portal</h2>
          <p className="staff-auth-subtitle">
            Secure institutional access for <strong>{roleMeta[selectedRole]?.label}</strong>
          </p>
        </div>

        {/* Institutional Role Selector Tabs */}
        <div className="role-selector-bar" style={{ display: 'flex', gap: '8px', marginBottom: '20px', padding: '4px', background: 'rgba(255,255,255,0.05)', borderRadius: '12px' }}>
          {(['HOD', 'FACULTY', 'MENTOR']).map((rKey) => {
            const isSelected = selectedRole === rKey;
            const meta = roleMeta[rKey];
            const IconComp = meta.icon;
            return (
              <button
                key={rKey}
                type="button"
                onClick={() => handleRoleSelect(rKey)}
                style={{
                  flex: 1,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '6px',
                  padding: '10px 12px',
                  borderRadius: '8px',
                  border: isSelected ? `1px solid ${meta.accent}` : '1px solid transparent',
                  background: isSelected ? 'rgba(255,255,255,0.1)' : 'transparent',
                  color: isSelected ? '#ffffff' : '#94a3b8',
                  fontWeight: isSelected ? 600 : 500,
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                  transition: 'all 0.2s ease'
                }}
              >
                <IconComp size={16} color={isSelected ? meta.accent : '#94a3b8'} />
                <span>{rKey === 'HOD' ? 'HOD' : rKey === 'FACULTY' ? 'Faculty' : 'Mentor'}</span>
              </button>
            );
          })}
        </div>

        {noticeMessage && (
          <div className="staff-auth-alert info">
            <Sparkles size={16} />
            <span>{noticeMessage}</span>
          </div>
        )}

        {apiError && (
          <div className="staff-auth-alert error">
            <AlertCircle size={16} />
            <span>{apiError}</span>
          </div>
        )}

        {apiSuccess && (
          <div className="staff-auth-alert success">
            <CheckCircle2 size={16} />
            <span>{apiSuccess}</span>
          </div>
        )}

        {/* Tab Switcher: Login and Forgot/Set Password Only (No Registration) */}
        <div className="staff-tabs">
          <button 
            type="button"
            className={`staff-tab-btn ${activeTab === 'login' ? 'active' : ''}`}
            onClick={() => handleTabSwitch('login')}
          >
            <LogIn size={15} />
            <span>Sign In</span>
          </button>
          <button 
            type="button"
            className={`staff-tab-btn ${activeTab === 'forgot' ? 'active' : ''}`}
            onClick={() => handleTabSwitch('forgot')}
          >
            <KeyRound size={15} />
            <span>Forgot / Set Password</span>
          </button>
        </div>

        {/* Login Form */}
        {activeTab === 'login' && (
          <form onSubmit={handleLoginSubmit} className="staff-form">
            <div className="form-group">
              <label htmlFor="staff-login-email">Registered Institutional Email</label>
              <div className="input-wrap">
                <Mail size={16} className="input-icon" />
                <input 
                  id="staff-login-email"
                  type="email"
                  placeholder={`name@intellicampus.edu`}
                  value={loginForm.email}
                  onChange={(e) => setLoginForm({ ...loginForm, email: e.target.value })}
                  autoComplete="email"
                />
              </div>
              {errors.email && <span className="field-error">{errors.email}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="staff-login-password">Password</label>
              <div className="input-wrap">
                <Lock size={16} className="input-icon" />
                <input 
                  id="staff-login-password"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Enter your password"
                  value={loginForm.password}
                  onChange={(e) => setLoginForm({ ...loginForm, password: e.target.value })}
                  autoComplete="current-password"
                />
                <button 
                  type="button"
                  className="eye-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {errors.password && <span className="field-error">{errors.password}</span>}
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '-4px' }}>
              <button 
                type="button"
                onClick={() => handleTabSwitch('forgot')}
                style={{ background: 'transparent', border: 'none', color: '#94a3b8', fontSize: '0.8rem', cursor: 'pointer', textDecoration: 'underline' }}
              >
                Forgot or need to set password?
              </button>
            </div>

            <button 
              type="submit" 
              className="submit-btn"
              disabled={loading}
              style={{ background: roleMeta[selectedRole]?.accent }}
            >
              {loading ? (
                <>
                  <RotateCw size={16} className="spin-icon" />
                  <span>Authenticating...</span>
                </>
              ) : (
                <>
                  <span>Sign In as {selectedRole}</span>
                  <ArrowRight size={16} />
                </>
              )}
            </button>
          </form>
        )}

        {/* Forgot / First-Time Set Password Flow */}
        {activeTab === 'forgot' && (
          <div className="forgot-flow">
            <div style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px', padding: '12px 14px', marginBottom: '16px', fontSize: '0.82rem', color: '#cbd5e1', lineHeight: '1.4' }}>
              <strong>First time logging in?</strong> When your account was provisioned by an Administrator, a 6-digit setup OTP was emailed to you (valid for 24 hours). Enter your email below to verify your OTP and set your password.
            </div>

            {forgotStep === 'email' ? (
              <form onSubmit={handleRequestOtp} className="staff-form">
                <div className="form-group">
                  <label htmlFor="staff-forgot-email">Registered Institutional Email</label>
                  <div className="input-wrap">
                    <Mail size={16} className="input-icon" />
                    <input 
                      id="staff-forgot-email"
                      type="email"
                      placeholder="name@intellicampus.edu"
                      value={forgotEmail}
                      onChange={(e) => setForgotEmail(e.target.value)}
                      autoComplete="email"
                    />
                  </div>
                  {errors.email && <span className="field-error">{errors.email}</span>}
                </div>

                <button 
                  type="submit" 
                  className="submit-btn"
                  disabled={loading}
                  style={{ background: roleMeta[selectedRole]?.accent }}
                >
                  {loading ? (
                    <>
                      <RotateCw size={16} className="spin-icon" />
                      <span>Sending OTP...</span>
                    </>
                  ) : (
                    <>
                      <span>Send 6-Digit OTP</span>
                      <ArrowRight size={16} />
                    </>
                  )}
                </button>
              </form>
            ) : (
              <form onSubmit={handleVerifyOtpAndReset} className="staff-form">
                <div className="form-group">
                  <label htmlFor="staff-otp">6-Digit Verification OTP</label>
                  <div className="input-wrap">
                    <KeyRound size={16} className="input-icon" />
                    <input 
                      id="staff-otp"
                      type="text"
                      maxLength={6}
                      placeholder="e.g. 123456"
                      value={otp}
                      onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                    />
                  </div>
                  {errors.otp && <span className="field-error">{errors.otp}</span>}
                </div>

                <div className="form-group">
                  <label htmlFor="staff-new-password">New Secure Password</label>
                  <div className="input-wrap">
                    <Lock size={16} className="input-icon" />
                    <input 
                      id="staff-new-password"
                      type={showOtpPassword ? 'text' : 'password'}
                      placeholder="Minimum 8 characters"
                      value={otpNewPassword}
                      onChange={(e) => setOtpNewPassword(e.target.value)}
                    />
                    <button 
                      type="button"
                      className="eye-toggle"
                      onClick={() => setShowOtpPassword(!showOtpPassword)}
                    >
                      {showOtpPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                  {errors.newPassword && <span className="field-error">{errors.newPassword}</span>}
                </div>

                <div className="form-group">
                  <label htmlFor="staff-confirm-password">Confirm New Password</label>
                  <div className="input-wrap">
                    <Lock size={16} className="input-icon" />
                    <input 
                      id="staff-confirm-password"
                      type={showOtpPassword ? 'text' : 'password'}
                      placeholder="Re-enter new password"
                      value={otpConfirmPassword}
                      onChange={(e) => setOtpConfirmPassword(e.target.value)}
                    />
                  </div>
                  {errors.confirmPassword && <span className="field-error">{errors.confirmPassword}</span>}
                </div>

                <button 
                  type="submit" 
                  className="submit-btn"
                  disabled={loading}
                  style={{ background: roleMeta[selectedRole]?.accent }}
                >
                  {loading ? (
                    <>
                      <RotateCw size={16} className="spin-icon" />
                      <span>Setting Password...</span>
                    </>
                  ) : (
                    <>
                      <span>Set Password & Continue</span>
                      <ArrowRight size={16} />
                    </>
                  )}
                </button>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '12px' }}>
                  <button 
                    type="button"
                    onClick={() => setForgotStep('email')}
                    style={{ background: 'transparent', border: 'none', color: '#94a3b8', fontSize: '0.8rem', cursor: 'pointer' }}
                  >
                    ← Change Email
                  </button>
                  <button 
                    type="button"
                    disabled={resendCooldown > 0 || loading}
                    onClick={handleRequestOtp}
                    style={{ background: 'transparent', border: 'none', color: resendCooldown > 0 ? '#64748b' : '#38bdf8', fontSize: '0.8rem', cursor: resendCooldown > 0 ? 'not-allowed' : 'pointer' }}
                  >
                    {resendCooldown > 0 ? `Resend OTP in ${resendCooldown}s` : 'Resend OTP'}
                  </button>
                </div>
              </form>
            )}
          </div>
        )}

        <div className="staff-auth-footer">
          <p>
            Accounts for HOD, Faculty, and Mentors are provisioned directly by the Campus Administrator.
          </p>
        </div>
      </div>
    </div>
  );
}
