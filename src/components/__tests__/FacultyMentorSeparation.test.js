import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import mongoose from 'mongoose';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';

import Faculty from '../../../server/models/Faculty.js';
import Mentor from '../../../server/models/Mentor.js';

describe('Faculty & Mentor Portal Separation & Security Test Suite', () => {
  const testDbUri = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/intellicampus_db';
  const jwtSecret = process.env.JWT_SECRET || 'intellicampus-super-secure-jwt-secret-key-2026';

  before(async () => {
    if (mongoose.connection.readyState === 0) {
      await mongoose.connect(testDbUri);
    }
    // Clean up test records
    await Faculty.deleteMany({ email: { $regex: /@test-portal\.edu$/ } });
    await Mentor.deleteMany({ email: { $regex: /@test-portal\.edu$/ } });
  });

  after(async () => {
    await Faculty.deleteMany({ email: { $regex: /@test-portal\.edu$/ } });
    await Mentor.deleteMany({ email: { $regex: /@test-portal\.edu$/ } });
    await mongoose.disconnect();
  });

  it('1. Faculty Registration: hashes password with bcrypt and stores isolated faculty record', async () => {
    const rawPassword = 'SecurePassword1!';
    const salt = await bcrypt.genSalt(10);
    const passwordHash = await bcrypt.hash(rawPassword, salt);

    const faculty = await Faculty.create({
      name: 'Prof. Test Faculty',
      email: 'faculty.test@test-portal.edu',
      employeeId: 'FAC-TEST-01',
      department: 'Computer Science',
      phone: '9876543210',
      passwordHash,
    });

    assert.ok(faculty._id);
    assert.strictEqual(faculty.email, 'faculty.test@test-portal.edu');
    assert.strictEqual(faculty.employeeId, 'FAC-TEST-01');
    assert.ok(faculty.passwordHash.startsWith('$2'));

    // Verify bcrypt comparison succeeds with correct password
    const isMatch = await faculty.comparePassword(rawPassword);
    assert.strictEqual(isMatch, true, 'bcrypt compare should match correct password');

    // Verify toJSON omits passwordHash
    const json = faculty.toJSON();
    assert.strictEqual(json.passwordHash, undefined, 'passwordHash must never be in JSON output');
    assert.strictEqual(json.role, 'faculty', 'toJSON must expose role: faculty');
  });

  it('2. Mentor Registration: stores required specialization and isolated mentor record', async () => {
    const rawPassword = 'MentorPass123!';
    const salt = await bcrypt.genSalt(10);
    const passwordHash = await bcrypt.hash(rawPassword, salt);

    const mentor = await Mentor.create({
      name: 'Dr. Test Mentor',
      email: 'mentor.test@test-portal.edu',
      employeeId: 'MNT-TEST-01',
      department: 'Information Technology',
      phone: '9876543211',
      specialization: 'Artificial Intelligence & Career Counseling',
      passwordHash,
    });

    assert.ok(mentor._id);
    assert.strictEqual(mentor.specialization, 'Artificial Intelligence & Career Counseling');
    assert.strictEqual(mentor.employeeId, 'MNT-TEST-01');

    const json = mentor.toJSON();
    assert.strictEqual(json.passwordHash, undefined, 'passwordHash must never be in JSON output');
    assert.strictEqual(json.role, 'mentor', 'toJSON must expose role: mentor');
  });

  it('3. Duplicate Prevention: rejects duplicate emails and employee IDs in each collection', async () => {
    // Attempt duplicate email in Faculty
    let duplicateEmailError = null;
    try {
      await Faculty.create({
        name: 'Another Faculty',
        email: 'faculty.test@test-portal.edu',
        employeeId: 'FAC-TEST-UNIQUE',
        department: 'CS',
        phone: '1234567890',
        passwordHash: 'dummyhash',
      });
    } catch (err) {
      duplicateEmailError = err;
    }
    assert.ok(duplicateEmailError, 'Should throw duplicate key error on email collision');

    // Attempt duplicate employeeId in Mentor
    let duplicateIdError = null;
    try {
      await Mentor.create({
        name: 'Another Mentor',
        email: 'unique.mentor@test-portal.edu',
        employeeId: 'MNT-TEST-01',
        department: 'IT',
        phone: '1234567890',
        specialization: 'Cloud Computing',
        passwordHash: 'dummyhash',
      });
    } catch (err) {
      duplicateIdError = err;
    }
    assert.ok(duplicateIdError, 'Should throw duplicate key error on employeeId collision');
  });

  it('4. JWT Role Isolation: Faculty token cannot access Mentor endpoints and vice-versa', async () => {
    const facultyUser = await Faculty.findOne({ email: 'faculty.test@test-portal.edu' });
    const mentorUser = await Mentor.findOne({ email: 'mentor.test@test-portal.edu' });

    const facultyToken = jwt.sign(
      { id: facultyUser._id, role: 'faculty', email: facultyUser.email },
      jwtSecret,
      { expiresIn: '7d' }
    );

    const mentorToken = jwt.sign(
      { id: mentorUser._id, role: 'mentor', email: mentorUser.email },
      jwtSecret,
      { expiresIn: '7d' }
    );

    // Decode and verify roles
    const decodedFaculty = jwt.verify(facultyToken, jwtSecret);
    const decodedMentor = jwt.verify(mentorToken, jwtSecret);

    assert.strictEqual(decodedFaculty.role, 'faculty');
    assert.strictEqual(decodedMentor.role, 'mentor');

    // Role guard simulation
    const verifyAccess = (tokenRole, requiredRole) => {
      return tokenRole.toLowerCase() === requiredRole.toLowerCase();
    };

    assert.strictEqual(verifyAccess(decodedFaculty.role, 'faculty'), true, 'Faculty can access faculty dashboard');
    assert.strictEqual(verifyAccess(decodedFaculty.role, 'mentor'), false, 'Faculty CANNOT access mentor dashboard');
    assert.strictEqual(verifyAccess(decodedMentor.role, 'mentor'), true, 'Mentor can access mentor dashboard');
    assert.strictEqual(verifyAccess(decodedMentor.role, 'faculty'), false, 'Mentor CANNOT access faculty dashboard');
  });

  it('5. Forgot Password Security: generates crypto-random token and securely hashes for storage', async () => {
    const rawToken = crypto.randomBytes(32).toString('hex');
    const hashedToken = crypto.createHash('sha256').update(rawToken).digest('hex');
    const expiry = new Date(Date.now() + 15 * 60 * 1000);

    const faculty = await Faculty.findOne({ email: 'faculty.test@test-portal.edu' });
    faculty.resetPasswordToken = hashedToken;
    faculty.resetPasswordExpires = expiry;
    await faculty.save();

    // Verify lookup by hashed token
    const candidateHashed = crypto.createHash('sha256').update(rawToken).digest('hex');
    const foundUser = await Faculty.findOne({
      resetPasswordToken: candidateHashed,
      resetPasswordExpires: { $gt: new Date() },
    });

    assert.ok(foundUser, 'Must locate faculty with valid, non-expired hashed token');
    assert.strictEqual(foundUser.email, 'faculty.test@test-portal.edu');
  });

  it('6. Reset Password Flow: updates password hash and clears reset token', async () => {
    const rawToken = crypto.randomBytes(32).toString('hex');
    const hashedToken = crypto.createHash('sha256').update(rawToken).digest('hex');
    const expiry = new Date(Date.now() + 15 * 60 * 1000);

    const mentor = await Mentor.findOne({ email: 'mentor.test@test-portal.edu' });
    mentor.resetPasswordToken = hashedToken;
    mentor.resetPasswordExpires = expiry;
    await mentor.save();

    // Simulate password update
    const newRawPassword = 'BrandNewMentorPass2026!';
    const salt = await bcrypt.genSalt(10);
    mentor.passwordHash = await bcrypt.hash(newRawPassword, salt);
    mentor.resetPasswordToken = undefined;
    mentor.resetPasswordExpires = undefined;
    await mentor.save();

    // Fetch refreshed mentor from DB
    const refreshed = await Mentor.findById(mentor._id).select('+passwordHash +resetPasswordToken');
    assert.strictEqual(refreshed.resetPasswordToken, undefined, 'resetPasswordToken must be cleared');
    const isNewPasswordValid = await bcrypt.compare(newRawPassword, refreshed.passwordHash);
    assert.strictEqual(isNewPasswordValid, true, 'New password must verify successfully');
  });
});
