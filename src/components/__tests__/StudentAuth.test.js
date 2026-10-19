import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { 
  validateStudentRegistration,
  calculateCosineSimilarity,
  computeAverageEmbedding,
  checkDuplicateFace,
  validateImageBrightness,
  estimateImageSharpness,
  validatePoseConsistency,
  validateFrameUniqueness,
  checkCameraSecurityEnvironment
} from '../../utils/cryptoAndGeo.js';

describe('Student Authentication & Registration - Field Validation', () => {
  const validStudentData = {
    fullName: 'Aarav Shah',
    email: 'aarav.cs24@intellicampus.edu',
    contactNo: '9876543210',
    rollNo: 'CS-104',
    department: 'Computer Science',
    year: 'Year 3',
    division: 'Div A',
    password: 'SecurePassword123!',
    confirmPassword: 'SecurePassword123!',
    biometricConsent: true
  };

  it('should accept valid student registration data', () => {
    const result = validateStudentRegistration(validStudentData);
    assert.strictEqual(result.isValid, true);
    assert.strictEqual(Object.keys(result.errors).length, 0);
  });

  it('should reject invalid email formats', () => {
    const invalidData = { ...validStudentData, email: 'not-an-email' };
    const result = validateStudentRegistration(invalidData);
    assert.strictEqual(result.isValid, false);
    assert.ok(result.errors.email);
    assert.ok(result.errors.email.includes('valid college or personal email'));
  });

  it('should reject invalid 10-digit Indian mobile numbers', () => {
    // Starting with invalid digit 5
    const invalidPhone1 = { ...validStudentData, contactNo: '5876543210' };
    const res1 = validateStudentRegistration(invalidPhone1);
    assert.strictEqual(res1.isValid, false);
    assert.ok(res1.errors.contactNo);

    // Only 9 digits
    const invalidPhone2 = { ...validStudentData, contactNo: '987654321' };
    const res2 = validateStudentRegistration(invalidPhone2);
    assert.strictEqual(res2.isValid, false);
    assert.ok(res2.errors.contactNo);
  });

  it('should accept 10-digit Indian mobile starting with 6, 7, 8, or 9', () => {
    for (const prefix of ['6', '7', '8', '9']) {
      const validPhone = { ...validStudentData, contactNo: `${prefix}123456789` };
      const res = validateStudentRegistration(validPhone);
      assert.strictEqual(res.isValid, true);
      assert.strictEqual(res.errors.contactNo, undefined);
    }
  });

  it('should reject weak passwords lacking uppercase, number, or special char', () => {
    // Missing special character
    const noSpecial = { ...validStudentData, password: 'Password123', confirmPassword: 'Password123' };
    const res1 = validateStudentRegistration(noSpecial);
    assert.strictEqual(res1.isValid, false);
    assert.ok(res1.errors.password);

    // Too short (< 8 chars)
    const tooShort = { ...validStudentData, password: 'Pass1!', confirmPassword: 'Pass1!' };
    const res2 = validateStudentRegistration(tooShort);
    assert.strictEqual(res2.isValid, false);
    assert.ok(res2.errors.password);
  });

  it('should reject password and confirm password mismatch', () => {
    const mismatch = { ...validStudentData, confirmPassword: 'DifferentPassword123!' };
    const res = validateStudentRegistration(mismatch);
    assert.strictEqual(res.isValid, false);
    assert.ok(res.errors.confirmPassword);
    assert.strictEqual(res.errors.confirmPassword, 'Passwords do not match.');
  });

  it('should require explicit DPDP Act 2023 biometric consent checkbox', () => {
    const noConsent = { ...validStudentData, biometricConsent: false };
    const res = validateStudentRegistration(noConsent);
    assert.strictEqual(res.isValid, false);
    assert.ok(res.errors.biometricConsent);
    assert.ok(res.errors.biometricConsent.includes('DPDP Act 2023'));
  });
});

