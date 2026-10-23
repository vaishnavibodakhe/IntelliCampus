import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  validateStaffLoginForm,
  validateChangePasswordForm,
  validatePrincipalRegisterForm,
  getPrincipalSetupStatusApi,
  registerPrincipalApi,
  verifyPrincipalOtpApi,
  loginPrincipalApi,
  getStoredStaffSession,
  saveStaffSession,
  clearStaffSession
} from '../staffAuthService.js';

describe('Principal Authentication & First-Run Setup', () => {

  it('validates principal login requires valid email and password', () => {
    const invalidEmpty = validateStaffLoginForm({ email: '', password: '' });
    assert.strictEqual(invalidEmpty.isValid, false);
    assert.strictEqual(invalidEmpty.errors.email, 'Email address is required');
    assert.strictEqual(invalidEmpty.errors.password, 'Password is required');

    const validLogin = validateStaffLoginForm({
      email: 'principal@college.edu',
      password: 'StrongPrincipalPassword123!'
    });
    assert.strictEqual(validLogin.isValid, true);
    assert.deepStrictEqual(validLogin.errors, {});
  });

  it('validates principal registration form fields, matching passwords, and strong complexity', () => {
    // Empty form
    const emptyForm = validatePrincipalRegisterForm({});
    assert.strictEqual(emptyForm.isValid, false);
    assert.strictEqual(emptyForm.errors.name, 'Full name is required');
    assert.strictEqual(emptyForm.errors.email, 'Email address is required');
    assert.strictEqual(emptyForm.errors.password, 'Password is required');

    // Weak password (under 8 chars or missing special char)
    const weakPass = validatePrincipalRegisterForm({
      name: 'Dr. Sharma',
      email: 'sharma@college.edu',
      password: 'weak',
      confirmPassword: 'weak'
    });
    assert.strictEqual(weakPass.isValid, false);
    assert.ok(weakPass.errors.password.includes('at least 8 characters'));

    // Mismatched passwords
    const mismatch = validatePrincipalRegisterForm({
      name: 'Dr. Sharma',
      email: 'sharma@college.edu',
      password: 'StrongPass@2026',
      confirmPassword: 'DifferentPass@2026'
    });
    assert.strictEqual(mismatch.isValid, false);
    assert.strictEqual(mismatch.errors.confirmPassword, 'Passwords do not match');

    // Valid registration
    const valid = validatePrincipalRegisterForm({
      name: 'Dr. A. Sharma',
      email: 'principal@college.edu',
      password: 'StrongPass@2026',
      confirmPassword: 'StrongPass@2026'
    });
    assert.strictEqual(valid.isValid, true);
    assert.deepStrictEqual(valid.errors, {});
  });

  it('queries GET /api/auth/principal/setup-status to control registration visibility', async () => {
    const originalFetch = globalThis.fetch;
    let capturedUrl = '';

    // 1. Fresh database: setup required is TRUE
    globalThis.fetch = async (url) => {
      capturedUrl = url;
      return {
        ok: true,
        status: 200,
        json: async () => ({ setupRequired: true })
      };
    };

    try {
      const resTrue = await getPrincipalSetupStatusApi();
      assert.strictEqual(resTrue.setupRequired, true);
      assert.ok(capturedUrl.includes('/api/auth/principal/setup-status'));

      // 2. Existing Principal: setup required is FALSE (register option must be hidden)
      globalThis.fetch = async (url) => {
        capturedUrl = url;
        return {
          ok: true,
          status: 200,
          json: async () => ({ setupRequired: false })
        };
      };

      const resFalse = await getPrincipalSetupStatusApi();
      assert.strictEqual(resFalse.setupRequired, false);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('handles first-run Principal self-registration API flow', async () => {
    const originalFetch = globalThis.fetch;
    let capturedUrl = '';
    let capturedBody = {};

    globalThis.fetch = async (url, options) => {
      capturedUrl = url;
      capturedBody = JSON.parse(options.body || '{}');
      return {
        ok: true,
        status: 200,
        json: async () => ({
          message: 'Registration initiated. OTP sent.',
          email: capturedBody.email,
          pendingVerification: true
        })
      };
    };

    try {
      const response = await registerPrincipalApi({
        name: 'Dr. A. Sharma',
        email: 'principal@college.edu',
        password: 'StrongPass@2026',
        confirmPassword: 'StrongPass@2026',
        setupToken: 'CAMPUS_SETUP_2026'
      });

      assert.strictEqual(response.pendingVerification, true);
      assert.strictEqual(response.email, 'principal@college.edu');
      assert.strictEqual(capturedBody.setupToken, 'CAMPUS_SETUP_2026');
      assert.ok(capturedUrl.includes('/api/auth/principal/register'));
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('handles Principal OTP verification to activate account and receive JWT', async () => {
    const originalFetch = globalThis.fetch;
    let capturedUrl = '';
    let capturedBody = {};

    globalThis.fetch = async (url, options) => {
      capturedUrl = url;
      capturedBody = JSON.parse(options.body || '{}');
      return {
        ok: true,
        status: 200,
        json: async () => ({
          status: 'SUCCESS',
          accessToken: 'jwt_verified_principal_token',
          refreshToken: 'refresh_verified_principal_token',
          user: {
            id: 1,
            name: 'Dr. A. Sharma',
            email: capturedBody.email,
            role: 'PRINCIPAL',
            userCode: 'PRIN-001'
          }
        })
      };
    };

    try {
      const response = await verifyPrincipalOtpApi({
        email: 'principal@college.edu',
        otp: '123456'
      });

      assert.strictEqual(response.status, 'SUCCESS');
      assert.strictEqual(response.user.role, 'PRINCIPAL');
      assert.strictEqual(capturedBody.otp, '123456');
      assert.ok(capturedUrl.includes('/api/auth/principal/verify-otp'));
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('validates change password form fields and complexity', () => {
    const emptyForm = validateChangePasswordForm({});
    assert.strictEqual(emptyForm.isValid, false);
    assert.strictEqual(emptyForm.errors.currentPassword, 'Current password is required');
    assert.strictEqual(emptyForm.errors.newPassword, 'New password is required');

    const mismatchForm = validateChangePasswordForm({
      currentPassword: 'OldPassword123!',
      newPassword: 'NewPassword123!',
      confirmPassword: 'DifferentPassword123!'
    });
    assert.strictEqual(mismatchForm.isValid, false);
    assert.strictEqual(mismatchForm.errors.confirmPassword, 'Passwords do not match');

    const validChange = validateChangePasswordForm({
      currentPassword: 'OldPassword123!',
      newPassword: 'NewStrongPassword2026@',
      confirmPassword: 'NewStrongPassword2026@'
    });
    assert.strictEqual(validChange.isValid, true);
    assert.deepStrictEqual(validChange.errors, {});
  });

  it('manages principal session persistence and isolation', () => {
    let mockStorage = {};
    globalThis.window = {
      localStorage: {
        setItem: (k, v) => { mockStorage[k] = v; },
        getItem: (k) => mockStorage[k] || null,
        removeItem: (k) => { delete mockStorage[k]; }
      }
    };

    const principalSession = {
      accessToken: 'principal_jwt_token',
      refreshToken: 'principal_refresh_token',
      user: {
        id: 1,
        name: 'College Principal',
        email: 'principal@college.edu',
        role: 'PRINCIPAL'
      }
    };

    saveStaffSession(principalSession);
    const stored = getStoredStaffSession();
    assert.strictEqual(stored.user.role, 'PRINCIPAL');
    assert.strictEqual(stored.user.email, 'principal@college.edu');

    clearStaffSession();
    assert.strictEqual(getStoredStaffSession(), null);
  });
});
