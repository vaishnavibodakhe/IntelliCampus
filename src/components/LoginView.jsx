import React, { useState, useRef, useEffect, useCallback } from 'react';
import { 
  LogIn, 
  UserPlus, 
  KeyRound, 
  User, 
  Mail, 
  Camera, 
  RefreshCw, 
  CheckCircle2, 
  AlertCircle, 
  Eye, 
  EyeOff, 
  ShieldCheck, 
  Upload, 
  Sparkles, 
  Lock, 
  BadgeCheck, 
  Hash,
  School,
  X
} from 'lucide-react';
import { api, setToken } from '../api/client';
import { authenticateUser } from '../utils/auth';

export default function LoginView({ users = [], onLogin }) {
  // Navigation mode: 'LOGIN' | 'REGISTER' | 'FORGOT'
  const [viewMode, setViewMode] = useState('LOGIN');

  // Shared alerts
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [loading, setLoading] = useState(false);

  // --- LOGIN STATE ---
  const [loginCode, setLoginCode] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [showLoginPassword, setShowLoginPassword] = useState(false);

  // --- REGISTRATION STATE ---
  const [regName, setRegName] = useState('');
  const [regEmail, setRegEmail] = useState('');
  const [regLoginCode, setRegLoginCode] = useState('');
  const [regRollNo, setRegRollNo] = useState('');
  const [regDepartment, setRegDepartment] = useState('Computer Engineering');
  const [regYear, setRegYear] = useState('TE');
  const [regDivision, setRegDivision] = useState('A');
  const [regPassword, setRegPassword] = useState('');
  const [regConfirmPassword, setRegConfirmPassword] = useState('');
  const [showRegPassword, setShowRegPassword] = useState(false);
  const [showRegConfirmPassword, setShowRegConfirmPassword] = useState(false);
  const [regConsent, setRegConsent] = useState(false);

  // Biometric Photo Capture State
  const [cameraActive, setCameraActive] = useState(false);
  const [photoBlob, setPhotoBlob] = useState(null);
  const [photoPreviewUrl, setPhotoPreviewUrl] = useState('');
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const fileInputRef = useRef(null);

  // --- FORGOT / RESET PASSWORD STATE ---
  const [forgotStep, setForgotStep] = useState(1); // 1 = Request code, 2 = Enter code & new password
  const [forgotLoginCodeOrEmail, setForgotLoginCodeOrEmail] = useState('');
  const [demoResetCode, setDemoResetCode] = useState('');
  const [resetCode, setResetCode] = useState('');
  const [resetLoginCode, setResetLoginCode] = useState('');
  const [resetNewPassword, setResetNewPassword] = useState('');
  const [resetConfirmPassword, setResetConfirmPassword] = useState('');
  const [showResetPassword, setShowResetPassword] = useState(false);
  const [showResetConfirmPassword, setShowResetConfirmPassword] = useState(false);

  // Student-only demo codes from seed data
  const demoCodes = [
    'student.aarav',
    'student.priya',
    'student.rohan',
    'student.kavya'
  ];

  // Stop webcam helper
  const stopCamera = useCallback(() => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }
    setCameraActive(false);
  }, []);

  // Clean up camera stream and object URL
  useEffect(() => {
    return () => {
      stopCamera();
      if (photoPreviewUrl) {
        URL.revokeObjectURL(photoPreviewUrl);
      }
    };
  }, [photoPreviewUrl, stopCamera]);

  // Clean up camera stream when viewMode changes
  useEffect(() => {
    stopCamera();
  }, [viewMode, stopCamera]);

  // Start webcam
  const startCamera = async () => {
    setError('');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: 'user',
          width: { ideal: 640 },
          height: { ideal: 480 }
        }
      });
      streamRef.current = stream;
      setCameraActive(true);
      setTimeout(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.play().catch(err => console.warn('Video play warning:', err));
        }
      }, 50);
    } catch (err) {
      console.warn('Camera access denied or unavailable:', err);
      setError('Camera access unavailable. You can upload a photo file below instead.');
      setCameraActive(false);
    }
  };

  // Capture webcam snapshot
  const capturePhoto = () => {
    if (!videoRef.current) return;
    const video = videoRef.current;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    canvas.toBlob((blob) => {
      if (blob) {
        if (photoPreviewUrl) URL.revokeObjectURL(photoPreviewUrl);
        setPhotoBlob(blob);
        setPhotoPreviewUrl(URL.createObjectURL(blob));
        stopCamera();
        setError('');
      }
    }, 'image/jpeg', 0.95);
  };

  // Retake photo
  const handleRetakePhoto = () => {
    if (photoPreviewUrl) URL.revokeObjectURL(photoPreviewUrl);
    setPhotoBlob(null);
    setPhotoPreviewUrl('');
    startCamera();
  };

  // File upload fallback
  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      setError('Please upload a valid JPEG or PNG image.');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setError('Photo file size must not exceed 5 MB.');
      return;
    }

    stopCamera();
    if (photoPreviewUrl) URL.revokeObjectURL(photoPreviewUrl);
    setPhotoBlob(file);
    setPhotoPreviewUrl(URL.createObjectURL(file));
    setError('');
  };

  // Auto-fill demo account credentials
  const handleSelectDemoCode = (code) => {
    setLoginCode(code);
    setLoginPassword('CampusPass2026!');
    setError('');
  };

  // --- SUBMIT: LOGIN ---
  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    if (!loginCode.trim()) {
      setError('Please enter your login code.');
      return;
    }
    if (!loginPassword) {
      setError('Password cannot be empty.');
      return;
    }

    setLoading(true);
    try {
      // 1. Attempt backend authentication
      const res = await api.login({
        loginCode: loginCode.trim(),
        password: loginPassword,
        portal: 'STUDENT',
        expectedRole: 'STUDENT'
      });

      if (res && res.user && res.user.role !== 'STUDENT') {
        setError('Invalid login code or password.');
        return;
      }
      if (res && res.accessToken) {
        setToken(res.accessToken);
      }
      if (onLogin && res.user) {
        onLogin(res.user);
      }
    } catch (err) {
      console.warn('Backend login attempt returned:', err);

      // 2. If network/server unreachable in offline/mock environment, fallback to mock data
      const isNetworkError = err.message && (
        err.message.includes('Failed to fetch') || 
        err.message.includes('NetworkError') ||
        err.message.includes('HTTP 502') ||
        err.message.includes('HTTP 503')
      );

      if (isNetworkError && users && users.length > 0) {
        const mockResult = authenticateUser(users, loginCode, loginPassword);
        if (mockResult.success) {
          if (mockResult.user?.role !== 'STUDENT') {
            setError('Invalid login code or password.');
            return;
          }
          if (onLogin) onLogin(mockResult.user);
          return;
        }
      }

      // Display specific backend error (e.g. 423 lockout or 401 invalid credentials)
      setError(err.message || 'Invalid login code or password.');
    } finally {
      setLoading(false);
    }
  };

  // --- SUBMIT: STUDENT REGISTRATION ---
  const handleRegisterSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    // Field Validations
    if (!regName.trim()) {
      setError('Full Name is required.');
      return;
    }
    if (!regEmail.trim() || !regEmail.includes('@')) {
      setError('Please enter a valid institutional email address.');
      return;
    }
    if (!regLoginCode.trim()) {
      setError('Login code is required (e.g., student.aarav).');
      return;
    }
    if (!regRollNo.trim()) {
      setError('Roll number is required.');
      return;
    }
    if (!regPassword || regPassword.length < 8) {
      setError('Password must be at least 8 characters long.');
      return;
    }
    if (regPassword !== regConfirmPassword) {
      setError('Passwords do not match. Please re-enter.');
      return;
    }
    if (!photoBlob) {
      setError('Facial photo is required for biometric campus identity verification. Please capture with webcam or upload a photo.');
      return;
    }
    if (!regConsent) {
      setError('Biometric consent is mandatory to create a student account and enable automated attendance.');
      return;
    }

    setLoading(true);
    try {
      const formData = new FormData();
      formData.append('name', regName.trim());
      formData.append('email', regEmail.trim());
      formData.append('loginCode', regLoginCode.trim());
      formData.append('rollNo', regRollNo.trim());
      formData.append('department', regDepartment);
      formData.append('year', regYear);
      formData.append('division', regDivision);
      formData.append('password', regPassword);
      formData.append('confirmPassword', regConfirmPassword);
      formData.append('biometricConsent', 'true');
      formData.append('consentVersion', '1.0');
      formData.append('photo', photoBlob, 'face_capture.jpg');

      const createdUser = await api.register(formData);

      // On successful registration:
      setSuccessMsg(`Account created successfully for ${createdUser.name || regName}! Your facial biometric profile has been enrolled. Please sign in with your credentials.`);
      setLoginCode(createdUser.userCode || regLoginCode.trim());
      setLoginPassword('');
      setViewMode('LOGIN');

      // Clear register inputs
      setRegName('');
      setRegEmail('');
      setRegLoginCode('');
      setRegRollNo('');
      setRegPassword('');
      setRegConfirmPassword('');
      setRegConsent(false);
      setPhotoBlob(null);
      if (photoPreviewUrl) URL.revokeObjectURL(photoPreviewUrl);
      setPhotoPreviewUrl('');
    } catch (err) {
      console.error('Registration error:', err);
      setError(err.message || 'Registration failed. Please check your information and try again.');
    } finally {
      setLoading(false);
    }
  };

  // --- SUBMIT: FORGOT PASSWORD (STEP 1) ---
  const handleForgotSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    if (!forgotLoginCodeOrEmail.trim()) {
      setError('Please enter your login code or registered email address.');
      return;
    }

    setLoading(true);
    try {
      const res = await api.forgotPassword({
        loginCodeOrEmail: forgotLoginCodeOrEmail.trim()
      });

      setSuccessMsg(res.message || 'If an account matches your details, a 6-digit reset code has been dispatched.');
      setResetLoginCode(forgotLoginCodeOrEmail.trim());

      if (res.demoResetCode) {
        setDemoResetCode(res.demoResetCode);
        setResetCode(res.demoResetCode);
      }

      setForgotStep(2);
    } catch (err) {
      console.error('Forgot password error:', err);
      setError(err.message || 'Unable to process reset request. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // --- SUBMIT: RESET PASSWORD (STEP 2) ---
  const handleResetSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    if (!resetCode.trim()) {
      setError('Please enter the 6-digit verification code.');
      return;
    }
    if (!resetNewPassword || resetNewPassword.length < 8) {
      setError('New password must be at least 8 characters long.');
      return;
    }
    if (resetNewPassword !== resetConfirmPassword) {
      setError('New passwords do not match. Please re-enter.');
      return;
    }

    setLoading(true);
    try {
      const res = await api.resetPassword({
        code: resetCode.trim(),
        newPassword: resetNewPassword,
        confirmPassword: resetConfirmPassword,
        loginCode: resetLoginCode.trim()
      });

      setSuccessMsg(res.message || 'Password reset successfully! You can now sign in with your new password.');
      setLoginCode(resetLoginCode.trim());
      setLoginPassword('');
      setDemoResetCode('');
      setResetCode('');
      setResetNewPassword('');
      setResetConfirmPassword('');
      setForgotStep(1);
      setViewMode('LOGIN');
    } catch (err) {
      console.error('Reset password error:', err);
      setError(err.message || 'Failed to reset password. Please verify your code and try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-viewport animate-fade-in">
      <div className={`login-card glass-card ${viewMode === 'REGISTER' ? 'register-mode' : ''}`}>
        
        {/* Navigation Tabs */}
        <div className="auth-nav-tabs">
          <button
            type="button"
            className={`auth-tab-btn ${viewMode === 'LOGIN' ? 'active' : ''}`}
            onClick={() => {
              setViewMode('LOGIN');
              setError('');
            }}
          >
            <LogIn size={15} />
            <span>Sign In</span>
          </button>
          <button
            type="button"
            className={`auth-tab-btn ${viewMode === 'REGISTER' ? 'active' : ''}`}
            onClick={() => {
              setViewMode('REGISTER');
              setError('');
            }}
          >
            <UserPlus size={15} />
            <span>Create Account</span>
          </button>
          <button
            type="button"
            className={`auth-tab-btn ${viewMode === 'FORGOT' ? 'active' : ''}`}
            onClick={() => {
              setViewMode('FORGOT');
              setError('');
            }}
          >
            <KeyRound size={15} />
            <span>Reset Password</span>
          </button>
        </div>

        {/* Header Branding */}
        <div className="login-header">
          <div className="login-icon-badge">
            {viewMode === 'LOGIN' && <Sparkles size={24} />}
            {viewMode === 'REGISTER' && <UserPlus size={24} />}
            {viewMode === 'FORGOT' && <ShieldCheck size={24} />}
          </div>

          <h2 className="login-title">
            {viewMode === 'LOGIN' && 'Student Portal'}
            {viewMode === 'REGISTER' && 'Student Self-Registration'}
            {viewMode === 'FORGOT' && (forgotStep === 1 ? 'Forgot Password' : 'Set New Password')}
          </h2>

          <p className="login-subtitle">
            {viewMode === 'LOGIN' && 'Enter your institutional login code and password to access your portal.'}
            {viewMode === 'REGISTER' && 'Create your student account with mandatory biometric facial enrollment for automated attendance.'}
            {viewMode === 'FORGOT' && (forgotStep === 1 
              ? 'Enter your institutional login code or email to receive a 6-digit one-time reset code.' 
              : 'Enter the 6-digit verification code and configure your new secure password.'
            )}
          </p>
        </div>

        {/* Success Alert Banner */}
        {successMsg && (
          <div className="login-success-alert" role="status">
            <CheckCircle2 size={18} className="flex-shrink-0" />
            <span>{successMsg}</span>
          </div>
        )}

        {/* Error Alert Banner */}
        {error && (
          <div className="login-error-alert" role="alert">
            <AlertCircle size={18} className="flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* ================================================================= */}
        {/* VIEW 1: SIGN IN VIEW */}
        {/* ================================================================= */}
        {viewMode === 'LOGIN' && (
          <>
            <form onSubmit={handleLoginSubmit} className="login-form">
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
                  required
                />
              </div>

              <div className="form-group">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <label htmlFor="password" className="form-label">
                    <KeyRound size={14} className="inline mr-1" /> Password
                  </label>
                  <button
                    type="button"
                    className="auth-link-btn"
                    onClick={() => {
                      setForgotLoginCodeOrEmail(loginCode);
                      setViewMode('FORGOT');
                      setError('');
                    }}
                    style={{ fontSize: '0.78rem' }}
                  >
                    Forgot password?
                  </button>
                </div>
                <div className="password-input-wrapper">
                  <input
                    id="password"
                    type={showLoginPassword ? 'text' : 'password'}
                    className="login-input"
                    placeholder="Enter your password"
                    value={loginPassword}
                    onChange={(e) => {
                      setLoginPassword(e.target.value);
                      if (error) setError('');
                    }}
                    autoComplete="current-password"
                    required
                  />
                  <button
                    type="button"
                    className="password-toggle-btn"
                    onClick={() => setShowLoginPassword(!showLoginPassword)}
                    title={showLoginPassword ? 'Hide password' : 'Show password'}
                  >
                    {showLoginPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              <button type="submit" className="login-submit-btn" disabled={loading}>
                {loading ? (
                  <>
                    <RefreshCw size={18} className="animate-spin" />
                    <span>Signing in...</span>
                  </>
                ) : (
                  <>
                    <LogIn size={18} />
                    <span>Login</span>
                  </>
                )}
              </button>
            </form>

            <div className="auth-links-group" style={{ marginTop: '1rem', textAlign: 'center' }}>
              <div style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                New student?{' '}
                <button
                  type="button"
                  className="auth-link-btn"
                  onClick={() => {
                    setViewMode('REGISTER');
                    setError('');
                  }}
                >
                  Create student account
                </button>
              </div>
            </div>

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
                    title={`Click to fill login code ${code} and password CampusPass2026!`}
                  >
                    {code}
                  </button>
                ))}
              </div>
              <p className="demo-hint-text">
                * Seed demo credentials password: <strong style={{ color: 'var(--text-primary)' }}>CampusPass2026!</strong>
              </p>
            </div>
          </>
        )}

        {/* ================================================================= */}
        {/* VIEW 2: STUDENT REGISTRATION VIEW */}
        {/* ================================================================= */}
        {viewMode === 'REGISTER' && (
          <form onSubmit={handleRegisterSubmit} className="login-form">
            <div style={{ textAlign: 'center' }}>
              <span className="student-only-badge">
                <BadgeCheck size={14} /> Student Role Only
              </span>
            </div>

            {/* Row 1: Full Name & Email */}
            <div className="form-grid-2">
              <div className="form-group">
                <label className="form-label" htmlFor="regName">
                  <User size={14} className="inline mr-1" /> Full Name *
                </label>
                <input
                  id="regName"
                  type="text"
                  className="login-input"
                  placeholder="e.g. Aarav Sharma"
                  value={regName}
                  onChange={(e) => setRegName(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="regEmail">
                  <Mail size={14} className="inline mr-1" /> Institutional Email *
                </label>
                <input
                  id="regEmail"
                  type="email"
                  className="login-input"
                  placeholder="e.g. student.aarav@campus.edu"
                  value={regEmail}
                  onChange={(e) => setRegEmail(e.target.value)}
                  required
                />
              </div>
            </div>

            {/* Row 2: Login Code & Roll Number */}
            <div className="form-grid-2">
              <div className="form-group">
                <label className="form-label" htmlFor="regLoginCode">
                  <Hash size={14} className="inline mr-1" /> Login Code *
                </label>
                <input
                  id="regLoginCode"
                  type="text"
                  className="login-input"
                  placeholder="e.g. student.aarav"
                  value={regLoginCode}
                  onChange={(e) => setRegLoginCode(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="regRollNo">
                  <School size={14} className="inline mr-1" /> Roll Number *
                </label>
                <input
                  id="regRollNo"
                  type="text"
                  className="login-input"
                  placeholder="e.g. CS-2026-042"
                  value={regRollNo}
                  onChange={(e) => setRegRollNo(e.target.value)}
                  required
                />
              </div>
            </div>

            {/* Row 3: Department, Year & Division */}
            <div className="form-grid-2">
              <div className="form-group">
                <label className="form-label" htmlFor="regDepartment">
                  Department *
                </label>
                <select
                  id="regDepartment"
                  className="login-input"
                  value={regDepartment}
                  onChange={(e) => setRegDepartment(e.target.value)}
                >
                  <option value="Computer Engineering">Computer Engineering</option>
                  <option value="Information Technology">Information Technology</option>
                  <option value="AI & Data Science">AI & Data Science</option>
                  <option value="Electronics & Telecommunication">Electronics & Telecommunication</option>
                  <option value="Mechanical Engineering">Mechanical Engineering</option>
                </select>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem' }}>
                <div className="form-group">
                  <label className="form-label" htmlFor="regYear">
                    Year *
                  </label>
                  <select
                    id="regYear"
                    className="login-input"
                    value={regYear}
                    onChange={(e) => setRegYear(e.target.value)}
                  >
                    <option value="FE">First Year (FE)</option>
                    <option value="SE">Second Year (SE)</option>
                    <option value="TE">Third Year (TE)</option>
                    <option value="BE">Final Year (BE)</option>
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="regDivision">
                    Division *
                  </label>
                  <select
                    id="regDivision"
                    className="login-input"
                    value={regDivision}
                    onChange={(e) => setRegDivision(e.target.value)}
                  >
                    <option value="A">Division A</option>
                    <option value="B">Division B</option>
                    <option value="C">Division C</option>
                  </select>
                </div>
              </div>
            </div>

            {/* Row 4: Password & Confirm Password */}
            <div className="form-grid-2">
              <div className="form-group">
                <label className="form-label" htmlFor="regPassword">
                  <Lock size={14} className="inline mr-1" /> Password (min 8 chars) *
                </label>
                <div className="password-input-wrapper">
                  <input
                    id="regPassword"
                    type={showRegPassword ? 'text' : 'password'}
                    className="login-input"
                    placeholder="Min 8 characters"
                    value={regPassword}
                    onChange={(e) => setRegPassword(e.target.value)}
                    required
                  />
                  <button
                    type="button"
                    className="password-toggle-btn"
                    onClick={() => setShowRegPassword(!showRegPassword)}
                    title={showRegPassword ? 'Hide password' : 'Show password'}
                  >
                    {showRegPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="regConfirmPassword">
                  <Lock size={14} className="inline mr-1" /> Confirm Password *
                </label>
                <div className="password-input-wrapper">
                  <input
                    id="regConfirmPassword"
                    type={showRegConfirmPassword ? 'text' : 'password'}
                    className="login-input"
                    placeholder="Confirm password"
                    value={regConfirmPassword}
                    onChange={(e) => setRegConfirmPassword(e.target.value)}
                    required
                  />
                  <button
                    type="button"
                    className="password-toggle-btn"
                    onClick={() => setShowRegConfirmPassword(!showRegConfirmPassword)}
                    title={showRegConfirmPassword ? 'Hide password' : 'Show password'}
                  >
                    {showRegConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>
            </div>

            {/* Row 5: Facial Biometric Enrollment */}
            <div className="form-group">
              <label className="form-label">
                <Camera size={14} className="inline mr-1" /> Biometric Face Photo Enrollment *
              </label>

              <div className="camera-box">
                {/* 1. Live Camera Stream */}
                {cameraActive && (
                  <div className="camera-video-wrapper">
                    <video ref={videoRef} className="camera-video" autoPlay playsInline muted />
                    <div className="camera-face-guide" />
                  </div>
                )}

                {/* 2. Photo Preview if captured */}
                {!cameraActive && photoPreviewUrl && (
                  <div style={{ textAlign: 'center' }}>
                    <img
                      src={photoPreviewUrl}
                      alt="Captured biometric preview"
                      className="camera-preview-img"
                    />
                    <div style={{ marginTop: '0.4rem' }}>
                      <span className="captured-badge">
                        <CheckCircle2 size={13} /> Face Photo Ready
                      </span>
                    </div>
                  </div>
                )}

                {/* 3. Camera Actions */}
                <div className="camera-actions">
                  {cameraActive ? (
                    <>
                      <button
                        type="button"
                        className="camera-btn capture"
                        onClick={capturePhoto}
                      >
                        <Camera size={16} />
                        <span>Capture Photo</span>
                      </button>
                      <button
                        type="button"
                        className="camera-btn cancel"
                        onClick={stopCamera}
                      >
                        <X size={16} />
                        <span>Cancel</span>
                      </button>
                    </>
                  ) : photoPreviewUrl ? (
                    <button
                      type="button"
                      className="camera-btn retake"
                      onClick={handleRetakePhoto}
                    >
                      <RefreshCw size={15} />
                      <span>Retake Photo</span>
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="camera-btn capture"
                      onClick={startCamera}
                    >
                      <Camera size={16} />
                      <span>Open Camera & Take Snapshot</span>
                    </button>
                  )}
                </div>

                {/* 4. File fallback */}
                <div style={{ width: '100%', marginTop: '0.25rem' }}>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/jpeg,image/png"
                    onChange={handleFileChange}
                    style={{ display: 'none' }}
                  />
                  <div
                    className="file-fallback-zone"
                    onClick={() => fileInputRef.current?.click()}
                  >
                    <Upload size={14} />
                    <span>Or upload a clear face photo (JPEG/PNG, max 5 MB)</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Row 6: Mandatory Biometric Consent Checkbox */}
            <div className="biometric-consent-card">
              <input
                id="regConsent"
                type="checkbox"
                checked={regConsent}
                onChange={(e) => setRegConsent(e.target.checked)}
                required
              />
              <label htmlFor="regConsent" style={{ cursor: 'pointer' }}>
                <strong style={{ color: 'var(--text-primary)' }}>Facial Biometric Data Consent:</strong> I hereby consent to the collection, cryptographic hashing, and secure processing of my facial biometric features exclusively for automated campus attendance and identity verification in accordance with the campus privacy policy.
              </label>
            </div>

            {/* Submit Button */}
            <button type="submit" className="login-submit-btn" disabled={loading}>
              {loading ? (
                <>
                  <RefreshCw size={18} className="animate-spin" />
                  <span>Enrolling Biometrics & Creating Account...</span>
                </>
              ) : (
                <>
                  <UserPlus size={18} />
                  <span>Register Student Account</span>
                </>
              )}
            </button>

            <div style={{ textAlign: 'center', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
              Already registered?{' '}
              <button
                type="button"
                className="auth-link-btn"
                onClick={() => {
                  setViewMode('LOGIN');
                  setError('');
                }}
              >
                Sign In to existing account
              </button>
            </div>
          </form>
        )}

        {/* ================================================================= */}
        {/* VIEW 3: FORGOT / RESET PASSWORD VIEW */}
        {/* ================================================================= */}
        {viewMode === 'FORGOT' && (
          <div>
            {/* Step 1: Enter Login Code / Email to Request Code */}
            {forgotStep === 1 && (
              <form onSubmit={handleForgotSubmit} className="login-form">
                <div className="form-group">
                  <label className="form-label" htmlFor="forgotCode">
                    <User size={14} className="inline mr-1" /> Institutional Login Code or Email
                  </label>
                  <input
                    id="forgotCode"
                    type="text"
                    className="login-input"
                    placeholder="e.g. student.aarav or aarav@campus.edu"
                    value={forgotLoginCodeOrEmail}
                    onChange={(e) => setForgotLoginCodeOrEmail(e.target.value)}
                    autoFocus
                    required
                  />
                </div>

                <button type="submit" className="login-submit-btn" disabled={loading}>
                  {loading ? (
                    <>
                      <RefreshCw size={18} className="animate-spin" />
                      <span>Generating verification code...</span>
                    </>
                  ) : (
                    <>
                      <KeyRound size={18} />
                      <span>Request 6-Digit Reset Code</span>
                    </>
                  )}
                </button>

                <div style={{ textAlign: 'center', fontSize: '0.82rem', marginTop: '0.75rem' }}>
                  <button
                    type="button"
                    className="auth-link-btn"
                    onClick={() => {
                      setViewMode('LOGIN');
                      setError('');
                    }}
                  >
                    Remember your password? Sign In
                  </button>
                </div>
              </form>
            )}

            {/* Step 2: Enter 6-digit Code & New Password */}
            {forgotStep === 2 && (
              <form onSubmit={handleResetSubmit} className="login-form">
                {/* Demo Code Banner if exposed by backend */}
                {demoResetCode && (
                  <div className="demo-code-banner">
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                      <Sparkles size={14} className="text-accent-cyan" />
                      <strong>Demo Environment One-Time Code:</strong>
                    </div>
                    <div className="demo-code-value">
                      <span>{demoResetCode}</span>
                      <button
                        type="button"
                        className="code-copy-btn"
                        onClick={() => {
                          setResetCode(demoResetCode);
                        }}
                      >
                        Auto-fill Code
                      </button>
                    </div>
                    <span style={{ fontSize: '0.73rem', color: 'var(--text-muted)' }}>
                      * Generated token expires in 15 minutes. Valid for up to 5 attempts.
                    </span>
                  </div>
                )}

                <div className="form-group">
                  <label className="form-label" htmlFor="resetLoginCode">
                    <User size={14} className="inline mr-1" /> Login Code or Email
                  </label>
                  <input
                    id="resetLoginCode"
                    type="text"
                    className="login-input"
                    value={resetLoginCode}
                    onChange={(e) => setResetLoginCode(e.target.value)}
                    required
                  />
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="resetCode">
                    <Hash size={14} className="inline mr-1" /> 6-Digit Verification Code *
                  </label>
                  <input
                    id="resetCode"
                    type="text"
                    maxLength={6}
                    className="login-input"
                    placeholder="e.g. 123456"
                    value={resetCode}
                    onChange={(e) => setResetCode(e.target.value.replace(/[^0-9]/g, ''))}
                    style={{ letterSpacing: '0.2em', fontFamily: 'var(--font-mono)', fontSize: '1.1rem' }}
                    autoFocus
                    required
                  />
                </div>

                <div className="form-grid-2">
                  <div className="form-group">
                    <label className="form-label" htmlFor="resetNewPassword">
                      <Lock size={14} className="inline mr-1" /> New Password *
                    </label>
                    <div className="password-input-wrapper">
                      <input
                        id="resetNewPassword"
                        type={showResetPassword ? 'text' : 'password'}
                        className="login-input"
                        placeholder="Min 8 characters"
                        value={resetNewPassword}
                        onChange={(e) => setResetNewPassword(e.target.value)}
                        required
                      />
                      <button
                        type="button"
                        className="password-toggle-btn"
                        onClick={() => setShowResetPassword(!showResetPassword)}
                        title={showResetPassword ? 'Hide password' : 'Show password'}
                      >
                        {showResetPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="resetConfirmPassword">
                      <Lock size={14} className="inline mr-1" /> Confirm New Password *
                    </label>
                    <div className="password-input-wrapper">
                      <input
                        id="resetConfirmPassword"
                        type={showResetConfirmPassword ? 'text' : 'password'}
                        className="login-input"
                        placeholder="Confirm password"
                        value={resetConfirmPassword}
                        onChange={(e) => setResetConfirmPassword(e.target.value)}
                        required
                      />
                      <button
                        type="button"
                        className="password-toggle-btn"
                        onClick={() => setShowResetConfirmPassword(!showResetConfirmPassword)}
                        title={showResetConfirmPassword ? 'Hide password' : 'Show password'}
                      >
                        {showResetConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>
                </div>

                <button type="submit" className="login-submit-btn" disabled={loading}>
                  {loading ? (
                    <>
                      <RefreshCw size={18} className="animate-spin" />
                      <span>Updating password...</span>
                    </>
                  ) : (
                    <>
                      <KeyRound size={18} />
                      <span>Reset Password</span>
                    </>
                  )}
                </button>

                <div className="auth-link-row" style={{ marginTop: '0.75rem' }}>
                  <button
                    type="button"
                    className="auth-link-btn"
                    onClick={() => {
                      setForgotStep(1);
                      setError('');
                    }}
                  >
                    ← Back to Request Code
                  </button>

                  <button
                    type="button"
                    className="auth-link-btn"
                    onClick={() => {
                      setViewMode('LOGIN');
                      setError('');
                    }}
                  >
                    Back to Sign In
                  </button>
                </div>
              </form>
            )}
          </div>
        )}

      </div>
    </div>
  );
}