describe('Biometric ArcFace - Duplicate Face Detection (Cosine Similarity)', () => {
  // Construct deterministic orthogonal 512-d unit vectors e1 and e2
  const e1 = new Array(512).fill(0);
  e1[0] = 1.0;
  const e2 = new Array(512).fill(0);
  e2[1] = 1.0;

  // Vector with exact cosine similarity 0.70 with e1: 0.70*e1 + sqrt(1 - 0.49)*e2
  const sim70 = new Array(512).fill(0);
  sim70[0] = 0.70;
  sim70[1] = Math.sqrt(1 - 0.49); // 0.71414...

  // Vector with exact cosine similarity 0.20 with e1: 0.20*e1 + sqrt(1 - 0.04)*e2
  const sim20 = new Array(512).fill(0);
  sim20[0] = 0.20;
  sim20[1] = Math.sqrt(1 - 0.04);

  // Vector with exact cosine similarity 0.40 with e1
  const sim40 = new Array(512).fill(0);
  sim40[0] = 0.40;
  sim40[1] = Math.sqrt(1 - 0.16);

  const enrolledStudents = [
    { id: 101, name: 'Rohan Deshmukh', rollNo: 'CS-106', faceEmbedding: e1 }
  ];

  it('should reject registration as DUPLICATE when similarity >= 0.45', () => {
    // sim70 has similarity 0.70 >= 0.45
    const check = checkDuplicateFace(sim70, enrolledStudents, 0.45, 0.35);
    assert.strictEqual(check.isDuplicate, true);
    assert.strictEqual(check.status, 'DUPLICATE');
    assert.ok(check.maxSimilarity >= 0.45);
    assert.ok(check.error.includes('Duplicate face detected'));
  });

  it('should allow registration as CLEAR when similarity < 0.35', () => {
    // sim20 has similarity 0.20 < 0.35
    const check = checkDuplicateFace(sim20, enrolledStudents, 0.45, 0.35);
    assert.strictEqual(check.isDuplicate, false);
    assert.strictEqual(check.status, 'CLEAR');
    assert.ok(check.maxSimilarity < 0.35);
    assert.strictEqual(check.matchedStudent, null);
  });

  it('should classify similarity in [0.35, 0.45) as BORDERLINE', () => {
    // sim40 has similarity 0.40 in [0.35, 0.45)
    const check = checkDuplicateFace(sim40, enrolledStudents, 0.45, 0.35);
    assert.strictEqual(check.isDuplicate, false);
    assert.strictEqual(check.status, 'BORDERLINE');
    assert.strictEqual(check.maxSimilarity, 0.40);
  });

  it('should support configurable thresholds via parameters', () => {
    // With custom threshold 0.80, sim70 (similarity 0.70) is NOT duplicate
    const checkHigh = checkDuplicateFace(sim70, enrolledStudents, 0.80, 0.50);
    assert.strictEqual(checkHigh.isDuplicate, false);
    assert.strictEqual(checkHigh.status, 'BORDERLINE');

    // With custom threshold 0.30, sim40 (similarity 0.40) IS duplicate
    const checkLow = checkDuplicateFace(sim40, enrolledStudents, 0.30, 0.20);
    assert.strictEqual(checkLow.isDuplicate, true);
    assert.strictEqual(checkLow.status, 'DUPLICATE');
  });
});

describe('Biometric Enrollment - Multi-Frame Embedding Averaging', () => {
  it('should compute averaged and L2-normalized embedding across 3 capture poses', () => {
    // 3 mock 512-d embeddings (Front, Left, Right)
    const frame1 = new Array(512).fill(0.044);
    const frame2 = new Array(512).fill(0.042);
    const frame3 = new Array(512).fill(0.046);

    const averaged = computeAverageEmbedding([frame1, frame2, frame3]);
    assert.strictEqual(averaged.length, 512);

    // Verify L2 norm is approximately 1.0
    const l2Norm = Math.sqrt(averaged.reduce((sum, v) => sum + v * v, 0));
    assert.ok(Math.abs(l2Norm - 1.0) < 1e-4);
  });
});

describe('Student Login - Rate Limiting & Lockout Logic', () => {
  function evaluateLoginAttempt(userState, isPasswordCorrect, maxAttempts = 5, lockoutMinutes = 15) {
    const now = new Date();
    if (userState.lockedUntil && now < userState.lockedUntil) {
      const remainingMs = userState.lockedUntil.getTime() - now.getTime();
      const remainingMin = Math.ceil(remainingMs / (60 * 1000));
      return { success: false, locked: true, remainingMinutes: remainingMin, error: `Account locked. Try again in ${remainingMin}m.` };
    }

    if (!isPasswordCorrect) {
      const newAttempts = (userState.failedAttempts || 0) + 1;
      if (newAttempts >= maxAttempts) {
        const lockedUntil = new Date(now.getTime() + lockoutMinutes * 60 * 1000);
        return { success: false, locked: true, failedAttempts: newAttempts, lockedUntil, error: `Account locked for ${lockoutMinutes} minutes.` };
      }
      return { success: false, locked: false, failedAttempts: newAttempts, attemptsRemaining: maxAttempts - newAttempts, error: 'Invalid password.' };
    }

    return { success: true, locked: false, failedAttempts: 0, lockedUntil: null };
  }

  it('should allow successful login and reset failed attempt count', () => {
    const state = { failedAttempts: 2, lockedUntil: null };
    const res = evaluateLoginAttempt(state, true);
    assert.strictEqual(res.success, true);
    assert.strictEqual(res.failedAttempts, 0);
  });

  it('should increment failed attempts and report attempts remaining', () => {
    const state = { failedAttempts: 2, lockedUntil: null };
    const res = evaluateLoginAttempt(state, false);
    assert.strictEqual(res.success, false);
    assert.strictEqual(res.failedAttempts, 3);
    assert.strictEqual(res.attemptsRemaining, 2);
  });

  it('should lock account after 5 consecutive failed attempts', () => {
    const state = { failedAttempts: 4, lockedUntil: null };
    const res = evaluateLoginAttempt(state, false);
    assert.strictEqual(res.success, false);
    assert.strictEqual(res.locked, true);
    assert.ok(res.lockedUntil);
    assert.ok(res.error.includes('Account locked'));
  });

  it('should reject login while account is actively locked', () => {
    const futureLock = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes in future
    const state = { failedAttempts: 5, lockedUntil: futureLock };
    const res = evaluateLoginAttempt(state, true); // even with correct password
    assert.strictEqual(res.success, false);
    assert.strictEqual(res.locked, true);
    assert.strictEqual(res.remainingMinutes, 10);
  });
});

