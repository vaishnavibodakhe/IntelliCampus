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
 * Defaults to false unless VITE_STUDENT_ONLY is explicitly set to 'true' or true.
 * @param {Object} [customEnv] Optional override for testing
 * @returns {boolean}
 */
export function isStudentOnlyMode(customEnv) {
  if (customEnv && customEnv.VITE_STUDENT_ONLY !== undefined) {
    return String(customEnv.VITE_STUDENT_ONLY) === 'true';
  }

  // Vite import.meta.env
  if (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_STUDENT_ONLY !== undefined) {
    return String(import.meta.env.VITE_STUDENT_ONLY) === 'true';
  }

  // Node process.env (fallback for testing)
  if (typeof process !== 'undefined' && process.env && process.env.VITE_STUDENT_ONLY !== undefined) {
    return String(process.env.VITE_STUDENT_ONLY) === 'true';
  }

  return false; // Default false (restores full role navbar and per-role navigation)
}

/**
 * Validates whether a stored user session is permitted under current portal mode.
 * In student-only mode, only users with role === 'STUDENT' are valid.
 * @param {Object|null} user User object
 * @param {boolean} studentOnly Flag
 * @returns {boolean}
 */
export function isSessionPermitted(user, studentOnly = false) {
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
export function getDemoCodes(studentOnly = false) {
  return studentOnly ? STUDENT_DEMO_CODES : ALL_DEMO_CODES;
}

/**
 * Resolves which viewport should be rendered.
 * In multi-role mode, Student role shows LoginView if no student is logged in,
 * while other roles open their views directly (unless a dedicated login exists).
 * In student-only mode, only StudentView (if logged in) or LoginView is reachable.
 * 
 * @param {Object} params
 * @param {boolean} [params.studentOnly=false]
 * @param {Object|null} [params.loggedInUser=null]
 * @param {Object|null} [params.studentUser=null]
 * @param {string} [params.currentRole='FACULTY']
 * @returns {string} One of: 'LOGIN', 'STUDENT', 'ADMIN', 'HOD', 'FACULTY', 'MENTOR', 'PUBLIC'
 */
export function resolveActiveViewport({ 
  studentOnly = false, 
  loggedInUser = null, 
  studentUser = null, 
  currentRole = 'FACULTY' 
}) {
  const activeStudent = studentUser || (loggedInUser?.role === 'STUDENT' ? loggedInUser : null);

  if (studentOnly) {
    if (!activeStudent) {
      return 'LOGIN';
    }
    return 'STUDENT';
  }

  // Multi-role mode:
  if (currentRole === 'STUDENT') {
    return activeStudent ? 'STUDENT' : 'LOGIN';
  }

  if (currentRole === 'PUBLIC') return 'PUBLIC';
  if (currentRole === 'ADMIN') return 'ADMIN';
  if (currentRole === 'HOD') return 'HOD';
  if (currentRole === 'FACULTY') return 'FACULTY';
  if (currentRole === 'MENTOR') return 'MENTOR';

  return 'LOGIN';
}
