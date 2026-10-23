// =====================================================================
// IntelliCampus Staff Authentication API Client
// End-to-End React -> Spring Boot -> MySQL integration
// =====================================================================

const API_BASE = (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_URL)
  ? import.meta.env.VITE_API_URL.replace(/\/+$/, '') 
  : (typeof process !== 'undefined' && process.env?.VITE_API_URL)
    ? process.env.VITE_API_URL.replace(/\/+$/, '')
    : '';

export const STAFF_SESSION_KEY = 'intellicampus_staff_session';

/**
 * Validates password policy: 8+ characters with upper, lower, digit and special character
 */
export function isValidStrongPassword(password) {
  if (!password || typeof password !== 'string') return false;
  return password.length >= 8 &&
    /[A-Z]/.test(password) &&
    /[a-z]/.test(password) &&
    /[0-9]/.test(password) &&
    /[^A-Za-z0-9]/.test(password);
}

/**
 * Validates staff registration form
 */
export function validateStaffRegisterForm({ name, email, password, confirmPassword, role, department }) {
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

  if (!role || (role !== 'HOD' && role !== 'PRINCIPAL' && role !== 'FACULTY')) {
    errors.role = 'Please select a valid staff role (HOD or Faculty)';
  }

  if (role === 'HOD' && (!department || !department.trim())) {
    errors.department = 'Department is required for HOD accounts';
  }

  if (!password) {
    errors.password = 'Password is required';
  } else if (!isValidStrongPassword(password)) {
    errors.password = 'Password must be at least 8 characters and include uppercase, lowercase, number, and special character';
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
 * Validates staff login form
 */
export function validateStaffLoginForm({ email, password }) {
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
 * Validates forgot password form
 */
export function validateForgotPasswordForm({ email }) {
  const errors = {};
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  if (!email || !email.trim()) {
    errors.email = 'Email address is required';
  } else if (!emailRegex.test(email.trim())) {
    errors.email = 'Please enter a valid email address';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors
  };
}

/**
 * Validates reset password form
 */
export function validateResetPasswordForm({ token, newPassword, confirmPassword }) {
  const errors = {};

  if (!token || !token.trim()) {
    errors.token = 'Reset token is missing or invalid';
  }

  if (!newPassword) {
    errors.newPassword = 'New password is required';
  } else if (!isValidStrongPassword(newPassword)) {
    errors.newPassword = 'Password must be at least 8 characters and include uppercase, lowercase, number, and special character';
  }

  if (newPassword && confirmPassword !== undefined && newPassword !== confirmPassword) {
    errors.confirmPassword = 'Passwords do not match';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors
  };
}

/**
 * Validates change password form
 */
export function validateChangePasswordForm({ currentPassword, newPassword, confirmPassword }) {
  const errors = {};

  if (!currentPassword) {
    errors.currentPassword = 'Current password is required';
  }

  if (!newPassword) {
    errors.newPassword = 'New password is required';
  } else if (newPassword.length < 6) {
    errors.newPassword = 'New password must be at least 6 characters long';
  }

  if (newPassword && confirmPassword !== undefined && newPassword !== confirmPassword) {
    errors.confirmPassword = 'Passwords do not match';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors
  };
}

/**
 * API Call: Register Staff (Admin / HOD)
 */
export async function registerStaffApi({ name, email, password, role, department, employeeCode }) {
  if (role === 'PRINCIPAL' || role === 'ADMIN') {
    throw new Error('Principal accounts cannot be self-registered. The Principal account is seeded securely.');
  }
  const endpoint = role === 'HOD' ? 'hod' : 'staff';
  const url = `${API_BASE}/api/auth/${endpoint}/register`;

  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
    body: JSON.stringify({
      name: name.trim(),
      email: email.trim().toLowerCase(),
      password,
      role: role || 'HOD',
      department: department ? department.trim() : null,
      employeeCode: employeeCode ? employeeCode.trim() : null
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
 * API Call: Login Staff (Principal / HOD)
 */
export async function loginStaffApi({ email, password, role }) {
  const endpoint = (role === 'PRINCIPAL' || role === 'ADMIN') ? 'principal' : role === 'HOD' ? 'hod' : 'staff';
  const url = `${API_BASE}/api/auth/${endpoint}/login`;

  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
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
 * Dedicated helper to login as Principal
 */
export async function loginPrincipalApi({ email, password }) {
  return loginStaffApi({ email, password, role: 'PRINCIPAL' });
}

/**
 * Validates Principal registration form
 */
export function validatePrincipalRegisterForm({ name, email, password, confirmPassword }) {
  const errors = {};

  if (!name || !name.trim()) {
    errors.name = 'Full name is required';
  } else if (name.trim().length < 2) {
    errors.name = 'Full name must be at least 2 characters';
  }

  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!email || !email.trim()) {
    errors.email = 'Email address is required';
  } else if (!emailRegex.test(email.trim())) {
    errors.email = 'Please enter a valid institutional email address';
  }

  if (!password) {
    errors.password = 'Password is required';
  } else if (!isValidStrongPassword(password)) {
    errors.password = 'Password must be at least 8 characters and include uppercase, lowercase, number, and special character';
  }

  if (!confirmPassword) {
    errors.confirmPassword = 'Confirm password is required';
  } else if (password !== confirmPassword) {
    errors.confirmPassword = 'Passwords do not match';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors
  };
}

/**
 * API Call: Query Principal Setup Status
 * Returns { setupRequired: boolean }
 */
export async function getPrincipalSetupStatusApi() {
  const url = `${API_BASE}/api/auth/principal/setup-status`;
  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: { 'Accept': 'application/json' }
    });
    if (!response.ok) {
      return { setupRequired: false };
    }
    const data = await response.json();
    return { setupRequired: Boolean(data.setupRequired) };
  } catch (err) {
    console.warn('Failed to query Principal setup status:', err);
    return { setupRequired: false };
  }
}

/**
 * API Call: First-Run Principal Self-Registration
 */
export async function registerPrincipalApi({ name, email, password, confirmPassword, setupToken }) {
  const url = `${API_BASE}/api/auth/principal/register`;

  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
    body: JSON.stringify({
      name: name.trim(),
      email: email.trim().toLowerCase(),
      password,
      confirmPassword,
      setupToken: setupToken ? setupToken.trim() : null
    })
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.message || data.error || (data.validationErrors ? Object.values(data.validationErrors)[0] : null) || `Registration failed (${response.status})`;
    const err = new Error(errorMsg);
    err.status = response.status;
    throw err;
  }
  return data;
}

/**
 * API Call: Verify Principal Registration / 2FA OTP
 */
export async function verifyPrincipalOtpApi({ email, otp }) {
  const url = `${API_BASE}/api/auth/principal/verify-otp`;

  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
    body: JSON.stringify({
      email: email.trim().toLowerCase(),
      otp: otp.trim()
    })
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.message || data.error || `OTP verification failed (${response.status})`;
    const err = new Error(errorMsg);
    err.status = response.status;
    throw err;
  }
  return data;
}

/**
 * Validates OTP-based password reset form
 */
export function validateOtpResetPasswordForm({ email, otp, newPassword, confirmPassword }) {
  const errors = {};
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  if (!email || !email.trim()) {
    errors.email = 'Email address is required';
  } else if (!emailRegex.test(email.trim())) {
    errors.email = 'Please enter a valid email address';
  }

  if (!otp || !otp.trim()) {
    errors.otp = '6-digit OTP is required';
  } else if (!/^\d{6}$/.test(otp.trim())) {
    errors.otp = 'OTP must be exactly 6 digits';
  }

  if (!newPassword) {
    errors.newPassword = 'New password is required';
  } else if (!isValidStrongPassword(newPassword)) {
    errors.newPassword = 'Password must be at least 8 characters and include uppercase, lowercase, number, and special character';
  }

  if (newPassword && confirmPassword !== undefined && newPassword !== confirmPassword) {
    errors.confirmPassword = 'Passwords do not match';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors
  };
}

/**
 * API Call: Forgot Password (dispatches 6-digit OTP)
 */
export async function forgotPasswordApi(param) {
  let email = '';
  let role = null;
  if (typeof param === 'object' && param !== null) {
    email = param.email || '';
    role = param.role || null;
  } else {
    email = String(param || '');
  }

  const endpoint = (role === 'PRINCIPAL' || role === 'ADMIN') ? 'principal' : role === 'HOD' ? 'hod' : 'staff';
  const url = `${API_BASE}/api/auth/${endpoint}/forgot-password`;

  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
    body: JSON.stringify({
      email: String(email).trim().toLowerCase(),
      role: role || undefined
    })
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.message || data.error || (data.validationErrors ? Object.values(data.validationErrors)[0] : null) || `Failed to request OTP (${response.status})`;
    throw new Error(errorMsg);
  }
  return data;
}

