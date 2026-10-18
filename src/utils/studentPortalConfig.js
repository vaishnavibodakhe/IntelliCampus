/**
 * IntelliCampus Portal Mode Configuration
 * Manages the VITE_STUDENT_ONLY configuration flag and role visibility policies.
 */

export const STUDENT_DEMO_CODES = [
  'student.aarav',
  'student.priya',
  'student.rohan',
  'student.kavya'
];

export const ALL_DEMO_CODES = [
  'student.aarav',
  'student.priya',
  'student.rohan',
  'student.kavya',
  'prof.verma',
  'dr.mehta',
  'hod.sharma',
  'admin.patel'
];

/**
 * Returns true if the portal is running in STUDENT_ONLY mode.
 * Defaults to true unless VITE_STUDENT_ONLY is explicitly set to 'false' or false.
 * @param {Object} [customEnv] Optional override for testing
 * @returns {boolean}
 */
export function isStudentOnlyMode(customEnv) {
  if (customEnv && customEnv.VITE_STUDENT_ONLY !== undefined) {
    return String(customEnv.VITE_STUDENT_ONLY) !== 'false';
  }

  // Vite import.meta.env
  if (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_STUDENT_ONLY !== undefined) {
    return String(import.meta.env.VITE_STUDENT_ONLY) !== 'false';
  }

  // Node process.env (fallback for testing)
  if (typeof process !== 'undefined' && process.env && process.env.VITE_STUDENT_ONLY !== undefined) {
    return String(process.env.VITE_STUDENT_ONLY) !== 'false';
  }

  return true; // Default true
}

/**
 * Validates whether a stored user session is permitted under current portal mode.
 * In student-only mode, only users with role === 'STUDENT' are valid.
 * @param {Object|null} user User object
 * @param {boolean} studentOnly Flag
 * @returns {boolean}
 */
export function isSessionPermitted(user, studentOnly = true) {
  if (!user) return false;
  if (studentOnly) {
    return user.role === 'STUDENT';
  }
  return true;
}

/**
 * Returns the list of demo account codes appropriate for current mode.
 * @param {boolean} studentOnly Flag
 * @returns {string[]}
 */
export function getDemoCodes(studentOnly = true) {
  return studentOnly ? STUDENT_DEMO_CODES : ALL_DEMO_CODES;
}

/**
 * Resolves which viewport should be rendered.
 * @param {Object} params
 * @param {boolean} params.studentOnly
 * @param {Object|null} params.loggedInUser
 * @param {string} params.currentRole
 * @returns {string} One of: 'LOGIN', 'STUDENT', 'ADMIN', 'HOD', 'FACULTY', 'MENTOR', 'PUBLIC'
 */
export function resolveActiveViewport({ studentOnly = true, loggedInUser = null, currentRole = 'LOGIN' }) {
  if (studentOnly) {
    if (!loggedInUser || loggedInUser.role !== 'STUDENT') {
      return 'LOGIN';
    }
    return 'STUDENT';
  }

  // Multi-role mode (legacy)
  if (!loggedInUser) {
    return currentRole === 'PUBLIC' ? 'PUBLIC' : 'LOGIN';
  }

  if (currentRole === 'PUBLIC') return 'PUBLIC';
  if (currentRole === 'ADMIN' && loggedInUser.role === 'ADMIN') return 'ADMIN';
  if (currentRole === 'HOD' && loggedInUser.role === 'HOD') return 'HOD';
  if (currentRole === 'FACULTY' && loggedInUser.role === 'FACULTY') return 'FACULTY';
  if (currentRole === 'MENTOR' && loggedInUser.role === 'MENTOR') return 'MENTOR';
  if (currentRole === 'STUDENT' && loggedInUser.role === 'STUDENT') return 'STUDENT';

  return 'LOGIN';
}
