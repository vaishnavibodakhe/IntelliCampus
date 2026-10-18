/**
 * Authentication & User Lookup Utilities
 * Case-insensitive lookup and demo credential verification for IntelliCampus.
 */

/**
 * Looks up a user in the provided user array by userCode (case-insensitive).
 * @param {Array} users List of user objects
 * @param {string} loginCode Code entered by the user
 * @returns {Object|null} Matching user object or null
 */
export function lookupUserByCode(users, loginCode) {
  if (!loginCode || typeof loginCode !== 'string' || !loginCode.trim()) {
    return null;
  }
  const normalized = loginCode.trim().toLowerCase();
  return (users || []).find(
    u => u && u.userCode && u.userCode.trim().toLowerCase() === normalized
  ) || null;
}

/**
 * Validates login credentials and returns the authenticated user or error.
 * Accepts any non-empty password for demo accounts.
 * @param {Array} users List of user objects
 * @param {string} loginCode Code entered by the user
 * @param {string} password Password entered by the user
 * @returns {{ success: boolean, user: Object|null, error: string|null }} Result object
 */
export function authenticateUser(users, loginCode, password) {
  if (!loginCode || typeof loginCode !== 'string' || !loginCode.trim()) {
    return {
      success: false,
      user: null,
      error: 'Please enter a login code.'
    };
  }

  if (!password || typeof password !== 'string' || !password.trim()) {
    return {
      success: false,
      user: null,
      error: 'Password cannot be empty.'
    };
  }

  const user = lookupUserByCode(users, loginCode);
  if (!user) {
    return {
      success: false,
      user: null,
      error: `Login code "${loginCode.trim()}" not found. Please check your credentials.`
    };
  }

  return {
    success: true,
    user,
    error: null
  };
}
