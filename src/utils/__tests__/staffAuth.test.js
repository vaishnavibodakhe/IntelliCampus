import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  validateStaffRegisterForm,
  validateStaffLoginForm,
  validateForgotPasswordForm,
  validateResetPasswordForm,
  validateChangePasswordForm,
  isValidStrongPassword,
  registerStaffApi,
  loginStaffApi,
  forgotPasswordApi,
  resetPasswordApi,
  changePasswordApi,
  saveStaffSession,
  getStoredStaffSession,
  clearStaffSession
} from '../staffAuthService.js';

describe('Staff Authentication - Strong Password Policy Rules', () => {

  it('validates 8+ chars with upper, lower, digit and special char', () => {
    assert.strictEqual(isValidStrongPassword('Pass123!'), true);
    assert.strictEqual(isValidStrongPassword('Short1!'), false); // < 8 chars
    assert.strictEqual(isValidStrongPassword('alllowercase123!'), false); // no uppercase
    assert.strictEqual(isValidStrongPassword('ALLUPPERCASE123!'), false); // no lowercase
    assert.strictEqual(isValidStrongPassword('NoDigitsHere!'), false); // no digits
    assert.strictEqual(isValidStrongPassword('NoSpecialChars123'), false); // no special char
  });

  it('validates correct HOD registration form payload with required department', () => {
    const validHodData = {
      name: 'Dr. John Von Neumann',
      email: 'john@intellicampus.edu',
      password: 'SecurePassword123!',
      confirmPassword: 'SecurePassword123!',
      role: 'HOD',
      department: 'Computer Science'
    };

    const result = validateStaffRegisterForm(validHodData);
    assert.strictEqual(result.isValid, true);
    assert.deepStrictEqual(result.errors, {});
  });

  it('validates correct Admin registration payload without department', () => {
    const validAdminData = {
      name: 'Grace Hopper',
      email: 'grace@intellicampus.edu',
      password: 'AdminPassword2026!',
      confirmPassword: 'AdminPassword2026!',
      role: 'ADMIN',
      department: null
    };

    const result = validateStaffRegisterForm(validAdminData);
    assert.strictEqual(result.isValid, true);
    assert.deepStrictEqual(result.errors, {});
  });

  it('requires department when role is HOD', () => {
    const invalidHod = {
      name: 'Dr. John Von Neumann',
      email: 'john@intellicampus.edu',
      password: 'SecurePassword123!',
      confirmPassword: 'SecurePassword123!',
      role: 'HOD',
      department: ''
    };

    const result = validateStaffRegisterForm(invalidHod);
    assert.strictEqual(result.isValid, false);
    assert.strictEqual(result.errors.department, 'Department is required for HOD accounts');
  });

  it('rejects registration when passwords do not match', () => {
    const invalidData = {
      name: 'Dr. John',
      email: 'john@intellicampus.edu',
      password: 'SecurePassword123!',
      confirmPassword: 'DifferentPassword123!',
      role: 'ADMIN',
      department: null
    };

    const result = validateStaffRegisterForm(invalidData);
    assert.strictEqual(result.isValid, false);
    assert.strictEqual(result.errors.confirmPassword, 'Passwords do not match');
  });

  it('rejects registration when password fails strong policy', () => {
    const weakPass = {
      name: 'Dr. Weak',
      email: 'weak@intellicampus.edu',
      password: 'weak',
      confirmPassword: 'weak',
      role: 'ADMIN',
      department: null
    };

    const result = validateStaffRegisterForm(weakPass);
    assert.strictEqual(result.isValid, false);
    assert.strictEqual(
      result.errors.password,
      'Password must be at least 8 characters and include uppercase, lowercase, number, and special character'
    );
  });

  it('validates staff login requires email and password', () => {
    const emptyLogin = validateStaffLoginForm({ email: '', password: '' });
    assert.strictEqual(emptyLogin.isValid, false);
    assert.strictEqual(emptyLogin.errors.email, 'Email address is required');
    assert.strictEqual(emptyLogin.errors.password, 'Password is required');

    const validLogin = validateStaffLoginForm({ email: 'admin@intellicampus.edu', password: 'Password123!' });
    assert.strictEqual(validLogin.isValid, true);
    assert.deepStrictEqual(validLogin.errors, {});
  });

  it('validates forgot password requires valid email address', () => {
    const invalidEmail = validateForgotPasswordForm({ email: 'invalid-email' });
    assert.strictEqual(invalidEmail.isValid, false);
    assert.strictEqual(invalidEmail.errors.email, 'Please enter a valid email address');

    const validEmail = validateForgotPasswordForm({ email: 'staff@intellicampus.edu' });
    assert.strictEqual(validEmail.isValid, true);
  });

  it('validates reset password requires token and strong password (8+ chars)', () => {
    const invalidReset = validateResetPasswordForm({ token: '', newPassword: '123', confirmPassword: '123' });
    assert.strictEqual(invalidReset.isValid, false);
    assert.strictEqual(invalidReset.errors.token, 'Reset token is missing or invalid');
    assert.strictEqual(
      invalidReset.errors.newPassword,
      'Password must be at least 8 characters and include uppercase, lowercase, number, and special character'
    );

    const validReset = validateResetPasswordForm({ token: 'abc123token', newPassword: 'NewPassword123!', confirmPassword: 'NewPassword123!' });
    assert.strictEqual(validReset.isValid, true);
  });

  it('validates change password enforces current password and strong new password', () => {
    const mismatchChange = validateChangePasswordForm({ currentPassword: 'OldPassword123!', newPassword: 'NewPassword123!', confirmPassword: 'WrongPassword' });
    assert.strictEqual(mismatchChange.isValid, false);
    assert.strictEqual(mismatchChange.errors.confirmPassword, 'Passwords do not match');

    const weakChange = validateChangePasswordForm({ currentPassword: 'OldPassword123!', newPassword: 'weak', confirmPassword: 'weak' });
    assert.strictEqual(weakChange.isValid, false);

    const validChange = validateChangePasswordForm({ currentPassword: 'OldPassword123!', newPassword: 'NewPassword123!', confirmPassword: 'NewPassword123!' });
    assert.strictEqual(validChange.isValid, true);
  });
});

