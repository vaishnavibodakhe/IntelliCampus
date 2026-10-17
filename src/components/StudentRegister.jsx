import React, { useState } from 'react';
import { 
  User, 
  Mail, 
  Phone, 
  KeyRound, 
  Building, 
  Layers, 
  ShieldCheck, 
  AlertCircle, 
  CheckCircle2, 
  ArrowRight,
  Eye,
  EyeOff,
  GraduationCap
} from 'lucide-react';
import { validateStudentRegistration } from '../utils/cryptoAndGeo';
import { api } from '../api/client';

export default function StudentRegister({ onSwitchToLogin, onRegisterSuccess }) {
  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    contactNo: '',
    rollNo: '',
    department: 'Computer Science',
    year: 'Year 3',
    division: 'Div A',
    password: '',
    confirmPassword: '',
    biometricConsent: false,
    consentVersion: '1.0'
  });

  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const departments = [
    'Computer Science',
    'Information Technology',
    'AI & Data Science',
    'Electronics & Telecommunication',
    'Mechanical Engineering',
    'Civil Engineering'
  ];

  const divisions = ['Div A', 'Div B', 'Div C'];
  const years = ['Year 1', 'Year 2', 'Year 3', 'Year 4'];

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
    // Clear specific field error
    if (errors[name]) {
      setErrors(prev => {
        const next = { ...prev };
        delete next[name];
        return next;
      });
    }
    setServerError('');
  };

  // Password strength calculation
  const getPasswordStrength = (pw) => {
    if (!pw) return 0;
    let score = 0;
    if (pw.length >= 8) score += 25;
    if (/[A-Z]/.test(pw)) score += 25;
    if (/[0-9]/.test(pw)) score += 25;
    if (/[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(pw)) score += 25;
    return score;
  };

  const passwordStrength = getPasswordStrength(formData.password);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setServerError('');
    setSuccessMessage('');

    const validation = validateStudentRegistration(formData);
    if (!validation.isValid) {
      setErrors(validation.errors);
      return;
    }

    setIsSubmitting(true);
    try {
      const resp = await api.registerStudent(formData);
      setSuccessMessage(resp.message || 'Registration successful! Your student profile is ready.');
      if (onRegisterSuccess) {
        setTimeout(() => {
          onRegisterSuccess(resp);
        }, 1200);
      }
    } catch (err) {
      setServerError(err.message || 'Registration failed. Please check your details and try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="auth-card-container">
      <div className="auth-card">
        {/* Header */}
        <div className="auth-card-header">
          <div className="auth-badge">
            <GraduationCap size={20} className="text-accent" />
            <span>Student Onboarding Portal</span>
          </div>
          <h2 className="auth-title">Create Student Account</h2>
          <p className="auth-subtitle">
            Enter your academic and personal credentials to register for smart facial attendance.
          </p>
        </div>

        {serverError && (
          <div className="alert-banner alert-danger">
            <AlertCircle size={18} />
            <span>{serverError}</span>
          </div>
        )}

        {successMessage && (
          <div className="alert-banner alert-success">
            <CheckCircle2 size={18} />
            <span>{successMessage}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="auth-form" noValidate>
          {/* Row 1: Full Name & Email */}
          <div className="form-grid-2">
            <div className="form-group">
              <label htmlFor="reg-fullname">Full Name *</label>
              <div className="input-wrap">
                <User size={16} className="input-icon" />
                <input
                  id="reg-fullname"
                  type="text"
                  name="fullName"
                  value={formData.fullName}
                  onChange={handleChange}
                  placeholder="e.g. Aarav Shah"
                  className={errors.fullName ? 'has-error' : ''}
                />
              </div>
              {errors.fullName && <span className="field-error">{errors.fullName}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="reg-email">College / Personal Email *</label>
              <div className="input-wrap">
                <Mail size={16} className="input-icon" />
                <input
                  id="reg-email"
                  type="email"
                  name="email"
                  value={formData.email}
                  onChange={handleChange}
                  placeholder="aarav.cs24@intellicampus.edu"
                  className={errors.email ? 'has-error' : ''}
                />
              </div>
              {errors.email && <span className="field-error">{errors.email}</span>}
            </div>
          </div>

          {/* Row 2: Contact Number & Roll Number */}
          <div className="form-grid-2">
            <div className="form-group">
              <label htmlFor="reg-contact">Contact Number (10-digit Indian Mobile) *</label>
              <div className="input-wrap">
                <Phone size={16} className="input-icon" />
                <input
                  id="reg-contact"
                  type="tel"
                  name="contactNo"
                  value={formData.contactNo}
                  onChange={handleChange}
                  placeholder="e.g. 9876543210"
                  maxLength={10}
                  className={errors.contactNo ? 'has-error' : ''}
                />
              </div>
              {errors.contactNo && <span className="field-error">{errors.contactNo}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="reg-rollno">Roll Number / PRN *</label>
              <div className="input-wrap">
                <GraduationCap size={16} className="input-icon" />
                <input
                  id="reg-rollno"
                  type="text"
                  name="rollNo"
                  value={formData.rollNo}
                  onChange={handleChange}
                  placeholder="e.g. CS-104"
                  className={errors.rollNo ? 'has-error' : ''}
                />
              </div>
              {errors.rollNo && <span className="field-error">{errors.rollNo}</span>}
            </div>
          </div>

          {/* Row 3: Department, Year & Division */}
          <div className="form-grid-3">
            <div className="form-group">
              <label htmlFor="reg-dept">Department *</label>
              <div className="input-wrap">
                <Building size={16} className="input-icon" />
                <select
                  id="reg-dept"
                  name="department"
                  value={formData.department}
                  onChange={handleChange}
                >
                  {departments.map(d => (
                    <option key={d} value={d}>{d}</option>
                  ))}
                </select>
              </div>
              {errors.department && <span className="field-error">{errors.department}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="reg-year">Academic Year *</label>
              <div className="input-wrap">
                <Layers size={16} className="input-icon" />
                <select
                  id="reg-year"
                  name="year"
                  value={formData.year}
                  onChange={handleChange}
                >
                  {years.map(y => (
                    <option key={y} value={y}>{y}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="reg-division">Division *</label>
              <div className="input-wrap">
                <Layers size={16} className="input-icon" />
                <select
                  id="reg-division"
                  name="division"
                  value={formData.division}
                  onChange={handleChange}
                >
                  {divisions.map(div => (
                    <option key={div} value={div}>{div}</option>
                  ))}
                </select>
              </div>
              {errors.division && <span className="field-error">{errors.division}</span>}
            </div>
          </div>

          {/* Row 4: Password & Confirm Password */}
          <div className="form-grid-2">
            <div className="form-group">
              <label htmlFor="reg-password">Password (min 8 chars, 1 uppercase, 1 number, 1 special) *</label>
              <div className="input-wrap">
                <KeyRound size={16} className="input-icon" />
                <input
                  id="reg-password"
                  type={showPassword ? 'text' : 'password'}
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  placeholder="••••••••"
                  className={errors.password ? 'has-error' : ''}
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {/* Strength Meter */}
              {formData.password && (
                <div className="password-strength-container">
                  <div 
                    className="password-strength-bar" 
                    style={{ 
                      width: `${passwordStrength}%`,
                      background: passwordStrength < 50 ? '#ef4444' : passwordStrength < 100 ? '#f59e0b' : '#10b981'
                    }}
                  />
                  <span className="strength-label">
                    {passwordStrength < 50 ? 'Weak' : passwordStrength < 100 ? 'Moderate' : 'Strong'}
                  </span>
                </div>
              )}
              {errors.password && <span className="field-error">{errors.password}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="reg-confirm-password">Confirm Password *</label>
              <div className="input-wrap">
                <KeyRound size={16} className="input-icon" />
                <input
                  id="reg-confirm-password"
                  type={showConfirmPassword ? 'text' : 'password'}
                  name="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  placeholder="••••••••"
                  className={errors.confirmPassword ? 'has-error' : ''}
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                >
                  {showConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {errors.confirmPassword && <span className="field-error">{errors.confirmPassword}</span>}
            </div>
          </div>

          {/* Biometric Consent Checkbox (DPDP Act 2023) */}
          <div className="consent-box">
            <label className="checkbox-label">
              <input
                type="checkbox"
                name="biometricConsent"
                checked={formData.biometricConsent}
                onChange={handleChange}
              />
              <span className="consent-text">
                <ShieldCheck size={18} className="consent-icon text-accent" />
                I hereby provide explicit consent under the <strong>Digital Personal Data Protection (DPDP) Act 2023</strong> for IntelliCampus to process my facial biometric template strictly for campus attendance and verification.
              </span>
            </label>
            {errors.biometricConsent && <span className="field-error">{errors.biometricConsent}</span>}
          </div>

          {/* Submit Button */}
          <button
            type="submit"
            disabled={isSubmitting}
            className="btn-primary auth-submit-btn"
          >
            {isSubmitting ? (
              <span>Creating Student Account...</span>
            ) : (
              <>
                <span>Complete Registration</span>
                <ArrowRight size={18} />
              </>
            )}
          </button>
        </form>

        {/* Footer switch to login */}
        <div className="auth-card-footer">
          <span>Already registered with IntelliCampus?</span>
          <button
            type="button"
            onClick={onSwitchToLogin}
            className="link-btn"
          >
            Sign In Here
          </button>
        </div>
      </div>
    </div>
  );
}
