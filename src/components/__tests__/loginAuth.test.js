import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { lookupUserByCode, authenticateUser } from '../../utils/auth.js';
import { INITIAL_USERS } from '../../data/mockData.js';

describe('Login & Authentication Lookup Functionality', () => {
  it('lookupUserByCode returns the correct user with case-insensitive code matching', () => {
    const student = lookupUserByCode(INITIAL_USERS, 'student.aarav');
    assert.ok(student, 'Should find user by lowercase code');
    assert.strictEqual(student.userCode, 'student.aarav');
    assert.strictEqual(student.role, 'STUDENT');
    assert.strictEqual(student.name, 'Aarav Shah');

    const upperStudent = lookupUserByCode(INITIAL_USERS, 'STUDENT.AARAV');
    assert.ok(upperStudent, 'Should find user by uppercase code');
    assert.strictEqual(upperStudent.id, student.id);

    const mixedFaculty = lookupUserByCode(INITIAL_USERS, 'Prof.Verma');
    assert.ok(mixedFaculty, 'Should find faculty user with mixed casing');
    assert.strictEqual(mixedFaculty.userCode, 'prof.verma');
    assert.strictEqual(mixedFaculty.role, 'FACULTY');
  });

  it('authenticateUser succeeds for valid code with any non-empty password', () => {
    const result = authenticateUser(INITIAL_USERS, 'student.aarav', 'demoPassword123');
    assert.strictEqual(result.success, true);
    assert.ok(result.user);
    assert.strictEqual(result.user.name, 'Aarav Shah');
    assert.strictEqual(result.user.role, 'STUDENT');
    assert.strictEqual(result.error, null);

    // Another user with different password
    const facultyResult = authenticateUser(INITIAL_USERS, 'prof.verma', 'randomPass!');
    assert.strictEqual(facultyResult.success, true);
    assert.strictEqual(facultyResult.user.role, 'FACULTY');
  });

  it('authenticateUser fails with clear message when login code is unknown', () => {
    const unknownCode = 'nonexistent.user';
    const result = authenticateUser(INITIAL_USERS, unknownCode, 'somePassword');
    assert.strictEqual(result.success, false);
    assert.strictEqual(result.user, null);
    assert.ok(result.error);
    assert.ok(
      result.error.toLowerCase().includes('not found') || result.error.includes(unknownCode),
      `Expected error to state code not found, got: ${result.error}`
    );
  });

  it('authenticateUser fails when password is empty or whitespace', () => {
    const emptyResult = authenticateUser(INITIAL_USERS, 'student.aarav', '');
    assert.strictEqual(emptyResult.success, false);
    assert.strictEqual(emptyResult.user, null);
    assert.ok(
      emptyResult.error.toLowerCase().includes('password'),
      `Expected error to mention password, got: ${emptyResult.error}`
    );

    const whitespaceResult = authenticateUser(INITIAL_USERS, 'student.aarav', '    ');
    assert.strictEqual(whitespaceResult.success, false);
    assert.strictEqual(whitespaceResult.user, null);
    assert.ok(
      whitespaceResult.error.toLowerCase().includes('password'),
      `Expected error to mention password, got: ${whitespaceResult.error}`
    );
  });

  it('authenticateUser fails when login code is empty or missing', () => {
    const missingCodeResult = authenticateUser(INITIAL_USERS, '', 'pass123');
    assert.strictEqual(missingCodeResult.success, false);
    assert.strictEqual(missingCodeResult.user, null);
    assert.ok(missingCodeResult.error.toLowerCase().includes('login code'));

    const nullResult = authenticateUser(INITIAL_USERS, null, 'pass123');
    assert.strictEqual(nullResult.success, false);
    assert.strictEqual(nullResult.user, null);
  });
});
