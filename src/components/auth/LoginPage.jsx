import React, { useState } from 'react';
import AuthLayout from './AuthLayout';
import { 
  Mail, 
  Lock, 
  Eye, 
  EyeOff, 
  ArrowRight, 
  AlertCircle,
  CheckCircle2
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

export default function LoginPage({
  role = 'FACULTY',
  apiEndpoint,
  onLoginSuccess,
  onNavigateRegister,
  onNavigateForgotPassword
}) {
  const { login, navigate } = useAuth();
  const isFaculty = role.toUpperCase() === 'FACULTY';
  const resolvedRole = isFaculty ? 'FACULTY' : 'MENTOR';
  const roleClass = isFaculty ? 'faculty' : 'mentor';
  const resolvedEndpoint = apiEndpoint || (isFaculty ? '/api/faculty/login' : '/api/mentor/login');

  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  // Interaction & status states
  const [touched, setTouched] = useState({ identifier: false, password: false });
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  // Field validations
  const identifierError = touched.identifier && !identifier.trim()
    ? 'Official Email or Employee ID is required'
    : '';

  const passwordError = touched.password && !password
    ? 'Password is required'
    : '';

  const isFormValid = identifier.trim().length > 0 && password.length > 0;

  const handleBlur = (field) => {
    setTouched((prev) => ({ ...prev, [field]: true }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setTouched({ identifier: true, password: true });

    if (!isFormValid || isLoading) return;

    setIsLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const response = await fetch(resolvedEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          identifier: identifier.trim(),
          password,
          rememberMe
        }),
      });

      const data = await response.json().catch(() => ({}));

      if (response.ok && data.success) {
        setSuccessMessage('Authentication verified. Access granted.');

        // Pass storage preference to auth context
        const authPayload = {
          token: data.data.token,
          role: resolvedRole,
          rememberMe,
          ...data.data.user
        };

        if (login) {
          login(authPayload);
        }

        setTimeout(() => {
          if (onLoginSuccess) {
            onLoginSuccess(data.data);
          } else {
            navigate(isFaculty ? '/faculty/dashboard' : '/mentor/dashboard');
          }
        }, 500);
        return;
      }

      // Handle backend error messages (401, 400, 403, 404, 429)
      const errorMsg = data.message || 'Invalid credentials. Please verify your Email/Employee ID and password.';
      setErrorMessage(errorMsg);
    } catch (networkErr) {
      console.error('[Login Network Error]:', networkErr);
      setErrorMessage('Unable to connect to the authentication service. Please ensure the backend is running.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthLayout
      role={resolvedRole}
      title="Welcome Back"
      subtitle={`Sign in to access your ${isFaculty ? 'academic instruction' : 'mentorship'} dashboard`}
    >
      {/* Alert Banners */}
      {errorMessage && (
        <div className="auth-alert error" role="alert">
          <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '1px' }} />
          <span>{errorMessage}</span>
        </div>
      )}

      {successMessage && (
        <div className="auth-alert success" role="alert">
          <CheckCircle2 size={18} style={{ flexShrink: 0, marginTop: '1px' }} />
          <span>{successMessage}</span>
        </div>
      )}

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        {/* Identifier: Official Email or Employee ID */}
        <div className="auth-field">
          <label htmlFor={`login-identifier-${roleClass}`}>
            <span>Official Email or Employee ID <span className="auth-label-required">*</span></span>
          </label>
          <div className="auth-input-wrap">
            <span className="auth-input-icon">
              <Mail size={18} />
            </span>
            <input
              id={`login-identifier-${roleClass}`}
              name="identifier"
              type="text"
              autoComplete="username"
              placeholder={isFaculty ? 'e.g. prof.name@intellicampus.edu or FAC-102' : 'e.g. mentor.name@intellicampus.edu or FAC-201'}
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              onBlur={() => handleBlur('identifier')}
              className={`auth-input ${roleClass} ${identifierError ? 'has-error' : ''}`}
              aria-invalid={!!identifierError}
              aria-describedby={identifierError ? `identifier-error-${roleClass}` : undefined}
            />
          </div>
          {identifierError && (
            <p id={`identifier-error-${roleClass}`} className="auth-error" role="alert">
              <AlertCircle size={13} />
              <span>{identifierError}</span>
            </p>
          )}
        </div>

        {/* Password */}
        <div className="auth-field">
          <label htmlFor={`login-password-${roleClass}`}>
            <span>Password <span className="auth-label-required">*</span></span>
          </label>
          <div className="auth-input-wrap">
            <span className="auth-input-icon">
              <Lock size={18} />
            </span>
            <input
              id={`login-password-${roleClass}`}
              name="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="Enter your account password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onBlur={() => handleBlur('password')}
              className={`auth-input ${roleClass} ${passwordError ? 'has-error' : ''}`}
              aria-invalid={!!passwordError}
              aria-describedby={passwordError ? `password-error-${roleClass}` : undefined}
            />
            <button
              type="button"
              className="auth-eye-btn"
              onClick={() => setShowPassword(!showPassword)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>
          {passwordError && (
            <p id={`password-error-${roleClass}`} className="auth-error" role="alert">
              <AlertCircle size={13} />
              <span>{passwordError}</span>
            </p>
          )}
        </div>

        {/* Remember Me & Forgot Password Row */}
        <div className="form-options-row">
          <label className="checkbox-label" htmlFor={`login-remember-${roleClass}`}>
            <input
              id={`login-remember-${roleClass}`}
              type="checkbox"
              className="auth-checkbox"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
            />
            <span>Remember me</span>
          </label>

          <button
            type="button"
            id={`login-forgot-password-link-${roleClass}`}
            className="auth-link"
            onClick={() => {
              if (onNavigateForgotPassword) {
                onNavigateForgotPassword();
              } else {
                navigate(isFaculty ? '/faculty/forgot-password' : '/mentor/forgot-password');
              }
            }}
          >
            Forgot password?
          </button>
        </div>

        {/* Submit Button */}
        <button
          type="submit"
          id={`login-submit-btn-${roleClass}`}
          disabled={!isFormValid || isLoading}
          className={`auth-submit-btn ${roleClass}`}
        >
          {isLoading ? (
            <>
              <div className="loading-spinner" />
              <span>Authenticating...</span>
            </>
          ) : (
            <>
              <span>Sign In to {isFaculty ? 'Faculty' : 'Mentor'} Portal</span>
              <ArrowRight size={17} />
            </>
          )}
        </button>
      </form>

      {/* Navigation Footer */}
      <div className="auth-footer">
        <span>Don't have an account? </span>
        <button
          type="button"
          id={`login-register-link-${roleClass}`}
          className="auth-link"
          onClick={() => {
            if (onNavigateRegister) {
              onNavigateRegister();
            } else {
              navigate(isFaculty ? '/faculty/register' : '/mentor/register');
            }
          }}
        >
          Register as {isFaculty ? 'Faculty' : 'Mentor'}
        </button>
      </div>
    </AuthLayout>
  );
}
