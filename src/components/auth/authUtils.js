/**
 * Authentication and Form Validation Utility Functions
 * Enforces validation rules mirrored on client and server.
 */

// Email regex pattern compliant with standard email formats
export const EMAIL_REGEX = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

// Phone regex pattern: exactly 10 digits
export const PHONE_REGEX = /^\d{10}$/;

// 6-digit OTP regex pattern
export const OTP_REGEX = /^\d{6}$/;

// Mentor Employee ID regex pattern: ^MEN-\d{3,}$
export const MENTOR_ID_REGEX = /^MEN-\d{3,}$/;

/**
 * Validates email format.
 */
export function isValidEmail(email) {
  if (!email || typeof email !== 'string') return false;
  return EMAIL_REGEX.test(email.trim());
}

/**
 * Validates 10-digit phone number.
 */
export function isValidPhone(phone) {
  if (!phone || typeof phone !== 'string') return false;
  return PHONE_REGEX.test(phone.trim());
}

/**
 * Validates required Employee ID / user code.
 */
export function isValidEmployeeId(employeeId) {
  if (!employeeId || typeof employeeId !== 'string') return false;
  return employeeId.trim().length >= 2;
}

/**
 * Validates Mentor Employee ID format (must match ^MEN-\d{3,}$).
 */
export function isValidMentorId(mentorId) {
  if (!mentorId || typeof mentorId !== 'string') return false;
  return MENTOR_ID_REGEX.test(mentorId.trim());
}

/**
 * Validates password complexity:
 * - Minimum 8 characters
 * - At least one uppercase letter
 * - At least one lowercase letter
 * - At least one number
 * - At least one special character
 */
export function validatePasswordCriteria(password) {
  const pwd = password || '';
  return {
    minLength: pwd.length >= 8,
    hasUpper: /[A-Z]/.test(pwd),
    hasLower: /[a-z]/.test(pwd),
    hasNumber: /\d/.test(pwd),
    hasSpecial: /[@$!%*?&#^()_+=\-[\]{};':"\\|,.<>/?]/.test(pwd)
  };
}

/**
 * Calculates password strength level:
 * returns { score (0-4), label ('Weak' | 'Fair' | 'Good' | 'Strong'), color }
 */
export function calculatePasswordStrength(password) {
  if (!password) {
    return { score: 0, label: 'Empty', color: 'var(--text-muted)' };
  }

  const criteria = validatePasswordCriteria(password);
  const metCount = Object.values(criteria).filter(Boolean).length;

  if (metCount <= 2) {
    return { score: 1, label: 'Weak', color: '#ef4444' };
  } else if (metCount === 3) {
    return { score: 2, label: 'Fair', color: '#f59e0b' };
  } else if (metCount === 4) {
    return { score: 3, label: 'Good', color: '#06b6d4' };
  } else {
    return { score: 4, label: 'Strong', color: '#10b981' };
  }
}

/**
 * Checks if password satisfies all minimum security criteria.
 */
export function isPasswordValid(password) {
  const criteria = validatePasswordCriteria(password);
  return (
    criteria.minLength &&
    criteria.hasUpper &&
    criteria.hasLower &&
    criteria.hasNumber &&
    criteria.hasSpecial
  );
}

/**
 * Validates 6-digit OTP reset code.
 */
export function isValidOtp(code) {
  if (!code || typeof code !== 'string') return false;
  return OTP_REGEX.test(code.trim());
}
