import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { 
  validateRegistrationForm, 
  validateLoginForm, 
  registerStudentApi, 
  loginStudentApi 
} from '../studentAuthService.js';

describe('Student Authentication - Form Validation Rules', () => {

  it('validates correct registration form payload without errors', () => {
    const validData = {
      name: 'Pooja Deshmukh',
      email: 'pooja.cs@intellicampus.edu',
      password: 'SecurePass#2026',
      confirmPassword: 'SecurePass#2026',
      rollNo: '2024-CS-042',
      department: 'Computer Science',
      year: 3
    };

    const result = validateRegistrationForm(validData);
    assert.strictEqual(result.isValid, true);
    assert.deepStrictEqual(result.errors, {});
  });

  it('rejects registration when name is missing or too short', () => {
    const invalidData = {
      name: ' ',
      email: 'pooja@intellicampus.edu',
      password: 'SecurePass#2026',
      confirmPassword: 'SecurePass#2026',
      rollNo: '2024-CS-042',
      department: 'Computer Science',
      year: 3
    };

    const result = validateRegistrationForm(invalidData);
    assert.strictEqual(result.isValid, false);
    assert.strictEqual(result.errors.name, 'Full name is required');
  });

  it('rejects registration with invalid email format', () => {
    const invalidData = {
      name: 'Pooja Deshmukh',
      email: 'pooja-not-an-email',
      password: 'SecurePass#2026',
      confirmPassword: 'SecurePass#2026',
      rollNo: '2024-CS-042',
      department: 'Computer Science',
      year: 3
    };

    const result = validateRegistrationForm(invalidData);
    assert.strictEqual(result.isValid, false);
    assert.strictEqual(result.errors.email, 'Please enter a valid email address');
  });

  it('rejects registration when password is shorter than 6 characters', () => {
    const invalidData = {
      name: 'Pooja Deshmukh',
      email: 'pooja@intellicampus.edu',
      password: '123',
      confirmPassword: '123',
      rollNo: '2024-CS-042',
      department: 'Computer Science',
      year: 3
    };

    const result = validateRegistrationForm(invalidData);
    assert.strictEqual(result.isValid, false);
    assert.strictEqual(result.errors.password, 'Password must be at least 6 characters long');
  });

  it('rejects registration when passwords do not match', () => {
    const invalidData = {
      name: 'Pooja Deshmukh',
      email: 'pooja@intellicampus.edu',
      password: 'SecurePass#2026',
      confirmPassword: 'DifferentPassword',
      rollNo: '2024-CS-042',
      department: 'Computer Science',
      year: 3
    };

    const result = validateRegistrationForm(invalidData);
    assert.strictEqual(result.isValid, false);
    assert.strictEqual(result.errors.confirmPassword, 'Passwords do not match');
  });

  it('rejects registration when roll number is missing', () => {
    const invalidData = {
      name: 'Pooja Deshmukh',
      email: 'pooja@intellicampus.edu',
      password: 'SecurePass#2026',
      confirmPassword: 'SecurePass#2026',
      rollNo: '',
      department: 'Computer Science',
      year: 3
    };

    const result = validateRegistrationForm(invalidData);
    assert.strictEqual(result.isValid, false);
    assert.strictEqual(result.errors.rollNo, 'Roll number / PRN is required');
  });

  it('validates login form requires email and password', () => {
    const emptyResult = validateLoginForm({ email: '', password: '' });
    assert.strictEqual(emptyResult.isValid, false);
    assert.strictEqual(emptyResult.errors.email, 'Email address is required');
    assert.strictEqual(emptyResult.errors.password, 'Password is required');

    const validResult = validateLoginForm({ email: 'student@intellicampus.edu', password: 'secret' });
    assert.strictEqual(validResult.isValid, true);
  });
});

describe('Student Authentication - API Client Integration', () => {

  it('successfully parses registration API response', async () => {
    const mockResponse = {
      accessToken: 'jwt.token.access',
      refreshToken: 'jwt.token.refresh',
      expiresInSeconds: 900,
      user: {
        id: 10,
        userCode: 'STU-2024-CS-042',
        name: 'Pooja Deshmukh',
        email: 'pooja.cs@intellicampus.edu',
        role: 'STUDENT',
        rollNo: '2024-CS-042',
        department: 'Computer Science',
        year: 3
      }
    };

    globalThis.fetch = async () => ({
      ok: true,
      status: 201,
      json: async () => mockResponse
    });

    const result = await registerStudentApi({
      name: 'Pooja Deshmukh',
      email: 'pooja.cs@intellicampus.edu',
      password: 'SecurePass#2026',
      rollNo: '2024-CS-042',
      department: 'Computer Science',
      year: 3
    });

    assert.strictEqual(result.accessToken, 'jwt.token.access');
    assert.strictEqual(result.user.name, 'Pooja Deshmukh');
    assert.strictEqual(result.user.rollNo, '2024-CS-042');
  });

  it('throws descriptive error on duplicate email registration failure', async () => {
    globalThis.fetch = async () => ({
      ok: false,
      status: 400,
      json: async () => ({
        status: 400,
        error: 'Bad Request',
        message: 'Email is already registered: pooja.cs@intellicampus.edu'
      })
    });

    await assert.rejects(
      async () => {
        await registerStudentApi({
          name: 'Pooja Deshmukh',
          email: 'pooja.cs@intellicampus.edu',
          password: 'SecurePass#2026',
          rollNo: '2024-CS-042',
          department: 'Computer Science',
          year: 3
        });
      },
      /Email is already registered/
    );
  });

  it('successfully parses student login API response', async () => {
    const mockLoginRes = {
      accessToken: 'jwt.student.access',
      refreshToken: 'jwt.student.refresh',
      expiresInSeconds: 900,
      user: {
        id: 5,
        userCode: 'STU-CS-104',
        name: 'Aarav Shah',
        email: 'aarav.cs24@intellicampus.edu',
        role: 'STUDENT',
        rollNo: 'CS-104'
      }
    };

    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      json: async () => mockLoginRes
    });

    const result = await loginStudentApi({
      email: 'aarav.cs24@intellicampus.edu',
      password: 'SecurePass#2026'
    });

    assert.strictEqual(result.accessToken, 'jwt.student.access');
    assert.strictEqual(result.user.rollNo, 'CS-104');
  });

  it('throws descriptive error on incorrect login password', async () => {
    globalThis.fetch = async () => ({
      ok: false,
      status: 401,
      json: async () => ({
        status: 401,
        error: 'Unauthorized',
        message: 'Invalid email or password'
      })
    });

    await assert.rejects(
      async () => {
        await loginStudentApi({
          email: 'aarav.cs24@intellicampus.edu',
          password: 'WrongPassword'
        });
      },
      /Invalid email or password/
    );
  });
});
