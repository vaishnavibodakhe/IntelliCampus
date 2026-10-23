import React, { useState, useEffect } from 'react';
import { 
  ShieldCheck, 
  BookOpen, 
  Lock, 
  Mail, 
  User, 
  Building2, 
  Eye, 
  EyeOff, 
  ArrowRight, 
  AlertCircle, 
  AlertTriangle,
  CheckCircle2, 
  Clock,
  KeyRound,
  UserPlus,
  LogIn,
  RotateCw
} from 'lucide-react';
import { 
  loginStaffApi, 
  registerStaffApi, 
  forgotPasswordApi, 
  resetPasswordApi,
  saveStaffSession, 
  validateStaffLoginForm, 
  validateStaffRegisterForm, 
  validateForgotPasswordForm,
  validateOtpResetPasswordForm 
} from '../../utils/staffAuthService';
import './StaffAuth.css';

export default function RoleAuthView({ 
  role = 'PRINCIPAL', 
  onAuthSuccess, 
  noticeMessage = '',
  onSwitchRole 
}) {
  const isPrincipal = role === 'PRINCIPAL' || role === 'ADMIN';
  const [activeTab, setActiveTab] = useState('login'); // 'login' | 'register' | 'forgot'
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState('');
  const [apiSuccess, setApiSuccess] = useState('');
  const [registerSuccess, setRegisterSuccess] = useState(null);

  // Login form state
  const [loginForm, setLoginForm] = useState({
    email: '',
    password: ''
  });

  // Register form state (role is fixed)
  const [registerForm, setRegisterForm] = useState({
    name: '',
    email: '',
    role: isPrincipal ? 'PRINCIPAL' : 'HOD',
    department: isPrincipal ? '' : 'Computer Science',
    password: '',
    confirmPassword: '',
    phone: '',
    employeeId: ''
  });

  // Forgot password OTP flow state: 'email' (Step 1) | 'otp' (Step 2)
  const [forgotStep, setForgotStep] = useState('email');
  const [forgotEmail, setForgotEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [otpNewPassword, setOtpNewPassword] = useState('');
  const [otpConfirmPassword, setOtpConfirmPassword] = useState('');
  const [showOtpPassword, setShowOtpPassword] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);

  const [errors, setErrors] = useState({});

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

  const departments = [
    'Computer Science',
    'Information Technology',
    'Mechanical Engineering',
    'Civil Engineering',
    'Electronics & Telecommunication'
  ];

  const handleTabSwitch = (tab) => {
    setActiveTab(tab);
    setApiError('');
    setApiSuccess('');
    setRegisterSuccess(null);
    setErrors({});
    if (tab === 'forgot') {
      setForgotStep('email');
      setOtp('');
      setOtpNewPassword('');
      setOtpConfirmPassword('');
    }
  };

  // Login handler
  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    setApiSuccess('');

    const validation = validateStaffLoginForm(loginForm);
    if (!validation.isValid) {
      setErrors(validation.errors);
      return;
    }

    setLoading(true);
    try {
      const data = await loginStaffApi({ ...loginForm, role });
      
      // Strict role check: Do not save or overwrite session on mismatch
      const expectedRole = isPrincipal ? 'PRINCIPAL' : role;
      if (data.user?.role !== expectedRole && data.user?.role !== role) {
        const roleLabel = role === 'HOD' ? 'an HOD' : (role === 'FACULTY' ? 'a Faculty' : (role === 'MENTOR' ? 'a Mentor' : `a ${role}`));
        setApiError(
          isPrincipal 
            ? 'This account is not a Principal account' 
            : `This account is not ${roleLabel} account`
        );
        return;
      }

      saveStaffSession(data);
      setApiSuccess(`Welcome, ${data.user?.name || 'Staff member'}! Access granted.`);
      setTimeout(() => {
        if (onAuthSuccess) {
          onAuthSuccess(data);
        }
      }, 500);
    } catch (err) {
      setApiError(err.message || 'Login failed. Please check credentials.');
    } finally {
      setLoading(false);
    }
  };

  // Register handler
  const handleRegisterSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    setApiSuccess('');

    const payload = {
      ...registerForm,
      role: isAdmin ? 'ADMIN' : 'HOD',
      department: isAdmin ? '' : registerForm.department
    };

    const validation = validateStaffRegisterForm(payload);
    if (!validation.isValid) {
      setErrors(validation.errors);
      return;
    }

    setLoading(true);
    try {
      const data = await registerStaffApi(payload);
      setApiSuccess(`Registration successful! Your ${roleName} account has been created. Please sign in with your password.`);
      setActiveTab('login');
      setLoginForm(prev => ({ ...prev, email: payload.email }));
      setRegisterForm({
        name: '',
        email: '',
        role: isAdmin ? 'ADMIN' : 'HOD',
        department: isAdmin ? '' : 'Computer Science',
        password: '',
        confirmPassword: '',
        phone: '',
        employeeId: ''
      });
    } catch (err) {
      setApiError(err.message || 'Registration failed.');
    } finally {
      setLoading(false);
    }
  };

  // Step 1: Send OTP to registered email
  const handleSendOtpSubmit = async (e) => {
    if (e) e.preventDefault();
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
      const data = await forgotPasswordApi({ email: forgotEmail, role });
      setForgotStep('otp');
      setResendCooldown(60);
      setApiSuccess(data.message || 'OTP sent to your email. Please check your inbox.');
    } catch (err) {
      setApiError(err.message || 'Failed to send OTP.');
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Resend OTP with cooldown
  const handleResendOtp = async () => {
    if (resendCooldown > 0 || loading) return;
    setApiError('');
    setApiSuccess('');
    setLoading(true);
    try {
      const data = await forgotPasswordApi({ email: forgotEmail, role });
      setResendCooldown(60);
      setApiSuccess(data.message || 'A new OTP has been sent to your email.');
    } catch (err) {
      setApiError(err.message || 'Failed to resend OTP.');
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Reset password using 6-digit OTP
  const handleResetPasswordSubmit = async (e) => {
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
      const data = await resetPasswordApi({
        email: forgotEmail,
        otp,
        newPassword: otpNewPassword,
        confirmPassword: otpConfirmPassword,
        role
      });
      setApiSuccess(data.message || 'Password reset successfully! You can now sign in.');
      setLoginForm(prev => ({ ...prev, email: forgotEmail, password: '' }));
      setForgotStep('email');
      setOtp('');
      setOtpNewPassword('');
      setOtpConfirmPassword('');
      setActiveTab('login');
    } catch (err) {
      setApiError(err.message || 'Failed to reset password.');
    } finally {
      setLoading(false);
    }
  };

  const accentColor = isPrincipal ? '#6366f1' : '#8b5cf6';
  const roleName = isPrincipal ? 'Principal' : 'HOD';

  return (
    <div className="staff-auth-wrapper staff-auth-container">
      <div className="staff-auth-card" style={{ borderColor: `${accentColor}33` }}>
        {/* Header */}
        <div className="staff-auth-header">
          <div 
            className="staff-auth-icon-wrap"
            style={{
              background: `linear-gradient(135deg, ${accentColor}25, ${accentColor}10)`,
              borderColor: `${accentColor}40`,
              color: accentColor
            }}
          >
            {isPrincipal ? <ShieldCheck size={32} /> : <BookOpen size={32} />}
          </div>
          <h2 className="staff-auth-title">{roleName} Portal</h2>
          <p className="staff-auth-subtitle">
            {isPrincipal 
              ? 'Institutional operations governance & academic administration' 
              : 'Head of Department academic & faculty management'}
          </p>
        </div>

        {/* Notice Message Banner (e.g. "Admin access required") */}
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

        {/* Global Error Alert */}
        {apiError && (
          <div className="staff-alert staff-alert-error" style={{ marginBottom: '1.25rem' }}>
            <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
            <div>{apiError}</div>
          </div>
        )}

        {/* Global Success Alert */}
        {apiSuccess && (
          <div className="staff-alert staff-alert-success" style={{ marginBottom: '1.25rem' }}>
            <CheckCircle2 size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
            <div>{apiSuccess}</div>
          </div>
        )}

        {/* Tab Selection (NO role dropdown anywhere) */}
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
            onClick={() => handleTabSwitch('login')}
            className={`staff-tab-btn ${activeTab === 'login' ? 'active' : ''}`}
            style={{
              flex: 1,
              padding: '0.65rem 0.5rem',
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'login' ? `2px solid ${accentColor}` : '2px solid transparent',
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


          <button
            type="button"
            onClick={() => handleTabSwitch('forgot')}
            className={`staff-tab-btn ${activeTab === 'forgot' ? 'active' : ''}`}
            style={{
              flex: 1,
              padding: '0.65rem 0.5rem',
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'forgot' ? `2px solid ${accentColor}` : '2px solid transparent',
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
            Forgot Password
          </button>
        </div>

        {/* TAB 1: LOGIN */}
        {activeTab === 'login' && (
          <form onSubmit={handleLoginSubmit} className="staff-auth-form">
            <div className="staff-field-group">
              <label className="staff-field-label">Work Email</label>
              <div className="staff-input-wrapper">
                <Mail className="staff-input-icon" size={18} />
                <input 
                  type="email"
                  value={loginForm.email}
                  onChange={(e) => {
                    setLoginForm(prev => ({ ...prev, email: e.target.value }));
                    if (errors.email) setErrors(prev => ({ ...prev, email: '' }));
                  }}
                  placeholder={isPrincipal ? 'principal@college.edu' : 'hod.cs@intellicampus.edu'}
                  className={`staff-input ${errors.email ? 'has-error' : ''}`}
                  autoComplete="email"
                />
              </div>
              {errors.email && <span className="staff-error-text">{errors.email}</span>}
            </div>

            <div className="staff-field-group">
              <div className="staff-field-label">
                <span>Password</span>
                <button
                  type="button"
                  className="staff-link"
                  onClick={() => handleTabSwitch('forgot')}
                  style={{ fontSize: '0.78rem' }}
                >
                  Forgot?
                </button>
              </div>
              <div className="staff-input-wrapper">
                <Lock className="staff-input-icon" size={18} />
                <input 
                  type={showPassword ? 'text' : 'password'}
                  value={loginForm.password}
                  onChange={(e) => {
                    setLoginForm(prev => ({ ...prev, password: e.target.value }));
                    if (errors.password) setErrors(prev => ({ ...prev, password: '' }));
                  }}
                  placeholder="Enter your password"
                  className={`staff-input ${errors.password ? 'has-error' : ''}`}
                  autoComplete="current-password"
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
              {errors.password && <span className="staff-error-text">{errors.password}</span>}
            </div>

            <button 
              type="submit" 
              className="staff-submit-btn"
              disabled={loading}
              style={{ background: accentColor }}
            >
              {loading ? `Signing into ${roleName}...` : `Sign In as ${roleName}`}
              <ArrowRight size={18} />
            </button>
          </form>
        )}


        {/* TAB 3: FORGOT PASSWORD (OTP FLOW) */}
        {activeTab === 'forgot' && (
          forgotStep === 'email' ? (
            /* STEP 1: Enter email and send OTP */
            <form onSubmit={handleSendOtpSubmit} className="staff-auth-form">
              <div style={{ fontSize: '0.88rem', color: 'var(--text-secondary, #94a3b8)', lineHeight: '1.4' }}>
                Enter your registered {roleName} email. We'll send you a 6-digit verification OTP to reset your password.
              </div>

              <div className="staff-field-group">
                <label className="staff-field-label">Account Email</label>
                <div className="staff-input-wrapper">
                  <Mail className="staff-input-icon" size={18} />
                  <input 
                    type="email"
                    value={forgotEmail}
                    onChange={(e) => {
                      setForgotEmail(e.target.value);
                      if (errors.email) setErrors(prev => ({ ...prev, email: '' }));
                    }}
                    placeholder={`Enter your registered ${roleName} email`}
                    className={`staff-input ${errors.email ? 'has-error' : ''}`}
                    disabled={loading}
                    autoComplete="email"
                  />
                </div>
                {errors.email && <span className="staff-error-text">{errors.email}</span>}
              </div>

              <button 
                type="submit" 
                className="staff-submit-btn"
                disabled={loading}
                style={{ background: accentColor }}
              >
                {loading ? 'Sending OTP...' : 'Send OTP'}
                <ArrowRight size={18} />
              </button>
            </form>
          ) : (
            /* STEP 2: Enter 6-digit OTP and new password */
            <form onSubmit={handleResetPasswordSubmit} className="staff-auth-form">
              <div style={{ 
                display: 'flex', 
                alignItems: 'center', 
                justifyContent: 'space-between',
                padding: '0.75rem 1rem',
                background: 'rgba(255, 255, 255, 0.04)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                borderRadius: '8px',
                fontSize: '0.85rem'
              }}>
                <div style={{ color: 'var(--text-secondary, #94a3b8)' }}>
                  OTP sent to: <strong style={{ color: '#f8fafc' }}>{forgotEmail}</strong>
                </div>
                <button
                  type="button"
                  className="staff-link"
                  onClick={() => {
                    setForgotStep('email');
                    setApiError('');
                    setApiSuccess('');
                    setErrors({});
                  }}
                  disabled={loading}
                  style={{ fontSize: '0.8rem' }}
                >
                  Change Email
                </button>
              </div>

              {/* 6-digit OTP */}
              <div className="staff-field-group">
                <label className="staff-field-label">6-Digit Verification OTP</label>
                <div className="staff-input-wrapper">
                  <KeyRound className="staff-input-icon" size={18} />
                  <input 
                    type="text"
                    maxLength={6}
                    value={otp}
                    onChange={(e) => {
                      const val = e.target.value.replace(/\D/g, '');
                      setOtp(val);
                      if (errors.otp) setErrors(prev => ({ ...prev, otp: '' }));
                    }}
                    placeholder="Enter 6-digit OTP"
                    className={`staff-input ${errors.otp ? 'has-error' : ''}`}
                    disabled={loading}
                    autoFocus
                    style={{ letterSpacing: '0.2em', fontWeight: 600, fontSize: '1.05rem' }}
                  />
                </div>
                {errors.otp && <span className="staff-error-text">{errors.otp}</span>}
              </div>

              {/* New Password */}
              <div className="staff-field-group">
                <label className="staff-field-label">New Password</label>
                <div className="staff-input-wrapper">
                  <Lock className="staff-input-icon" size={18} />
                  <input 
                    type={showOtpPassword ? 'text' : 'password'}
                    value={otpNewPassword}
                    onChange={(e) => {
                      setOtpNewPassword(e.target.value);
                      if (errors.newPassword) setErrors(prev => ({ ...prev, newPassword: '' }));
                    }}
                    placeholder="8+ chars (upper, lower, number, special)"
                    className={`staff-input ${errors.newPassword ? 'has-error' : ''}`}
                    disabled={loading}
                    autoComplete="new-password"
                  />
                  <button 
                    type="button" 
                    className="staff-eye-btn"
                    onClick={() => setShowOtpPassword(!showOtpPassword)}
                    tabIndex="-1"
                  >
                    {showOtpPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                  </button>
                </div>
                {errors.newPassword && <span className="staff-error-text">{errors.newPassword}</span>}
              </div>

              {/* Confirm Password */}
              <div className="staff-field-group">
                <label className="staff-field-label">Confirm New Password</label>
                <div className="staff-input-wrapper">
                  <Lock className="staff-input-icon" size={18} />
                  <input 
                    type={showOtpPassword ? 'text' : 'password'}
                    value={otpConfirmPassword}
                    onChange={(e) => {
                      setOtpConfirmPassword(e.target.value);
                      if (errors.confirmPassword) setErrors(prev => ({ ...prev, confirmPassword: '' }));
                    }}
                    placeholder="Re-enter new password"
                    className={`staff-input ${errors.confirmPassword ? 'has-error' : ''}`}
                    disabled={loading}
                    autoComplete="new-password"
                  />
                </div>
                {errors.confirmPassword && <span className="staff-error-text">{errors.confirmPassword}</span>}
              </div>

              {/* Action buttons */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '0.5rem' }}>
                <button 
                  type="submit" 
                  className="staff-submit-btn"
                  disabled={loading}
                  style={{ background: accentColor }}
                >
                  {loading ? 'Updating Password...' : 'Reset Password'}
                  <ArrowRight size={18} />
                </button>

                <button
                  type="button"
                  onClick={handleResendOtp}
                  disabled={loading || resendCooldown > 0}
                  className="staff-secondary-btn"
                >
                  <RotateCw size={15} className={loading ? 'animate-spin' : ''} />
                  {resendCooldown > 0 ? `Resend OTP in ${resendCooldown}s` : 'Resend OTP'}
                </button>
              </div>
            </form>
          )
        )}

        {/* Role switch helper link if provided */}
        {onSwitchRole && (
          <div className="staff-links" style={{ justifyContent: 'center' }}>
            <button 
              type="button" 
              className="staff-link"
              onClick={() => onSwitchRole(isPrincipal ? 'HOD' : 'PRINCIPAL')}
            >
              Switch to {isPrincipal ? 'HOD' : 'Principal'} Portal &rarr;
            </button>
          </div>
        )}

        <div style={{ marginTop: '1.5rem', paddingTop: '1rem', borderTop: '1px solid rgba(255,255,255,0.08)', textAlign: 'center', fontSize: '0.8rem', color: '#94a3b8' }}>
          <span>{isPrincipal ? 'Principal account credentials are configured via secure institutional environment.' : 'HOD accounts are provisioned exclusively by the Principal.'}</span>
        </div>
      </div>
    </div>
  );
}