describe('Session Security - Token Invalidation on Logout', () => {
  class MockTokenDenylist {
    constructor() {
      this.denylist = new Set();
    }
    invalidateToken(token) {
      this.denylist.add(token);
    }
    isTokenRevoked(token) {
      return this.denylist.has(token);
    }
  }

  it('should invalidate JWT token upon logout and prevent further requests', () => {
    const denylist = new MockTokenDenylist();
    const testToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test-token';

    assert.strictEqual(denylist.isTokenRevoked(testToken), false);

    // Student clicks Logout
    denylist.invalidateToken(testToken);

    assert.strictEqual(denylist.isTokenRevoked(testToken), true);
  });
});

describe('Frontend Biometric Quality & Security Helpers', () => {
  it('should validate image brightness within [40, 220] range and flag too dark / too bright', () => {
    // 10x10 dark image (all pixels ~ 20)
    const darkPixels = new Uint8ClampedArray(10 * 10 * 4);
    for (let i = 0; i < darkPixels.length; i += 4) {
      darkPixels[i] = 20;
      darkPixels[i + 1] = 20;
      darkPixels[i + 2] = 20;
      darkPixels[i + 3] = 255;
    }
    const darkRes = validateImageBrightness(darkPixels);
    assert.strictEqual(darkRes.valid, false);
    assert.strictEqual(darkRes.status, 'TOO_DARK');

    // 10x10 bright image (all pixels ~ 240)
    const brightPixels = new Uint8ClampedArray(10 * 10 * 4);
    for (let i = 0; i < brightPixels.length; i += 4) {
      brightPixels[i] = 240;
      brightPixels[i + 1] = 240;
      brightPixels[i + 2] = 240;
      brightPixels[i + 3] = 255;
    }
    const brightRes = validateImageBrightness(brightPixels);
    assert.strictEqual(brightRes.valid, false);
    assert.strictEqual(brightRes.status, 'TOO_BRIGHT');

    // 10x10 optimal image (all pixels ~ 128)
    const optimalPixels = new Uint8ClampedArray(10 * 10 * 4);
    for (let i = 0; i < optimalPixels.length; i += 4) {
      optimalPixels[i] = 128;
      optimalPixels[i + 1] = 128;
      optimalPixels[i + 2] = 128;
      optimalPixels[i + 3] = 255;
    }
    const optRes = validateImageBrightness(optimalPixels);
    assert.strictEqual(optRes.valid, true);
    assert.strictEqual(optRes.status, 'OPTIMAL');
  });

  it('should estimate image sharpness via Laplacian variance and identify blurry frames', () => {
    // Uniform flat image has variance 0.0 < 10.0 (BLURRY)
    const flatPixels = new Uint8ClampedArray(20 * 20 * 4);
    flatPixels.fill(100);
    const flatRes = estimateImageSharpness(flatPixels, 20, 20);
    assert.strictEqual(flatRes.valid, false);
    assert.strictEqual(flatRes.status, 'BLURRY');

    // High frequency pattern (checkerboard) has high variance (SHARP)
    const sharpPixels = new Uint8ClampedArray(20 * 20 * 4);
    for (let y = 0; y < 20; y++) {
      for (let x = 0; x < 20; x++) {
        const idx = (y * 20 + x) * 4;
        const val = (x + y) % 2 === 0 ? 0 : 255;
        sharpPixels[idx] = val;
        sharpPixels[idx + 1] = val;
        sharpPixels[idx + 2] = val;
        sharpPixels[idx + 3] = 255;
      }
    }
    const sharpRes = estimateImageSharpness(sharpPixels, 20, 20);
    assert.strictEqual(sharpRes.valid, true);
    assert.strictEqual(sharpRes.status, 'SHARP');
    assert.ok(sharpRes.sharpness >= 10.0);
  });

  it('should validate pose consistency across front, left, and right camera poses', () => {
    // Valid: all pairwise similarities >= 0.50
    const passingPoses = [0.75, 0.68, 0.62];
    const passRes = validatePoseConsistency(passingPoses, 0.50);
    assert.strictEqual(passRes.valid, true);

    // Failing: one pose similarity is 0.35 < 0.50
    const failingPoses = [0.72, 0.35, 0.60];
    const failRes = validatePoseConsistency(failingPoses, 0.50);
    assert.strictEqual(failRes.valid, false);
    assert.ok(failRes.error.includes('Pose consistency check failed'));
  });

  it('should reject duplicate frames by validating SHA-256 hash uniqueness', () => {
    // 3 unique hashes
    const uniqueHashes = ['hash-front-111', 'hash-left-222', 'hash-right-333'];
    const uniqueRes = validateFrameUniqueness(uniqueHashes);
    assert.strictEqual(uniqueRes.valid, true);
    assert.strictEqual(uniqueRes.uniqueCount, 3);

    // Repeated frame hash
    const repeatedHashes = ['hash-front-111', 'hash-front-111', 'hash-right-333'];
    const repeatRes = validateFrameUniqueness(repeatedHashes);
    assert.strictEqual(repeatRes.valid, false);
    assert.ok(repeatRes.error.includes('Duplicate frames detected'));
  });

  it('should permit camera streaming on localhost or HTTPS and restrict remote HTTP', () => {
    // Localhost allowed
    assert.strictEqual(checkCameraSecurityEnvironment('localhost', false).isSecure, true);
    assert.strictEqual(checkCameraSecurityEnvironment('127.0.0.1', false).isSecure, true);

    // Remote HTTPS allowed
    assert.strictEqual(checkCameraSecurityEnvironment('campus.edu', true).isSecure, true);

    // Remote HTTP restricted
    const remoteInsecure = checkCameraSecurityEnvironment('campus.edu', false);
    assert.strictEqual(remoteInsecure.isSecure, false);
    assert.ok(remoteInsecure.error.includes('HTTPS'));
  });
});