/**
 * API Call: Reset Password with OTP
 */
export async function resetPasswordApi({ email, otp, token, newPassword, confirmPassword, role }) {
  const endpoint = (role === 'PRINCIPAL' || role === 'ADMIN') ? 'principal' : role === 'HOD' ? 'hod' : 'staff';
  const url = `${API_BASE}/api/auth/${endpoint}/reset-password`;

  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
    body: JSON.stringify({
      email: email ? String(email).trim().toLowerCase() : undefined,
      otp: otp ? String(otp).trim() : (token ? String(token).trim() : undefined),
      token: token ? String(token).trim() : (otp ? String(otp).trim() : undefined),
      newPassword,
      confirmPassword: confirmPassword || newPassword
    })
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.message || data.error || (data.validationErrors ? Object.values(data.validationErrors)[0] : null) || `Failed to reset password (${response.status})`;
    throw new Error(errorMsg);
  }
  return data;
}

/**
 * API Call: Change Password
 */
export async function changePasswordApi({ currentPassword, newPassword }, accessToken) {
  const url = `${API_BASE}/api/auth/staff/change-password`;

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...(accessToken ? { 'Authorization': `Bearer ${accessToken}` } : {})
    },
    body: JSON.stringify({ currentPassword, newPassword })
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.message || data.error || 'Failed to change password';
    throw new Error(errorMsg);
  }
  return data;
}

