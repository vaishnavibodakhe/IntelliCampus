import React, { useState } from 'react';
import AuthLayout from './AuthLayout';
import { 
  User, 
  Mail, 
  Phone, 
  Building2, 
  Sparkles, 
  Lock, 
  Eye, 
  EyeOff, 
  Check, 
  CheckCircle2, 
  AlertCircle, 
  ArrowRight
} from 'lucide-react';
import { 
  isValidEmail, 
  isValidPhone, 
  isPasswordValid, 
  validatePasswordCriteria, 
  calculatePasswordStrength,
  isValidMentorId 
} from './authUtils';
import { useAuth } from '../../context/AuthContext';

export default function RegisterPage({
  role = 'FACULTY',
  apiEndpoint,
  onRegisterSuccess,
  onNavigateLogin
}) {
  const { navigate } = useAuth();
  const isFaculty = role.toUpperCase() === 'FACULTY';
  const resolvedRole = isFaculty ? 'FACULTY' : 'MENTOR';
  const roleClass = isFaculty ? 'faculty' : 'mentor';
  const resolvedEndpoint = apiEndpoint || (isFaculty ? '/api/faculty/register' : '/api/mentor/register');

  // Common Form Fields
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [employeeId, setEmployeeId] = useState('');
  const [department, setDepartment] = useState('Computer Science & Engineering');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // Mentor Specific Field
  const [specialization, setSpecialization] = useState('');

  // UI & status states
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [touched, setTouched] = useState({});
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successToast, setSuccessToast] = useState('');

  const strength = calculatePasswordStrength(password);
  const criteria = validatePasswordCriteria(password);

  // Field Validations
  const errors = {};
  if (touched.fullName && (!fullName.trim() || fullName.trim().length < 3)) {
    errors.fullName = 'Full Name is required (minimum 3 characters)';
  }

  if (touched.email && !isValidEmail(email)) {
    errors.email = 'Please enter a valid official institutional email address';
  }

  if (touched.employeeId) {
    if (!employeeId.trim()) {
      errors.employeeId = isFaculty ? 'Employee ID is required (e.g. FAC-102)' : 'Employee ID is required (e.g. MEN-123)';
    } else if (!isFaculty && !isValidMentorId(employeeId)) {
      errors.employeeId = 'Mentor Employee ID must follow the pattern MEN-xxx (e.g. MEN-123)';
    } else if (isFaculty && employeeId.trim().length < 3) {
      errors.employeeId = 'Faculty ID must be at least 3 characters (e.g. FAC-102)';
    }
  }

  if (touched.department && !department.trim()) {
    errors.department = 'Department is required';
  }

  if (touched.phone && !isValidPhone(phone)) {
    errors.phone = 'Phone number must be exactly 10 digits';
  }

  if (!isFaculty && touched.specialization && (!specialization.trim() || specialization.trim().length < 3)) {
    errors.specialization = 'Area of Expertise/Specialization is required';
  }

  if (touched.password && !isPasswordValid(password)) {
    errors.password = 'Password must be at least 8 characters with uppercase, lowercase, and a number';
  }

  if (touched.confirmPassword && password !== confirmPassword) {
    errors.confirmPassword = 'Passwords do not match';
  }

  const isEmployeeIdValid = isFaculty
    ? employeeId.trim().length >= 3
    : isValidMentorId(employeeId);

  const isFormValid =
    fullName.trim().length >= 3 &&
    isValidEmail(email) &&
    isEmployeeIdValid &&
    department.trim().length > 0 &&
    isValidPhone(phone) &&
    (isFaculty || specialization.trim().length >= 3) &&
    isPasswordValid(password) &&
    password === confirmPassword;

  const handleBlur = (field) => {
    setTouched((prev) => ({ ...prev, [field]: true }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setTouched({
      fullName: true,
      email: true,
      employeeId: true,
      department: true,
      phone: true,
      specialization: true,
      password: true,
      confirmPassword: true
    });

    if (!isFormValid || isLoading) return;

    setIsLoading(true);
    setErrorMessage('');
    setSuccessToast('');

    const payload = {
      name: fullName.trim(),
      email: email.trim().toLowerCase(),
      employeeId: employeeId.trim().toUpperCase(),
      department: department.trim(),
      phone: phone.trim(),
      password,
      confirmPassword,
      ...(isFaculty ? {} : { specialization: specialization.trim() })
    };

    try {
      const response = await fetch(resolvedEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      const data = await response.json().catch(() => ({}));

      if (response.ok && data.success) {
        setSuccessToast(data.message || 'Registration successful! Redirecting to sign in...');
        setTimeout(() => {
          if (onRegisterSuccess) {
            onRegisterSuccess();
          } else {
            navigate(isFaculty ? '/faculty/login' : '/mentor/login');
          }
        }, 1500);
        return;
      }

      const extractedError = data.message ||
        (data.errors && typeof data.errors === 'object' ? Object.values(data.errors).join(', ') : null) ||
        data.error ||
        'Registration failed. Please verify your details.';
      setErrorMessage(extractedError);
    } catch (networkErr) {
      console.error('[Registration Network Error]:', networkErr);
      setErrorMessage('Unable to connect to registration server. Please verify backend status.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthLayout
      role={resolvedRole}
      wide={true}
      title={`Register as ${isFaculty ? 'Faculty' : 'Mentor'}`}
      subtitle={`Create your official ${isFaculty ? 'Faculty instruction' : 'Mentorship guidance'} credentials`}
    >
      {/* Success Toast */}
      {successToast && (
        <div className="auth-alert success" role="alert" style={{ marginBottom: '1.25rem' }}>
          <CheckCircle2 size={18} style={{ flexShrink: 0 }} />
          <span>{successToast}</span>
        </div>
      )}

      {/* Error Alert */}
      {errorMessage && (
        <div className="auth-alert error" role="alert" style={{ marginBottom: '1.25rem' }}>
          <AlertCircle size={18} style={{ flexShrink: 0 }} />
          <span>{errorMessage}</span>
        </div>
      )}

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        {/* Full Name & Official Email */}
        <div className="form-grid-2col">
          <div className="auth-field">
            <label htmlFor="reg-fullname">
              <span>Full Name <span className="auth-label-required">*</span></span>
            </label>
            <div className="auth-input-wrap">
              <span className="auth-input-icon">
                <User size={18} />
              </span>
              <input
                id="reg-fullname"
                type="text"
                autoComplete="name"
                placeholder={isFaculty ? 'e.g. Dr. Vikram Verma' : 'e.g. Dr. Sunita Mehta'}
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                onBlur={() => handleBlur('fullName')}
                className={`auth-input ${roleClass} ${errors.fullName ? 'has-error' : ''}`}
                aria-invalid={!!errors.fullName}
              />
            </div>
            {errors.fullName && (
              <p className="auth-error" role="alert">
                <AlertCircle size={13} />
                <span>{errors.fullName}</span>
              </p>
            )}
          </div>

          <div className="auth-field">
            <label htmlFor="reg-email">
              <span>Official Institutional Email <span className="auth-label-required">*</span></span>
            </label>
            <div className="auth-input-wrap">
              <span className="auth-input-icon">
                <Mail size={18} />
              </span>
              <input
                id="reg-email"
                type="email"
                autoComplete="email"
                placeholder={isFaculty ? 'prof.name@intellicampus.edu' : 'mentor.name@intellicampus.edu'}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                onBlur={() => handleBlur('email')}
                className={`auth-input ${roleClass} ${errors.email ? 'has-error' : ''}`}
                aria-invalid={!!errors.email}
              />
            </div>
            {errors.email && (
              <p className="auth-error" role="alert">
                <AlertCircle size={13} />
                <span>{errors.email}</span>
              </p>
            )}
          </div>
        </div>

        {/* Employee ID & Phone */}
        <div className="form-grid-2col">
          <div className="auth-field">
            <label htmlFor="reg-employeeid">
              <span>Employee ID <span className="auth-label-required">*</span></span>
            </label>
            <div className="auth-input-wrap">
              <span className="auth-input-icon">
                <User size={18} />
              </span>
              <input
                id="reg-employeeid"
                type="text"
                placeholder={isFaculty ? 'e.g. FAC-102' : 'e.g. MEN-123'}
                value={employeeId}
                onChange={(e) => setEmployeeId(e.target.value)}
                onBlur={() => handleBlur('employeeId')}
                className={`auth-input ${roleClass} ${errors.employeeId ? 'has-error' : ''}`}
                aria-invalid={!!errors.employeeId}
              />
            </div>
            <span id="reg-employeeid-hint" style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
              {isFaculty ? 'Expected format: FAC-xxx (e.g. FAC-102)' : 'Expected format: MEN-xxx (e.g. MEN-123)'}
            </span>
            {errors.employeeId && (
              <p className="auth-error" role="alert">
                <AlertCircle size={13} />
                <span>{errors.employeeId}</span>
              </p>
            )}
          </div>

          <div className="auth-field">
            <label htmlFor="reg-phone">
              <span>Mobile Phone Number <span className="auth-label-required">*</span></span>
            </label>
            <div className="auth-input-wrap">
              <span className="auth-input-icon">
                <Phone size={18} />
              </span>
              <input
                id="reg-phone"
                type="tel"
                placeholder="10-digit mobile number"
                maxLength={10}
                value={phone}
                onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))}
                onBlur={() => handleBlur('phone')}
                className={`auth-input ${roleClass} ${errors.phone ? 'has-error' : ''}`}
                aria-invalid={!!errors.phone}
              />
            </div>
            {errors.phone && (
              <p className="auth-error" role="alert">
                <AlertCircle size={13} />
                <span>{errors.phone}</span>
              </p>
            )}
          </div>
        </div>

        {/* Department & (Mentor Specialization) */}
        <div className={!isFaculty ? 'form-grid-2col' : ''}>
          <div className="auth-field">
            <label htmlFor="reg-dept">
              <span>Academic Department <span className="auth-label-required">*</span></span>
            </label>
            <div className="auth-input-wrap">
              <span className="auth-input-icon">
                <Building2 size={18} />
              </span>
              <select
                id="reg-dept"
                value={department}
                onChange={(e) => setDepartment(e.target.value)}
                onBlur={() => handleBlur('department')}
                className={`auth-select ${roleClass}`}
              >
                <option value="Computer Science & Engineering">Computer Science & Engineering</option>
                <option value="Information Technology">Information Technology</option>
                <option value="Electronics & Telecommunication">Electronics & Telecommunication</option>
                <option value="Data Science & Artificial Intelligence">Data Science & Artificial Intelligence</option>
                <option value="Mechanical Engineering">Mechanical Engineering</option>
                <option value="Civil Engineering">Civil Engineering</option>
              </select>
            </div>
          </div>

          {!isFaculty && (
            <div className="auth-field">
              <label htmlFor="reg-specialization">
                <span>Area of Expertise / Specialization <span className="auth-label-required">*</span></span>
              </label>
              <div className="auth-input-wrap">
                <span className="auth-input-icon">
                  <Sparkles size={18} />
                </span>
                <input
                  id="reg-specialization"
                  type="text"
                  placeholder="e.g. AI & Ethics, Career Advisory, System Design"
                  value={specialization}
                  onChange={(e) => setSpecialization(e.target.value)}
                  onBlur={() => handleBlur('specialization')}
                  className={`auth-input ${roleClass} ${errors.specialization ? 'has-error' : ''}`}
                  aria-invalid={!!errors.specialization}
                />
              </div>
              {errors.specialization && (
                <p className="auth-error" role="alert">
                  <AlertCircle size={13} />
                  <span>{errors.specialization}</span>
                </p>
              )}
            </div>
          )}
        </div>

        {/* Passwords in 2 Columns */}
        <div className="form-grid-2col">
          {/* Password */}
          <div className="auth-field">
            <label htmlFor="reg-password">
              <span>Create Password <span className="auth-label-required">*</span></span>
            </label>
            <div className="auth-input-wrap">
              <span className="auth-input-icon">
                <Lock size={18} />
              </span>
              <input
                id="reg-password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="Min. 8 characters"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onBlur={() => handleBlur('password')}
                className={`auth-input ${roleClass} ${errors.password ? 'has-error' : ''}`}
                aria-invalid={!!errors.password}
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
            {errors.password && (
              <p className="auth-error" role="alert">
                <AlertCircle size={13} />
                <span>{errors.password}</span>
              </p>
            )}
          </div>

          {/* Confirm Password */}
          <div className="auth-field">
            <label htmlFor="reg-confirm-password">
              <span>Confirm Password <span className="auth-label-required">*</span></span>
            </label>
            <div className="auth-input-wrap">
              <span className="auth-input-icon">
                <Lock size={18} />
              </span>
              <input
                id="reg-confirm-password"
                type={showConfirmPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="Repeat password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                onBlur={() => handleBlur('confirmPassword')}
                className={`auth-input ${roleClass} ${errors.confirmPassword ? 'has-error' : ''}`}
                aria-invalid={!!errors.confirmPassword}
              />
              <button
                type="button"
                className="auth-eye-btn"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
              >
                {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {errors.confirmPassword && (
              <p className="auth-error" role="alert">
                <AlertCircle size={13} />
                <span>{errors.confirmPassword}</span>
              </p>
            )}
          </div>
        </div>

        {/* Dynamic Password Strength Indicator */}
        {password && (
          <div className="password-strength-box">
            <div className="strength-header">
              <span className="strength-label">Password Strength:</span>
              <span className="strength-value" style={{ color: strength.color }}>{strength.label}</span>
            </div>
            <div className="strength-bar-track">
              <div
                className="strength-bar-fill"
                style={{ width: `${strength.score}%`, backgroundColor: strength.color }}
              />
            </div>
            <div className="strength-criteria-list">
              <div className={`criteria-item ${criteria.length ? 'met' : ''}`}>
                <Check size={12} />
                <span>8+ characters</span>
              </div>
              <div className={`criteria-item ${criteria.hasUpper ? 'met' : ''}`}>
                <Check size={12} />
                <span>Uppercase letter</span>
              </div>
              <div className={`criteria-item ${criteria.hasLower ? 'met' : ''}`}>
                <Check size={12} />
                <span>Lowercase letter</span>
              </div>
              <div className={`criteria-item ${criteria.hasNumber ? 'met' : ''}`}>
                <Check size={12} />
                <span>Numeric digit</span>
              </div>
            </div>
          </div>
        )}

        {/* Submit Button */}
        <button
          type="submit"
          id={`register-submit-btn-${roleClass}`}
          disabled={!isFormValid || isLoading}
          className={`auth-submit-btn ${roleClass}`}
          style={{ marginTop: '0.75rem' }}
        >
          {isLoading ? (
            <>
              <div className="loading-spinner" />
              <span>Registering {isFaculty ? 'Faculty' : 'Mentor'} Account...</span>
            </>
          ) : (
            <>
              <span>Complete {isFaculty ? 'Faculty' : 'Mentor'} Registration</span>
              <ArrowRight size={17} />
            </>
          )}
        </button>
      </form>

      {/* Return to Sign In */}
      <div className="auth-footer" style={{ marginTop: '1.5rem' }}>
        <span>Already have an authorized account? </span>
        <button
          type="button"
          id={`register-login-link-${roleClass}`}
          className="auth-link"
          onClick={() => {
            if (onNavigateLogin) {
              onNavigateLogin();
            } else {
              navigate(isFaculty ? '/faculty/login' : '/mentor/login');
            }
          }}
        >
          Sign in to {isFaculty ? 'Faculty' : 'Mentor'} Portal
        </button>
      </div>
    </AuthLayout>
  );
}
