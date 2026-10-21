import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { 
  getStoredToken, 
  getStoredUser, 
  setStoredToken, 
  clearAuthSession, 
  api 
} from '../../api/client.js';

describe('Phase 5 Frontend Security - Unified Token & Session Storage', () => {

  // Mock localStorage and sessionStorage
  let localStore = {};
  let sessionStore = {};

  beforeEach(() => {
    localStore = {};
    sessionStore = {};

    globalThis.localStorage = {
      getItem: (k) => localStore[k] || null,
      setItem: (k, v) => { localStore[k] = String(v); },
      removeItem: (k) => { delete localStore[k]; },
      clear: () => { localStore = {}; }
    };

    globalThis.sessionStorage = {
      getItem: (k) => sessionStore[k] || null,
      setItem: (k, v) => { sessionStore[k] = String(v); },
      removeItem: (k) => { delete sessionStore[k]; },
      clear: () => { sessionStore = {}; }
    };
  });

  it('retrieves token stored under intellicampus_auth_token', () => {
    setStoredToken('test.jwt.token', { id: 1, name: 'Admin', role: 'ADMIN' });
    assert.strictEqual(getStoredToken(), 'test.jwt.token');
    const user = getStoredUser();
    assert.strictEqual(user.name, 'Admin');
    assert.strictEqual(user.role, 'ADMIN');
  });

  it('retrieves legacy token from intellicampus_staff_session JSON structure', () => {
    localStore['intellicampus_staff_session'] = JSON.stringify({
      accessToken: 'staff.jwt.legacy',
      user: { id: 2, name: 'Dr. HOD', role: 'HOD' }
    });

    assert.strictEqual(getStoredToken(), 'staff.jwt.legacy');
    const user = getStoredUser();
    assert.strictEqual(user.name, 'Dr. HOD');
    assert.strictEqual(user.role, 'HOD');
  });

  it('clearAuthSession purges all token keys from both localStorage and sessionStorage', () => {
    localStore['intellicampus_auth_token'] = 'token1';
    localStore['intellicampus_token'] = 'token2';
    localStore['intellicampus_token_faculty'] = 'token3';
    localStore['intellicampus_staff_session'] = '{"token":"token4"}';
    sessionStore['intellicampus_auth_token'] = 'token5';
    sessionStore['intellicampus_student_session'] = '{"token":"token6"}';

    clearAuthSession();

    assert.strictEqual(getStoredToken(), null);
    assert.strictEqual(getStoredUser(), null);
    assert.strictEqual(localStore['intellicampus_auth_token'], undefined);
    assert.strictEqual(localStore['intellicampus_token'], undefined);
    assert.strictEqual(localStore['intellicampus_token_faculty'], undefined);
    assert.strictEqual(localStore['intellicampus_staff_session'], undefined);
    assert.strictEqual(sessionStore['intellicampus_auth_token'], undefined);
    assert.strictEqual(sessionStore['intellicampus_student_session'], undefined);
  });
});

describe('Phase 5 Frontend Security - Admin Staff Management API Client', () => {

  it('builds query parameters accurately for staff list filtering', async () => {
    let capturedUrl = '';
    let capturedHeaders = {};

    globalThis.fetch = async (url, options) => {
      capturedUrl = url;
      capturedHeaders = options?.headers || {};
      return {
        ok: true,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => [{ id: 10, name: 'Prof Smith', role: 'FACULTY' }]
      };
    };

    setStoredToken('admin.bearer.token');

    const result = await api.getAdminStaff({
      role: 'FACULTY',
      department: 'Computer Science',
      status: 'ACTIVE',
      search: 'Smith'
    });

    assert.strictEqual(result.length, 1);
    assert.strictEqual(result[0].name, 'Prof Smith');
    assert.ok(capturedUrl.includes('/api/admin/staff?'));
    assert.ok(capturedUrl.includes('role=FACULTY'));
    assert.ok(capturedUrl.includes('department=Computer+Science') || capturedUrl.includes('department=Computer%20Science'));
    assert.ok(capturedUrl.includes('status=ACTIVE'));
    assert.ok(capturedUrl.includes('search=Smith'));
    assert.strictEqual(capturedHeaders['Authorization'], 'Bearer admin.bearer.token');
  });

  it('sends POST request to /api/admin/staff with correct payload when creating staff', async () => {
    let capturedUrl = '';
    let capturedMethod = '';
    let capturedBody = '';

    globalThis.fetch = async (url, options) => {
      capturedUrl = url;
      capturedMethod = options?.method;
      capturedBody = JSON.parse(options?.body || '{}');
      return {
        ok: true,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          id: 42,
          name: capturedBody.name,
          email: capturedBody.email,
          role: capturedBody.role,
          temporaryPassword: 'TempPassword123!'
        })
      };
    };

    const newStaff = {
      name: 'Dr. Grace Hopper',
      email: 'grace@intellicampus.edu',
      employeeCode: 'HOD-CS-01',
      role: 'HOD',
      department: 'Computer Science'
    };

    const result = await api.createAdminStaff(newStaff);
    assert.strictEqual(capturedUrl, '/api/admin/staff');
    assert.strictEqual(capturedMethod, 'POST');
    assert.strictEqual(capturedBody.role, 'HOD');
    assert.strictEqual(result.temporaryPassword, 'TempPassword123!');
  });
});