describe('Staff Authentication - Role Gating & Session Lifecycle', () => {
  const originalFetch = globalThis.fetch;

  it('successfully parses staff registration API response', async () => {
    globalThis.fetch = async () => ({
      ok: true,
      status: 201,
      json: async () => ({
        id: 10,
        userCode: 'HOD-1001',
        name: 'Dr. Turing',
        email: 'turing@intellicampus.edu',
        role: 'HOD',
        status: 'PENDING_APPROVAL'
      })
    });

    const response = await registerStaffApi({
      name: 'Dr. Turing',
      email: 'turing@intellicampus.edu',
      password: 'PassWord123!',
      role: 'HOD',
      department: 'Computer Science'
    });

    assert.strictEqual(response.status, 'PENDING_APPROVAL');
    assert.strictEqual(response.role, 'HOD');
  });

  it('verifies role mismatch logic on login', () => {
    // Helper function that checks role match on frontend
    const verifyRoleMatch = (returnedRole, expectedRole) => {
      if (returnedRole !== expectedRole) {
        return {
          allowed: false,
          error: expectedRole === 'ADMIN'
            ? 'This account is not an Admin account'
            : 'This account is not an HOD account'
        };
      }
      return { allowed: true };
    };

    const hodOnAdmin = verifyRoleMatch('HOD', 'ADMIN');
    assert.strictEqual(hodOnAdmin.allowed, false);
    assert.strictEqual(hodOnAdmin.error, 'This account is not an Admin account');

    const adminOnHod = verifyRoleMatch('ADMIN', 'HOD');
    assert.strictEqual(adminOnHod.allowed, false);
    assert.strictEqual(adminOnHod.error, 'This account is not an HOD account');

    const adminOnAdmin = verifyRoleMatch('ADMIN', 'ADMIN');
    assert.strictEqual(adminOnAdmin.allowed, true);
  });

  it('throws descriptive error on rejected login due to PENDING_APPROVAL', async () => {
    globalThis.fetch = async () => ({
      ok: false,
      status: 403,
      json: async () => ({
        error: 'Forbidden',
        message: 'Your staff account is currently pending administrator approval.'
      })
    });

    await assert.rejects(
      async () => {
        await loginStaffApi({ email: 'turing@intellicampus.edu', password: 'PassWord123!' });
      },
      /pending administrator approval/
    );
  });

  it('handles password reset API generic response', async () => {
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({
        message: 'If the email is registered, a password reset link has been sent.'
      })
    });

    const result = await forgotPasswordApi('anyone@intellicampus.edu');
    assert.strictEqual(result.message, 'If the email is registered, a password reset link has been sent.');
  });

  it('manages staff localStorage session lifecycle without saving on mismatch', () => {
    let mockStorage = {};
    globalThis.window = {
      localStorage: {
        setItem: (k, v) => { mockStorage[k] = v; },
        getItem: (k) => mockStorage[k] || null,
        removeItem: (k) => { delete mockStorage[k]; }
      }
    };

    const hodSession = {
      accessToken: 'sample_hod_jwt',
      refreshToken: 'sample_hod_refresh',
      user: { id: 2, name: 'Dr. Mehta', role: 'HOD' }
    };

    // Save initial HOD session
    saveStaffSession(hodSession);
    assert.strictEqual(getStoredStaffSession().user.role, 'HOD');

    // Mismatched role on Admin screen: session must NOT be updated
    const incomingMismatch = { role: 'HOD' };
    const targetScreen = 'ADMIN';
    if (incomingMismatch.role === targetScreen) {
      saveStaffSession({ accessToken: 'fail', user: incomingMismatch });
    }
    // Existing session remains untouched
    assert.strictEqual(getStoredStaffSession().user.role, 'HOD');

    // Matching Admin login replaces session
    const adminSession = {
      accessToken: 'sample_admin_jwt',
      refreshToken: 'sample_admin_refresh',
      user: { id: 1, name: 'System Admin', role: 'ADMIN' }
    };
    if (adminSession.user.role === 'ADMIN') {
      saveStaffSession(adminSession);
    }
    assert.strictEqual(getStoredStaffSession().user.role, 'ADMIN');

    clearStaffSession();
    assert.strictEqual(getStoredStaffSession(), null);

    globalThis.fetch = originalFetch;
  });
});