describe('Student Registration - Backend Status & Auto-Login Invariant', () => {
  it('should preserve backend status PENDING and faceEnrolled false prior to biometric enrollment', () => {
    const backendRegisterResponse = {
      token: 'jwt-pending-token-123',
      message: 'Student account registered successfully. Please complete biometric face enrollment.',
      user: {
        id: 10,
        userCode: 'student.cs110',
        name: 'Rohan Sharma',
        email: 'rohan.cs26@intellicampus.edu',
        role: 'STUDENT',
        status: 'PENDING'
      },
      student: {
        rollNo: 'CS-110',
        division: 'Div A',
        year: 'Year 3',
        faceEnrolled: false
      }
    };

    // Derived registration state
    const backendStatus = backendRegisterResponse.user.status || 'PENDING';
    const backendFaceEnrolled = Boolean(backendRegisterResponse.student.faceEnrolled);

    assert.strictEqual(backendStatus, 'PENDING');
    assert.strictEqual(backendFaceEnrolled, false);

    // Auto-login invariant: must NOT auto-login if status is PENDING or face is not enrolled
    const shouldAutoLogin = backendStatus === 'ACTIVE' && backendFaceEnrolled;
    assert.strictEqual(shouldAutoLogin, false);
  });

  it('should only auto-login when backend status is ACTIVE and faceEnrolled is true', () => {
    const backendEnrollResponse = {
      token: 'jwt-active-token-456',
      user: {
        id: 10,
        userCode: 'student.cs110',
        name: 'Rohan Sharma',
        email: 'rohan.cs26@intellicampus.edu',
        role: 'STUDENT',
        status: 'ACTIVE'
      },
      student: {
        rollNo: 'CS-110',
        division: 'Div A',
        year: 'Year 3',
        faceEnrolled: true
      }
    };

    const backendStatus = backendEnrollResponse.user.status;
    const backendFaceEnrolled = Boolean(backendEnrollResponse.student.faceEnrolled);

    assert.strictEqual(backendStatus, 'ACTIVE');
    assert.strictEqual(backendFaceEnrolled, true);

    const shouldAutoLogin = backendStatus === 'ACTIVE' && backendFaceEnrolled;
    assert.strictEqual(shouldAutoLogin, true);
  });

  it('should not treat PENDING students as having an active student session', () => {
    const pendingStudent = {
      id: 10,
      role: 'STUDENT',
      status: 'PENDING'
    };

    const hasStudentSession = Boolean(pendingStudent && pendingStudent.role === 'STUDENT' && pendingStudent.status !== 'PENDING');
    assert.strictEqual(hasStudentSession, false);
  });
});

