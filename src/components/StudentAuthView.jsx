import React, { useState } from 'react';
import { 
  GraduationCap, 
  Lock, 
  Mail, 
  User, 
  Hash, 
  Building2, 
  Calendar, 
  Eye, 
  EyeOff, 
  ArrowRight, 
  Sparkles, 
  AlertCircle, 
  CheckCircle2, 
  ShieldCheck 
} from 'lucide-react';
import { 
  loginStudentApi, 
  registerStudentApi, 
  saveStudentSession, 
  validateLoginForm, 
  validateRegistrationForm 
} from '../utils/studentAuthService';

export default function StudentAuthView({ onAuthSuccess }) {
  const [authMode, setAuthMode] = useState('login'); // 'login' | 'register'
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState('');
  const [apiSuccess, setApiSuccess] = useState('');

  // Form states
  const [loginForm, setLoginForm] = useState({
    email: '',
    password: ''
  });

  const [registerForm, setRegisterForm] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: '',
    rollNo: '',
    department: 'Computer Science',
    year: 3
  });

  const [fieldErrors, setFieldErrors] = useState({});

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    setApiSuccess('');

    const validation = validateLoginForm(loginForm);
    if (!validation.isValid) {
      setFieldErrors(validation.errors);
      return;
    }
    setFieldErrors({});
    setLoading(true);

    try {
      const response = await loginStudentApi(loginForm);
      saveStudentSession(response);
      setApiSuccess('Login successful! Redirecting to student dashboard...');
      setTimeout(() => {
        onAuthSuccess(response);
      }, 400);
    } catch (err) {
      setApiError(err.message || 'Login failed. Please check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  const handleRegisterSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    setApiSuccess('');

    const validation = validateRegistrationForm(registerForm);
    if (!validation.isValid) {
      setFieldErrors(validation.errors);
      return;
    }
    setFieldErrors({});
    setLoading(true);

    try {
      const response = await registerStudentApi(registerForm);
      saveStudentSession(response);
      setApiSuccess('Registration successful! Welcome to IntelliCampus.');
      setTimeout(() => {
        onAuthSuccess(response);
      }, 500);
    } catch (err) {
      setApiError(err.message || 'Registration failed. Please verify details.');
    } finally {
      setLoading(false);
    }
  };

  const handleQuickFillLogin = () => {
    setLoginForm({
      email: 'aarav.cs24@intellicampus.edu',
      password: 'Password@123'
    });
    setFieldErrors({});
    setApiError('');
  };

  const handleQuickFillRegister = () => {
    const randomId = Math.floor(100 + Math.random() * 900);
    setRegisterForm({
      name: `Rohan Sharma`,
      email: `rohan.${randomId}@intellicampus.edu`,
      password: 'Password@123',
      confirmPassword: 'Password@123',
      rollNo: `2024-CS-${randomId}`,
      department: 'Computer Science',
      year: 3
    });
    setFieldErrors({});
    setApiError('');
  };

  return (
    <div className="student-auth-container" style={{
      minHeight: '75vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '2rem 1rem'
    }}>
      <div className="glass-card animate-fade-in" style={{
        maxWidth: '520px',
        width: '100%',
        padding: '2.5rem',
        borderRadius: '1.25rem',
        boxShadow: '0 20px 40px rgba(0,0,0,0.25)',
        border: '1px solid var(--border-color)'
      }}>
        {/* Brand Header */}
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <div style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '64px',
            height: '64px',
            borderRadius: '1rem',
            background: 'linear-gradient(135deg, #6366f1 0%, #06b6d4 100%)',
            color: '#ffffff',
            marginBottom: '1rem',
            boxShadow: '0 8px 24px rgba(99, 102, 241, 0.35)'
          }}>
            <GraduationCap size={34} />
          </div>
          <h2 style={{ fontSize: '1.75rem', fontWeight: 800, margin: '0 0 0.5rem 0' }}>
            Student Portal
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', margin: 0 }}>
            IntelliCampus Student Authentication & Registration
          </p>
        </div>

        {/* Tab Switcher */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: '0.5rem',
          background: 'var(--bg-tertiary, rgba(255,255,255,0.05))',
          padding: '0.35rem',
          borderRadius: '0.75rem',
          marginBottom: '1.75rem'
        }}>
          <button
            type="button"
            onClick={() => { setAuthMode('login'); setApiError(''); setApiSuccess(''); setFieldErrors({}); }}
            style={{
              padding: '0.65rem',
              borderRadius: '0.5rem',
              border: 'none',
              fontWeight: 600,
              fontSize: '0.9rem',
              cursor: 'pointer',
              transition: 'all 0.2s',
              background: authMode === 'login' ? 'var(--primary)' : 'transparent',
              color: authMode === 'login' ? '#ffffff' : 'var(--text-muted)'
            }}
          >
            Student Login
          </button>
          <button
            type="button"
            onClick={() => { setAuthMode('register'); setApiError(''); setApiSuccess(''); setFieldErrors({}); }}
            style={{
              padding: '0.65rem',
              borderRadius: '0.5rem',
              border: 'none',
              fontWeight: 600,
              fontSize: '0.9rem',
              cursor: 'pointer',
              transition: 'all 0.2s',
              background: authMode === 'register' ? 'var(--primary)' : 'transparent',
              color: authMode === 'register' ? '#ffffff' : 'var(--text-muted)'
            }}
          >
            New Student Register
          </button>
        </div>

        {/* Feedback Messages */}
        {apiError && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.6rem',
            padding: '0.85rem 1rem',
            borderRadius: '0.65rem',
            background: 'rgba(239, 68, 68, 0.12)',
            border: '1px solid rgba(239, 68, 68, 0.35)',
            color: '#ef4444',
            fontSize: '0.875rem',
            marginBottom: '1.25rem'
          }}>
            <AlertCircle size={18} style={{ flexShrink: 0 }} />
            <span>{apiError}</span>
          </div>
        )}

        {apiSuccess && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.6rem',
            padding: '0.85rem 1rem',
            borderRadius: '0.65rem',
            background: 'rgba(16, 185, 129, 0.12)',
            border: '1px solid rgba(16, 185, 129, 0.35)',
            color: '#10b981',
            fontSize: '0.875rem',
            marginBottom: '1.25rem'
          }}>
            <CheckCircle2 size={18} style={{ flexShrink: 0 }} />
            <span>{apiSuccess}</span>
          </div>
        )}

        {/* LOGIN FORM */}
        {authMode === 'login' && (
          <form onSubmit={handleLoginSubmit} className="form-stack">
            <div className="form-group">
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <Mail size={14} /> Student Email
              </label>
              <input 
                type="email"
                placeholder="e.g. aarav.cs24@intellicampus.edu"
                value={loginForm.email}
                onChange={e => setLoginForm({ ...loginForm, email: e.target.value })}
                className={fieldErrors.email ? 'input-error' : ''}
              />
              {fieldErrors.email && <span className="error-text" style={{ color: '#ef4444', fontSize: '0.8rem' }}>{fieldErrors.email}</span>}
            </div>

            <div className="form-group">
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <Lock size={14} /> Password
              </label>
              <div style={{ position: 'relative' }}>
                <input 
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Enter your student password"
                  value={loginForm.password}
                  onChange={e => setLoginForm({ ...loginForm, password: e.target.value })}
                  className={fieldErrors.password ? 'input-error' : ''}
                  style={{ width: '100%', paddingRight: '2.5rem' }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  style={{
                    position: 'absolute',
                    right: '10px',
                    top: '50%',
                    transform: 'translateY(-50%)',
                    background: 'none',
                    border: 'none',
                    color: 'var(--text-muted)',
                    cursor: 'pointer'
                  }}
                  title={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {fieldErrors.password && <span className="error-text" style={{ color: '#ef4444', fontSize: '0.8rem' }}>{fieldErrors.password}</span>}
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: '0.5rem 0 1rem' }}>
              <button
                type="button"
                onClick={handleQuickFillLogin}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--primary)',
                  fontSize: '0.82rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.3rem',
                  padding: 0
                }}
              >
                <Sparkles size={13} /> Quick-Fill Demo (Aarav Shah)
              </button>
            </div>

            <button 
              type="submit" 
              className="btn-primary w-full"
              disabled={loading}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '0.5rem',
                padding: '0.85rem'
              }}
            >
              {loading ? (
                <span>Authenticating with Backend...</span>
              ) : (
                <>
                  <ShieldCheck size={18} />
                  <span>Log In to Student Portal</span>
                  <ArrowRight size={16} />
                </>
              )}
            </button>
          </form>
        )}

        {/* REGISTRATION FORM */}
        {authMode === 'register' && (
          <form onSubmit={handleRegisterSubmit} className="form-stack">
            <div className="form-group">
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <User size={14} /> Full Name
              </label>
              <input 
                type="text"
                placeholder="e.g. Pooja Deshmukh"
                value={registerForm.name}
                onChange={e => setRegisterForm({ ...registerForm, name: e.target.value })}
                className={fieldErrors.name ? 'input-error' : ''}
              />
              {fieldErrors.name && <span className="error-text" style={{ color: '#ef4444', fontSize: '0.8rem' }}>{fieldErrors.name}</span>}
            </div>

            <div className="form-group">
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <Mail size={14} /> College Email Address
              </label>
              <input 
                type="email"
                placeholder="e.g. pooja.cs24@intellicampus.edu"
                value={registerForm.email}
                onChange={e => setRegisterForm({ ...registerForm, email: e.target.value })}
                className={fieldErrors.email ? 'input-error' : ''}
              />
              {fieldErrors.email && <span className="error-text" style={{ color: '#ef4444', fontSize: '0.8rem' }}>{fieldErrors.email}</span>}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.8rem' }}>
              <div className="form-group">
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                  <Hash size={14} /> Roll Number / PRN
                </label>
                <input 
                  type="text"
                  placeholder="e.g. 2024-CS-042"
                  value={registerForm.rollNo}
                  onChange={e => setRegisterForm({ ...registerForm, rollNo: e.target.value })}
                  className={fieldErrors.rollNo ? 'input-error' : ''}
                />
                {fieldErrors.rollNo && <span className="error-text" style={{ color: '#ef4444', fontSize: '0.8rem' }}>{fieldErrors.rollNo}</span>}
              </div>

              <div className="form-group">
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                  <Calendar size={14} /> Academic Year
                </label>
                <select
                  value={registerForm.year}
                  onChange={e => setRegisterForm({ ...registerForm, year: Number(e.target.value) })}
                  className="select-dropdown"
                >
                  <option value={1}>1st Year (FE)</option>
                  <option value={2}>2nd Year (SE)</option>
                  <option value={3}>3rd Year (TE)</option>
                  <option value={4}>4th Year (BE)</option>
                </select>
              </div>
            </div>

            <div className="form-group">
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <Building2 size={14} /> Department
              </label>
              <select
                value={registerForm.department}
                onChange={e => setRegisterForm({ ...registerForm, department: e.target.value })}
                className="select-dropdown"
              >
                <option value="Computer Science">Computer Science</option>
                <option value="Information Technology">Information Technology</option>
                <option value="Mechanical Engineering">Mechanical Engineering</option>
                <option value="Civil Engineering">Civil Engineering</option>
                <option value="Electronics & Telecommunication">Electronics & Telecommunication</option>
              </select>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.8rem' }}>
              <div className="form-group">
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                  <Lock size={14} /> Password
                </label>
                <input 
                  type="password"
                  placeholder="Min 6 characters"
                  value={registerForm.password}
                  onChange={e => setRegisterForm({ ...registerForm, password: e.target.value })}
                  className={fieldErrors.password ? 'input-error' : ''}
                />
                {fieldErrors.password && <span className="error-text" style={{ color: '#ef4444', fontSize: '0.8rem' }}>{fieldErrors.password}</span>}
              </div>

              <div className="form-group">
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                  <Lock size={14} /> Confirm
                </label>
                <input 
                  type="password"
                  placeholder="Re-enter password"
                  value={registerForm.confirmPassword}
                  onChange={e => setRegisterForm({ ...registerForm, confirmPassword: e.target.value })}
                  className={fieldErrors.confirmPassword ? 'input-error' : ''}
                />
                {fieldErrors.confirmPassword && <span className="error-text" style={{ color: '#ef4444', fontSize: '0.8rem' }}>{fieldErrors.confirmPassword}</span>}
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: '0.5rem 0 1rem' }}>
              <button
                type="button"
                onClick={handleQuickFillRegister}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--primary)',
                  fontSize: '0.82rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.3rem',
                  padding: 0
                }}
              >
                <Sparkles size={13} /> Quick-Fill Random Student
              </button>
            </div>

            <button 
              type="submit" 
              className="btn-primary w-full"
              disabled={loading}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '0.5rem',
                padding: '0.85rem'
              }}
            >
              {loading ? (
                <span>Registering in MySQL Database...</span>
              ) : (
                <>
                  <GraduationCap size={18} />
                  <span>Create Student Account & Sign In</span>
                  <ArrowRight size={16} />
                </>
              )}
            </button>
          </form>
        )}

        {/* Footer Switcher */}
        <div style={{ marginTop: '1.75rem', textAlign: 'center', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
          {authMode === 'login' ? (
            <>
              Don't have a registered account yet?{' '}
              <button
                type="button"
                onClick={() => { setAuthMode('register'); setApiError(''); setApiSuccess(''); setFieldErrors({}); }}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--primary)',
                  fontWeight: 600,
                  cursor: 'pointer',
                  padding: 0
                }}
              >
                Register as Student
              </button>
            </>
          ) : (
            <>
              Already have an account?{' '}
              <button
                type="button"
                onClick={() => { setAuthMode('login'); setApiError(''); setApiSuccess(''); setFieldErrors({}); }}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--primary)',
                  fontWeight: 600,
                  cursor: 'pointer',
                  padding: 0
                }}
              >
                Sign In
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
