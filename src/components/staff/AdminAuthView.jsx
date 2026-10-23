import React, { useState, useEffect } from 'react';
import {
  ShieldCheck,
  Lock,
  Mail,
  User,
  Eye,
  EyeOff,
  ArrowRight,
  AlertCircle,
  CheckCircle2,
  KeyRound,
  LogIn,
  UserPlus,
  RotateCw,
  ShieldAlert
} from 'lucide-react';
import {
  loginStaffApi,
  registerStaffApi,
  forgotPasswordApi,
  resetPasswordApi,
  checkAdminBootstrapApi,
  saveStaffSession,
  validateStaffLoginForm,
  validateStaffRegisterForm,
  validateForgotPasswordForm,
  validateOtpResetPasswordForm
} from '../../utils/staffAuthService';
import './StaffAuth.css';

export default function AdminAuthView({ onAuthSuccess, noticeMessage = '' }) {
  const [canBootstrap, setCanBootstrap] = useState(false);
  const [activeTab, setActiveTab] = useState('login'); // 'login' | 'register' | 'forgot'
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState('');
  const [apiSuccess, setApiSuccess] = useState('');

  // Login form state
  const [loginForm, setLoginForm] = useState({
    email: '',
    password: ''
  });

  // Bootstrap Admin Register form state
  const [registerForm, setRegisterForm] = useState({
    name: '',
    email: '',
    role: 'ADMIN',
    department: '',
    password: '',
    confirmPassword: '',
    employeeCode: ''
  });

  // Forgot Password OTP flow state
  const [forgotStep, setForgotStep] = useState('email');
  const [forgotEmail, setForgotEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [otpNewPassword, setOtpNewPassword] = useState('');
  const [otpConfirmPassword, setOtpConfirmPassword] = useState('');
  const [showOtpPassword, setShowOtpPassword] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);

  const [errors, setErrors] = useState({});

  useEffect(() => {
    checkAdminBootstrapApi()
      .then((res) => {
        if (res && res.canBootstrap) {
          setCanBootstrap(true);
          setActiveTab('register'); // Default to bootstrap registration when 0 admins exist
        } else {
          setCanBootstrap(false);
          setActiveTab('login');
        }
      })
      .catch(() => {
        setCanBootstrap(false);
      });
  }, []);

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
        role: 'ADMIN'
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

  // One-time Bootstrap Admin Registration submission
  const handleRegisterSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    setApiSuccess('');

    const validation = validateStaffRegisterForm({
      name: registerForm.name,
      email: registerForm.email,
      password: registerForm.password,
      confirmPassword: registerForm.confirmPassword,
      role: 'ADMIN',
      department: 'Central Administration'
    });

    if (!validation.isValid) {
      setErrors(validation.errors);
      return;
    }
    setErrors({});
    setLoading(true);

    try {
      await registerStaffApi({
        name: registerForm.name,
        email: registerForm.email,
        password: registerForm.password,
        role: 'ADMIN',
        department: 'Central Administration',
        employeeCode: registerForm.employeeCode
      });

      setApiSuccess('Initial Principal account bootstrapped successfully! You can now log in.');
      setCanBootstrap(false);
      setTimeout(() => {
        setActiveTab('login');
        setLoginForm({ email: registerForm.email, password: registerForm.password });
      }, 1500);
    } catch (err) {
      const msg = err?.message || '';
      if (
        err?.status === 403 ||
        msg.includes('403') ||
        msg.toLowerCase().includes('forbidden') ||
        msg.toLowerCase().includes('closed') ||
        msg.toLowerCase().includes('already exists')
      ) {
        setApiError('Principal registration is closed. Contact an existing Principal.');
      } else {
        setApiError(msg || 'Registration failed.');
      }
    } finally {
      setLoading(false);
    }
  };

  // Step 1: Request OTP for password reset
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
        role: 'ADMIN'
      });
      setApiSuccess(response.message || 'If a principal account exists, a 6-digit OTP has been sent.');
      setForgotStep('otp');
      setResendCooldown(60);
    } catch (err) {
      setApiError(err.message || 'Failed to dispatch verification OTP.');
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Verify OTP and reset password
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
        role: 'ADMIN'
      });

      setApiSuccess(response.message || 'Password successfully reset! You may now sign in.');
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

  return (
    <div className="staff-auth-container">
      <div className="staff-auth-card">
        {/* Header */}
        <div className="staff-auth-header">
          <div className="staff-brand-badge" style={{ borderColor: '#6366f1' }}>
            <ShieldCheck size={28} color="#6366f1" />
          </div>
          <h2>Principal Portal</h2>
          <p className="staff-auth-subtitle">
            Secure institutional leadership & governance console
          </p>
        </div>

        {noticeMessage && (
          <div className="staff-auth-alert info">
            <ShieldAlert size={16} />
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

        {/* Tab Switcher */}
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
            className={`staff-tab-btn ${activeTab === 'register' ? 'active' : ''}`}
            onClick={() => handleTabSwitch('register')}
          >
            <UserPlus size={15} />
            <span>Register</span>
          </button>

          <button
            type="button"
            className={`staff-tab-btn ${activeTab === 'forgot' ? 'active' : ''}`}
            onClick={() => handleTabSwitch('forgot')}
          >
            <KeyRound size={15} />
            <span>Forgot Password</span>
          </button>
        </div>

        {/* Login Form */}
        {activeTab === 'login' && (
          <form onSubmit={handleLoginSubmit} className="staff-form">
            <div className="form-group">
              <label htmlFor="admin-login-email">Principal Email</label>
              <div className="input-wrap">
                <Mail size={16} className="input-icon" />
                <input
                  id="admin-login-email"
                  type="email"
                  placeholder="principal@intellicampus.edu"
                  value={loginForm.email}
                  onChange={(e) => setLoginForm({ ...loginForm, email: e.target.value })}
                  autoComplete="email"
                />
              </div>
              {errors.email && <span className="field-error">{errors.email}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="admin-login-password">Principal Password</label>
              <div className="input-wrap">
                <Lock size={16} className="input-icon" />
                <input
                  id="admin-login-password"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Enter principal password"
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

            <button
              type="submit"
              className="submit-btn"
              disabled={loading}
              style={{ background: '#6366f1' }}
            >
              {loading ? (
                <>
                  <RotateCw size={16} className="spin-icon" />
                  <span>Verifying Credentials...</span>
                </>
              ) : (
                <>
                  <span>Sign In to Principal Console</span>
                  <ArrowRight size={16} />
                </>
              )}
            </button>
          </form>
        )}

        {/* Principal Registration Form */}
        {activeTab === 'register' && (
          <form onSubmit={handleRegisterSubmit} className="staff-form">
            {canBootstrap && (
              <div style={{ background: 'rgba(99, 102, 241, 0.1)', border: '1px solid rgba(99, 102, 241, 0.3)', borderRadius: '8px', padding: '10px 14px', marginBottom: '14px', fontSize: '0.82rem', color: '#c7d2fe' }}>
                <strong>Bootstrap Mode:</strong> No Principal account exists yet. Create the initial Principal account to initialize the system.
              </div>
            )}

            <div className="form-group">
              <label htmlFor="admin-reg-name">Full Name</label>
              <div className="input-wrap">
                <User size={16} className="input-icon" />
                <input
                  id="admin-reg-name"
                  type="text"
                  placeholder="e.g. Dr. Alan Turing"
                  value={registerForm.name}
                  onChange={(e) => setRegisterForm({ ...registerForm, name: e.target.value })}
                />
              </div>
              {errors.name && <span className="field-error">{errors.name}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="admin-reg-email">Principal Email</label>
              <div className="input-wrap">
                <Mail size={16} className="input-icon" />
                <input
                  id="admin-reg-email"
                  type="email"
                  placeholder="principal@intellicampus.edu"
                  value={registerForm.email}
                  onChange={(e) => setRegisterForm({ ...registerForm, email: e.target.value })}
                />
              </div>
              {errors.email && <span className="field-error">{errors.email}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="admin-reg-password">Principal Password</label>
              <div className="input-wrap">
                <Lock size={16} className="input-icon" />
                <input
                  id="admin-reg-password"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Min 8 chars with upper, lower, digit & symbol"
                  value={registerForm.password}
                  onChange={(e) => setRegisterForm({ ...registerForm, password: e.target.value })}
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

            <div className="form-group">
              <label htmlFor="admin-reg-confirm">Confirm Principal Password</label>
              <div className="input-wrap">
                <Lock size={16} className="input-icon" />
                <input
                  id="admin-reg-confirm"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Re-enter principal password"
                  value={registerForm.confirmPassword}
                  onChange={(e) => setRegisterForm({ ...registerForm, confirmPassword: e.target.value })}
                />
              </div>
              {errors.confirmPassword && <span className="field-error">{errors.confirmPassword}</span>}
            </div>

            <button
              type="submit"
              className="submit-btn"
              disabled={loading}
              style={{ background: '#6366f1' }}
            >
              {loading ? (
                <>
                  <RotateCw size={16} className="spin-icon" />
                  <span>Registering Principal...</span>
                </>
              ) : (
                <>
                  <span>{canBootstrap ? 'Initialize Principal Account' : 'Register Principal'}</span>
                  <ArrowRight size={16} />
                </>
              )}
            </button>
          </form>
        )}

        {/* Forgot Password Flow */}
        {activeTab === 'forgot' && (
          <div className="forgot-flow">
            {forgotStep === 'email' ? (
              <form onSubmit={handleRequestOtp} className="staff-form">
                <div className="form-group">
                  <label htmlFor="admin-forgot-email">Principal Email</label>
                  <div className="input-wrap">
                    <Mail size={16} className="input-icon" />
                    <input
                      id="admin-forgot-email"
                      type="email"
                      placeholder="principal@intellicampus.edu"
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
                  style={{ background: '#6366f1' }}
                >
                  {loading ? (
                    <>
                      <RotateCw size={16} className="spin-icon" />
                      <span>Sending OTP...</span>
                    </>
                  ) : (
                    <>
                      <span>Send Recovery OTP</span>
                      <ArrowRight size={16} />
                    </>
                  )}
                </button>
              </form>
            ) : (
              <form onSubmit={handleVerifyOtpAndReset} className="staff-form">
                <div className="form-group">
                  <label htmlFor="admin-otp">6-Digit Verification OTP</label>
                  <div className="input-wrap">
                    <KeyRound size={16} className="input-icon" />
                    <input
                      id="admin-otp"
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
                  <label htmlFor="admin-new-password">New Principal Password</label>
                  <div className="input-wrap">
                    <Lock size={16} className="input-icon" />
                    <input
                      id="admin-new-password"
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
                  <label htmlFor="admin-confirm-password">Confirm New Principal Password</label>
                  <div className="input-wrap">
                    <Lock size={16} className="input-icon" />
                    <input
                      id="admin-confirm-password"
                      type={showOtpPassword ? 'text' : 'password'}
                      placeholder="Re-enter password"
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
                  style={{ background: '#6366f1' }}
                >
                  {loading ? (
                    <>
                      <RotateCw size={16} className="spin-icon" />
                      <span>Updating Password...</span>
                    </>
                  ) : (
                    <>
                      <span>Reset Password</span>
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
                    style={{ background: 'transparent', border: 'none', color: resendCooldown > 0 ? '#64748b' : '#818cf8', fontSize: '0.8rem', cursor: resendCooldown > 0 ? 'not-allowed' : 'pointer' }}
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
            IntelliCampus Principal Portal. Restricted to authorized institutional leadership.
          </p>
        </div>
      </div>
    </div>
  );
}
