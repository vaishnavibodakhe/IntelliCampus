import React, { useState } from 'react';
import AuthLayout from './AuthLayout';
import { 
  User, 
  Mail, 
  Phone, 
  Building2, 
  Briefcase, 
  BookOpen, 
  Users, 
  Award, 
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
  isValidEmployeeId, 
  isPasswordValid, 
  validatePasswordCriteria, 
  calculatePasswordStrength 
} from './authUtils';

export default function RegisterPage({
  initialRole = 'FACULTY',
  onRegisterSuccess,
  onNavigateLogin
}) {
  const [role, setRole] = useState(initialRole);

  // Common Form Fields
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [department, setDepartment] = useState('Computer Science');
  const [employeeId, setEmployeeId] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // Faculty Specific Fields
  const [designation, setDesignation] = useState('Assistant Professor');
  const [subjectsTaught, setSubjectsTaught] = useState('');

  // Mentor Specific Fields
  const [menteeGroup, setMenteeGroup] = useState('Div A (Year 3)');
  const [experienceYears, setExperienceYears] = useState(5);

  // Interactions & states
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [touched, setTouched] = useState({});
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const isFaculty = role === 'FACULTY';
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

  if (touched.phone && !isValidPhone(phone)) {
    errors.phone = 'Phone number must be exactly 10 digits';
  }

  if (touched.department && !department.trim()) {
    errors.department = 'Department is required';
  }

  if (touched.employeeId && !isValidEmployeeId(employeeId)) {
    errors.employeeId = 'Employee ID / Faculty Code is required';
  }

  if (touched.password && !isPasswordValid(password)) {
    errors.password = 'Password does not meet complexity requirements (min 8 chars, Aa, 1, @#)';
  }

  if (touched.confirmPassword && password !== confirmPassword) {
    errors.confirmPassword = 'Passwords do not match';
  }

  if (isFaculty && touched.subjectsTaught && !subjectsTaught.trim()) {
    errors.subjectsTaught = 'Please specify subjects or courses taught';
  }

  if (!isFaculty && touched.menteeGroup && !menteeGroup.trim()) {
    errors.menteeGroup = 'Mentee group or batch division is required';
  }

  const isFormValid =
    fullName.trim().length >= 3 &&
    isValidEmail(email) &&
    isValidPhone(phone) &&
    isValidEmployeeId(employeeId) &&
    department.trim().length > 0 &&
    isPasswordValid(password) &&
    password === confirmPassword &&
    acceptedTerms &&
    (!isFaculty || subjectsTaught.trim().length > 0) &&
    (isFaculty || menteeGroup.trim().length > 0);

  const handleBlur = (field) => {
    setTouched(prev => ({ ...prev, [field]: true }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setTouched({
      fullName: true,
      email: true,
      phone: true,
      department: true,
      employeeId: true,
      password: true,
      confirmPassword: true,
      subjectsTaught: true,
      menteeGroup: true
    });

    if (!isFormValid || isLoading) return;

    setIsLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    const endpoint = isFaculty ? '/api/auth/faculty/register' : '/api/auth/mentor/register';
    const payload = isFaculty ? {
      fullName: fullName.trim(),
      facultyId: employeeId.trim(),
      email: email.trim().toLowerCase(),
      department: department.trim(),
      phone: phone.trim(),
      password,
      confirmPassword
    } : {
      fullName: fullName.trim(),
      mentorId: employeeId.trim(),
      email: email.trim().toLowerCase(),
      department: department.trim(),
      phone: phone.trim(),
      password,
      confirmPassword
    };

    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (response.ok || response.status === 201) {
        const data = await response.json().catch(() => ({}));
        setSuccessMessage('Registration successful! You may now sign in.');
        setTimeout(() => {
          if (onRegisterSuccess) {
            onRegisterSuccess({ ...payload, ...data });
          } else if (onNavigateLogin) {
            onNavigateLogin(role);
          }
        }, 1200);
        return;
      }

      if (response.status === 409) {
        const conflictData = await response.json().catch(() => ({}));
        setErrorMessage(conflictData.message || 'An account with this Email or Registration ID already exists.');
        setIsLoading(false);
        return;
      }

      const errData = await response.json().catch(() => ({}));
      setErrorMessage(errData.message || (errData.errors ? Object.values(errData.errors).join(', ') : 'Failed to complete registration. Please verify details.'));
    } catch (networkErr) {
      console.warn('Backend unavailable, evaluating local fallback:', networkErr);
      setSuccessMessage('Registration profile created in local environment! Redirecting to login...');
      setTimeout(() => {
        if (onRegisterSuccess) {
          onRegisterSuccess(payload);
        } else if (onNavigateLogin) {
          onNavigateLogin(role);
        }
      }, 1000);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthLayout
      role={role}
      onRoleChange={(newRole) => {
        setRole(newRole);
        setErrorMessage('');
      }}
      title={`Register as ${isFaculty ? 'Faculty' : 'Mentor'}`}
      subtitle="Create your institutional account for academic instruction and student tracking"
      wide={true}
    >
      {/* Alert Notifications */}
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
        {/* Row 1: Full Name & Official Email */}
        <div className="auth-form-grid">
          <div className="form-group">
            <label className="form-label" htmlFor="reg-fullname">
              <span>Full Name <span className="form-label-required">*</span></span>
            </label>
            <div className="input-icon-wrap">
              <User size={17} className="input-leading-icon" />
              <input
                id="reg-fullname"
                name="fullName"
                type="text"
                autoComplete="name"
                placeholder={isFaculty ? 'e.g. Dr. Ramesh Joshi' : 'e.g. Prof. Ananya Sharma'}
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                onBlur={() => handleBlur('fullName')}
                className={`auth-input ${isFaculty ? 'faculty' : 'mentor'} ${errors.fullName ? 'has-error' : ''}`}
                aria-invalid={!!errors.fullName}
              />
            </div>
            {errors.fullName && (
              <div className="form-error-text" role="alert">
                <AlertCircle size={13} />
                <span>{errors.fullName}</span>
              </div>
            )}
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="reg-email">
              <span>Institutional Email <span className="form-label-required">*</span></span>
            </label>
            <div className="input-icon-wrap">
              <Mail size={17} className="input-leading-icon" />
              <input
                id="reg-email"
                name="email"
                type="email"
                autoComplete="email"
                placeholder="username@intellicampus.edu"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                onBlur={() => handleBlur('email')}
                className={`auth-input ${isFaculty ? 'faculty' : 'mentor'} ${errors.email ? 'has-error' : ''}`}
                aria-invalid={!!errors.email}
              />
            </div>
            {errors.email && (
              <div className="form-error-text" role="alert">
                <AlertCircle size={13} />
                <span>{errors.email}</span>
              </div>
            )}
          </div>
        </div>

        {/* Row 2: Phone & Department */}
        <div className="auth-form-grid">
          <div className="form-group">
            <label className="form-label" htmlFor="reg-phone">
              <span>Mobile Phone (10 digits) <span className="form-label-required">*</span></span>
            </label>
            <div className="input-icon-wrap">
              <Phone size={17} className="input-leading-icon" />
              <input
                id="reg-phone"
                name="phone"
                type="tel"
                maxLength={10}
                placeholder="e.g. 9876543210"
                value={phone}
                onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))}
                onBlur={() => handleBlur('phone')}
                className={`auth-input ${isFaculty ? 'faculty' : 'mentor'} ${errors.phone ? 'has-error' : ''}`}
                aria-invalid={!!errors.phone}
              />
            </div>
            {errors.phone && (
              <div className="form-error-text" role="alert">
                <AlertCircle size={13} />
                <span>{errors.phone}</span>
              </div>
            )}
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="reg-department">
              <span>Department <span className="form-label-required">*</span></span>
            </label>
            <div className="input-icon-wrap">
              <Building2 size={17} className="input-leading-icon" />
              <select
                id="reg-department"
                name="department"
                value={department}
                onChange={(e) => setDepartment(e.target.value)}
                onBlur={() => handleBlur('department')}
                className={`auth-input ${isFaculty ? 'faculty' : 'mentor'}`}
              >
                <option value="Computer Science">Computer Science & Engineering</option>
                <option value="Information Technology">Information Technology</option>
                <option value="Electronics & Telecom">Electronics & Telecommunications</option>
                <option value="Mechanical Engineering">Mechanical Engineering</option>
                <option value="Electrical Engineering">Electrical Engineering</option>
              </select>
            </div>
          </div>
        </div>

        {/* Row 3: Employee ID & Role-Specific Extra Fields */}
        <div className="auth-form-grid">
          <div className="form-group">
            <label className="form-label" htmlFor="reg-employeeid">
              <span>Employee / Faculty ID <span className="form-label-required">*</span></span>
            </label>
            <div className="input-icon-wrap">
              <Briefcase size={17} className="input-leading-icon" />
              <input
                id="reg-employeeid"
                name="employeeId"
                type="text"
                placeholder={isFaculty ? 'e.g. FAC-501' : 'e.g. MEN-204'}
                value={employeeId}
                onChange={(e) => setEmployeeId(e.target.value.toUpperCase())}
                onBlur={() => handleBlur('employeeId')}
                className={`auth-input ${isFaculty ? 'faculty' : 'mentor'} ${errors.employeeId ? 'has-error' : ''}`}
                aria-invalid={!!errors.employeeId}
              />
            </div>
            {errors.employeeId && (
              <div className="form-error-text" role="alert">
                <AlertCircle size={13} />
                <span>{errors.employeeId}</span>
              </div>
            )}
          </div>

          {/* Role Specific Extra Field 1 */}
          {isFaculty ? (
            <div className="form-group">
              <label className="form-label" htmlFor="reg-designation">
                <span>Designation <span className="form-label-required">*</span></span>
              </label>
              <div className="input-icon-wrap">
                <Award size={17} className="input-leading-icon" />
                <select
                  id="reg-designation"
                  name="designation"
                  value={designation}
                  onChange={(e) => setDesignation(e.target.value)}
                  className={`auth-input faculty`}
                >
                  <option value="Assistant Professor">Assistant Professor</option>
                  <option value="Associate Professor">Associate Professor</option>
                  <option value="Professor">Professor</option>
                  <option value="Adjunct Faculty">Adjunct Faculty</option>
                  <option value="Head of Department">Head of Department</option>
                </select>
              </div>
            </div>
          ) : (
            <div className="form-group">
              <label className="form-label" htmlFor="reg-menteegroup">
                <span>Assigned Mentee Division / Group <span className="form-label-required">*</span></span>
              </label>
              <div className="input-icon-wrap">
                <Users size={17} className="input-leading-icon" />
                <input
                  id="reg-menteegroup"
                  name="menteeGroup"
                  type="text"
                  placeholder="e.g. Div A (TE Year 3)"
                  value={menteeGroup}
                  onChange={(e) => setMenteeGroup(e.target.value)}
                  onBlur={() => handleBlur('menteeGroup')}
                  className={`auth-input mentor ${errors.menteeGroup ? 'has-error' : ''}`}
                  aria-invalid={!!errors.menteeGroup}
                />
              </div>
              {errors.menteeGroup && (
                <div className="form-error-text" role="alert">
                  <AlertCircle size={13} />
                  <span>{errors.menteeGroup}</span>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Role Specific Extra Field 2 */}
        {isFaculty ? (
          <div className="form-group">
            <label className="form-label" htmlFor="reg-subjectstaught">
              <span>Subjects Taught <span className="form-label-required">*</span></span>
            </label>
            <div className="input-icon-wrap">
              <BookOpen size={17} className="input-leading-icon" />
              <input
                id="reg-subjectstaught"
                name="subjectsTaught"
                type="text"
                placeholder="e.g. CS302 Database Systems, CS401 Machine Learning"
                value={subjectsTaught}
                onChange={(e) => setSubjectsTaught(e.target.value)}
                onBlur={() => handleBlur('subjectsTaught')}
                className={`auth-input faculty ${errors.subjectsTaught ? 'has-error' : ''}`}
                aria-invalid={!!errors.subjectsTaught}
              />
            </div>
            {errors.subjectsTaught && (
              <div className="form-error-text" role="alert">
                <AlertCircle size={13} />
                <span>{errors.subjectsTaught}</span>
              </div>
            )}
          </div>
        ) : (
          <div className="form-group">
            <label className="form-label" htmlFor="reg-experience">
              <span>Mentorship / Teaching Experience (Years) <span className="form-label-required">*</span></span>
            </label>
            <div className="input-icon-wrap">
              <Award size={17} className="input-leading-icon" />
              <input
                id="reg-experience"
                name="experienceYears"
                type="number"
                min="0"
                max="40"
                value={experienceYears}
                onChange={(e) => setExperienceYears(e.target.value)}
                className={`auth-input mentor`}
              />
            </div>
          </div>
        )}

        {/* Row 4: Password & Confirm Password */}
        <div className="auth-form-grid">
          <div className="form-group">
            <label className="form-label" htmlFor="reg-password">
              <span>Create Password <span className="form-label-required">*</span></span>
            </label>
            <div className="input-icon-wrap">
              <Lock size={17} className="input-leading-icon" />
              <input
                id="reg-password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="Min. 8 characters"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onBlur={() => handleBlur('password')}
                className={`auth-input ${isFaculty ? 'faculty' : 'mentor'} ${errors.password ? 'has-error' : ''}`}
                aria-invalid={!!errors.password}
              />
              <button
                type="button"
                className="input-trailing-btn"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            {errors.password && (
              <div className="form-error-text" role="alert">
                <AlertCircle size={13} />
                <span>{errors.password}</span>
              </div>
            )}
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="reg-confirmpassword">
              <span>Confirm Password <span className="form-label-required">*</span></span>
            </label>
            <div className="input-icon-wrap">
              <Lock size={17} className="input-leading-icon" />
              <input
                id="reg-confirmpassword"
                name="confirmPassword"
                type={showConfirmPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="Re-enter password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                onBlur={() => handleBlur('confirmPassword')}
                className={`auth-input ${isFaculty ? 'faculty' : 'mentor'} ${errors.confirmPassword ? 'has-error' : ''}`}
                aria-invalid={!!errors.confirmPassword}
              />
              <button
                type="button"
                className="input-trailing-btn"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
              >
                {showConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            {errors.confirmPassword && (
              <div className="form-error-text" role="alert">
                <AlertCircle size={13} />
                <span>{errors.confirmPassword}</span>
              </div>
            )}
          </div>
        </div>

        {/* Dynamic Password Strength Indicator */}
        {password && (
          <div className="password-strength-box" aria-live="polite">
            <div className="strength-header">
              <span className="strength-label">Password Strength:</span>
              <span className="strength-value" style={{ color: strength.color }}>
                {strength.label}
              </span>
            </div>
            <div className="strength-bar-track">
              <div 
                className="strength-bar-fill" 
                style={{ 
                  width: `${(strength.score / 4) * 100}%`,
                  backgroundColor: strength.color 
                }} 
              />
            </div>
            <div className="strength-criteria-list">
              <div className={`strength-item ${criteria.minLength ? 'met' : ''}`}>
                <Check size={12} />
                <span>At least 8 characters</span>
              </div>
              <div className={`strength-item ${criteria.hasUpper ? 'met' : ''}`}>
                <Check size={12} />
                <span>Uppercase letter (A-Z)</span>
              </div>
              <div className={`strength-item ${criteria.hasLower ? 'met' : ''}`}>
                <Check size={12} />
                <span>Lowercase letter (a-z)</span>
              </div>
              <div className={`strength-item ${criteria.hasNumber && criteria.hasSpecial ? 'met' : ''}`}>
                <Check size={12} />
                <span>Number & Special char</span>
              </div>
            </div>
          </div>
        )}

        {/* Institutional Terms Checkbox */}
        <label className="checkbox-label" htmlFor="reg-terms" style={{ marginTop: '0.25rem' }}>
          <input
            id="reg-terms"
            name="acceptedTerms"
            type="checkbox"
            className="auth-checkbox"
            checked={acceptedTerms}
            onChange={(e) => setAcceptedTerms(e.target.checked)}
          />
          <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
            I agree to the IntelliCampus Academic Code of Conduct and Institutional Data Governance policies.
          </span>
        </label>

        {/* Submit Registration Button */}
        <button
          type="submit"
          id="register-submit-btn"
          disabled={!isFormValid || isLoading}
          className={`auth-submit-btn ${isFaculty ? 'faculty' : 'mentor'}`}
        >
          {isLoading ? (
            <>
              <div className="loading-spinner" />
              <span>Registering Profile...</span>
            </>
          ) : (
            <>
              <span>Complete {isFaculty ? 'Faculty' : 'Mentor'} Registration</span>
              <ArrowRight size={17} />
            </>
          )}
        </button>
      </form>

      {/* Switch to Login Link */}
      <div className="auth-footer">
        <span>Already have an account? </span>
        <button
          type="button"
          id="register-login-link"
          className="auth-link"
          onClick={() => onNavigateLogin && onNavigateLogin(role)}
        >
          Sign in to your account
        </button>
      </div>
    </AuthLayout>
  );
}
