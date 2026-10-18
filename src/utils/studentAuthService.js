// =====================================================================
// IntelliCampus Student Authentication API Client
// End-to-End React -> Spring Boot -> MySQL integration
// =====================================================================

const API_BASE = (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_URL)
  ? import.meta.env.VITE_API_URL.replace(/\/+$/, '') 
  : (typeof process !== 'undefined' && process.env?.VITE_API_URL)
    ? process.env.VITE_API_URL.replace(/\/+$/, '')
    : '';

export const SESSION_STORAGE_KEY = 'intellicampus_student_session';

/**
 * Validates registration form fields on the client before network submission
 */
export function validateRegistrationForm({ name, email, password, confirmPassword, rollNo, department, year }) {
  const errors = {};

  if (!name || !name.trim()) {
    errors.name = 'Full name is required';
  } else if (name.trim().length < 2) {
    errors.name = 'Name must be at least 2 characters';
  }

  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!email || !email.trim()) {
    errors.email = 'Email address is required';
  } else if (!emailRegex.test(email.trim())) {
    errors.email = 'Please enter a valid email address';
  }

  if (!rollNo || !rollNo.trim()) {
    errors.rollNo = 'Roll number / PRN is required';
  } else if (rollNo.trim().length < 2) {
    errors.rollNo = 'Roll number must be at least 2 characters';
  }

  if (!department || !department.trim()) {
    errors.department = 'Please select your department';
  }

  if (!year || isNaN(Number(year)) || Number(year) < 1 || Number(year) > 4) {
    errors.year = 'Please select a valid academic year (1 - 4)';
  }

  if (!password) {
    errors.password = 'Password is required';
  } else if (password.length < 6) {
    errors.password = 'Password must be at least 6 characters long';
  }

  if (password && confirmPassword !== undefined && password !== confirmPassword) {
    errors.confirmPassword = 'Passwords do not match';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors
  };
}

/**
 * Validates login form fields on the client
 */
export function validateLoginForm({ email, password }) {
  const errors = {};

  if (!email || !email.trim()) {
    errors.email = 'Email address is required';
  }

  if (!password) {
    errors.password = 'Password is required';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors
  };
}

/**
 * Call POST /api/auth/register on Spring Boot backend
 */
export async function registerStudentApi({ name, email, password, rollNo, department, year }) {
  const url = `${API_BASE}/api/auth/register`;
  
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    },
    body: JSON.stringify({
      name: name.trim(),
      email: email.trim().toLowerCase(),
      password,
      rollNo: rollNo.trim().toUpperCase(),
      department: department.trim(),
      year: Number(year)
    })
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const errorMsg = data.message || data.error || (data.validationErrors ? Object.values(data.validationErrors)[0] : null) || `Registration failed (${response.status})`;
    throw new Error(errorMsg);
  }

  return data;
}

/**
 * Call POST /api/auth/login on Spring Boot backend
 */
export async function loginStudentApi({ email, password }) {
  const url = `${API_BASE}/api/auth/login`;

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    },
    body: JSON.stringify({
      email: email.trim().toLowerCase(),
      password
    })
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const errorMsg = data.message || data.error || `Login failed (${response.status})`;
    throw new Error(errorMsg);
  }

  return data;
}

/**
 * Session storage helpers
 */
export function saveStudentSession(authResponse) {
  if (typeof window !== 'undefined' && window.localStorage) {
    window.localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(authResponse));
  }
}

export function getStoredStudentSession() {
  if (typeof window === 'undefined' || !window.localStorage) return null;
  try {
    const raw = window.localStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function clearStudentSession() {
  if (typeof window !== 'undefined' && window.localStorage) {
    window.localStorage.removeItem(SESSION_STORAGE_KEY);
  }
}