/**
 * API Call: Pending Approvals (PRINCIPAL only)
 */
export async function getPendingApprovalsApi(accessToken) {
  const url = `${API_BASE}/api/auth/staff/pending`;

  const response = await fetch(url, {
    headers: {
      'Accept': 'application/json',
      ...(accessToken ? { 'Authorization': `Bearer ${accessToken}` } : {})
    }
  });

  const data = await response.json().catch(() => []);
  if (!response.ok) {
    const errorMsg = (data && data.message) || `Failed to fetch approvals (${response.status})`;
    throw new Error(errorMsg);
  }
  return Array.isArray(data) ? data : [];
}

/**
 * API Call: Approve Staff (PRINCIPAL only)
 */
export async function approveStaffApi(staffId, accessToken) {
  const url = `${API_BASE}/api/auth/staff/${staffId}/approve`;

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      ...(accessToken ? { 'Authorization': `Bearer ${accessToken}` } : {})
    }
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || 'Failed to approve staff');
  }
  return data;
}

/**
 * API Call: Reject Staff (PRINCIPAL only)
 */
export async function rejectStaffApi(staffId, reason, accessToken) {
  const url = `${API_BASE}/api/auth/staff/${staffId}/reject`;

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...(accessToken ? { 'Authorization': `Bearer ${accessToken}` } : {})
    },
    body: JSON.stringify({ reason: reason || 'Rejected by Principal' })
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || 'Failed to reject staff');
  }
  return data;
}

/**
 * API Call: Logout Staff
 */
export async function logoutStaffApi(refreshToken, accessToken) {
  const url = `${API_BASE}/api/auth/staff/logout`;

  try {
    await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(accessToken ? { 'Authorization': `Bearer ${accessToken}` } : {})
      },
      body: JSON.stringify({ refreshToken })
    });
  } catch {
    // Ignore network error on logout
  }
}

/**
 * Staff Session Storage Management
 */
export function saveStaffSession(authResponse) {
  if (typeof window !== 'undefined' && window.localStorage) {
    window.localStorage.setItem(STAFF_SESSION_KEY, JSON.stringify(authResponse));
  }
}

export function getStoredStaffSession() {
  if (typeof window === 'undefined' || !window.localStorage) return null;
  try {
    const raw = window.localStorage.getItem(STAFF_SESSION_KEY);
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function clearStaffSession() {
  if (typeof window !== 'undefined' && window.localStorage) {
    window.localStorage.removeItem(STAFF_SESSION_KEY);
  }
}
