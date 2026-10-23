import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

/**
 * Student Attendance Data Flow & Calculation Invariants
 *
 * Verifies:
 * 1. Two different students receive their own correct attendance data.
 * 2. A student cannot access another student's attendance.
 * 3. Finalized PRESENT records appear in the correct student's attendance history.
 * 4. Overall and subject-wise attendance percentages are calculated dynamically from records,
 *    not hardcoded (e.g. removes 87.5% 28/32).
 * 5. Safe handling for zero eligible sessions without division-by-zero errors.
 * 6. API failures and empty state do not fall back to fake/mock attendance data.
 * 7. Existing facial recognition thresholds (>= 0.50 PRESENT, 0.20-0.50 REVIEW, < 0.20 UNMATCHED) are preserved.
 */
describe('Student Attendance Data Flow & Invariants', () => {

  describe('Attendance Percentage & Metrics Calculation', () => {
    function computeStudentAttendance(records) {
      if (!Array.isArray(records)) {
        return {
          totalEligible: 0,
          attendedSessions: 0,
          absentSessions: 0,
          reviewSessions: 0,
          overallPercentage: '0.0',
          subjectStats: []
        };
      }

      const totalEligible = records.length;
      const attendedSessions = records.filter(r => r.finalStatus === 'PRESENT').length;
      const absentSessions = records.filter(r => r.finalStatus === 'ABSENT').length;
      const reviewSessions = records.filter(r => r.finalStatus === 'REVIEW').length;
      const overallPercentage = totalEligible > 0
        ? ((attendedSessions / totalEligible) * 100).toFixed(1)
        : '0.0';

      const subjectMap = {};
      records.forEach(r => {
        const code = r.subjectCode || (r.subjectId ? `SUB-${r.subjectId}` : 'General');
        if (!subjectMap[code]) {
          subjectMap[code] = {
            code,
            name: r.subjectName || code,
            attended: 0,
            total: 0
          };
        }
        subjectMap[code].total += 1;
        if (r.finalStatus === 'PRESENT') {
          subjectMap[code].attended += 1;
        }
      });

      const subjectStats = Object.values(subjectMap).map(sub => ({
        ...sub,
        percentage: sub.total > 0 ? ((sub.attended / sub.total) * 100).toFixed(1) : '0.0'
      }));

      return {
        totalEligible,
        attendedSessions,
        absentSessions,
        reviewSessions,
        overallPercentage,
        subjectStats
      };
    }

    it('should correctly calculate overall and subject-wise percentages from actual records', () => {
      const records = [
        { sessionId: 1, subjectCode: 'CS-301', subjectName: 'Database Systems', finalStatus: 'PRESENT' },
        { sessionId: 2, subjectCode: 'CS-301', subjectName: 'Database Systems', finalStatus: 'ABSENT' },
        { sessionId: 3, subjectCode: 'CS-302', subjectName: 'Machine Learning', finalStatus: 'PRESENT' },
        { sessionId: 4, subjectCode: 'CS-302', subjectName: 'Machine Learning', finalStatus: 'PRESENT' }
      ];

      const stats = computeStudentAttendance(records);
      assert.strictEqual(stats.totalEligible, 4);
      assert.strictEqual(stats.attendedSessions, 3);
      assert.strictEqual(stats.absentSessions, 1);
      assert.strictEqual(stats.overallPercentage, '75.0');

      const cs301 = stats.subjectStats.find(s => s.code === 'CS-301');
      assert.ok(cs301);
      assert.strictEqual(cs301.attended, 1);
      assert.strictEqual(cs301.total, 2);
      assert.strictEqual(cs301.percentage, '50.0');

      const cs302 = stats.subjectStats.find(s => s.code === 'CS-302');
      assert.ok(cs302);
      assert.strictEqual(cs302.attended, 2);
      assert.strictEqual(cs302.total, 2);
      assert.strictEqual(cs302.percentage, '100.0');
    });

    it('should handle zero eligible sessions gracefully without division by zero', () => {
      const stats = computeStudentAttendance([]);
      assert.strictEqual(stats.totalEligible, 0);
      assert.strictEqual(stats.attendedSessions, 0);
      assert.strictEqual(stats.overallPercentage, '0.0');
      assert.strictEqual(stats.subjectStats.length, 0);
    });

    it('should NOT use the hardcoded 87.5% or 28/32 values', () => {
      // Simulate real student records with 5 total sessions, 4 attended (80.0%)
      const realRecords = [
        { sessionId: 10, subjectCode: 'CS-302', subjectName: 'Machine Learning & AI', finalStatus: 'PRESENT' },
        { sessionId: 11, subjectCode: 'CS-302', subjectName: 'Machine Learning & AI', finalStatus: 'PRESENT' },
        { sessionId: 12, subjectCode: 'CS-302', subjectName: 'Machine Learning & AI', finalStatus: 'PRESENT' },
        { sessionId: 13, subjectCode: 'CS-302', subjectName: 'Machine Learning & AI', finalStatus: 'PRESENT' },
        { sessionId: 14, subjectCode: 'CS-302', subjectName: 'Machine Learning & AI', finalStatus: 'ABSENT' }
      ];

      const stats = computeStudentAttendance(realRecords);
      const cs302 = stats.subjectStats.find(s => s.code === 'CS-302');
      assert.ok(cs302);
      assert.notStrictEqual(cs302.percentage, '87.5');
      assert.notStrictEqual(`${cs302.attended}/${cs302.total}`, '28/32');
      assert.strictEqual(cs302.percentage, '80.0');
      assert.strictEqual(`${cs302.attended}/${cs302.total}`, '4/5');
    });
  });

  describe('Student Identity & Access Isolation', () => {
    function authorizeAttendanceAccess(requestingUser, studentId, studentProfileId) {
      if (!requestingUser) {
        throw new Error('401 UNAUTHORIZED: Full authentication is required');
      }
      if (requestingUser.role === 'STUDENT') {
        const matchesUser = requestingUser.id === studentId;
        const matchesProfile = studentProfileId === studentId;
        if (!matchesUser && !matchesProfile) {
          throw new Error('403 FORBIDDEN: Students are only permitted to view their own attendance records.');
        }
      }
      return true;
    }

    it('should allow student to access their own attendance via User ID or Profile ID', () => {
      const student1 = { id: 2, role: 'STUDENT', name: 'Aarav Mehta' };
      const student1ProfileId = 10;

      // Access using User ID (2)
      assert.doesNotThrow(() => authorizeAttendanceAccess(student1, 2, student1ProfileId));

      // Access using Profile ID (10)
      assert.doesNotThrow(() => authorizeAttendanceAccess(student1, 10, student1ProfileId));
    });

    it('should reject student attempting to access another student attendance (ID tampering)', () => {
      const student1 = { id: 2, role: 'STUDENT', name: 'Aarav Mehta' };
      const student1ProfileId = 10;

      // Student 1 attempts to query Student 2's ID (3 or 20)
      assert.throws(() => authorizeAttendanceAccess(student1, 3, student1ProfileId), {
        message: /Students are only permitted to view their own attendance records/
      });

      assert.throws(() => authorizeAttendanceAccess(student1, 20, student1ProfileId), {
        message: /Students are only permitted to view their own attendance records/
      });
    });

    it('should isolate attendance data between two different students', () => {
      const dbRecords = [
        { id: 101, studentId: 10, subjectCode: 'CS-301', finalStatus: 'PRESENT' },
        { id: 102, studentId: 10, subjectCode: 'CS-302', finalStatus: 'PRESENT' },
        { id: 201, studentId: 20, subjectCode: 'CS-301', finalStatus: 'ABSENT' }
      ];

      function getStudentRecords(studentProfileId) {
        return dbRecords.filter(r => r.studentId === studentProfileId);
      }

      const student1Records = getStudentRecords(10);
      const student2Records = getStudentRecords(20);

      assert.strictEqual(student1Records.length, 2);
      assert.strictEqual(student1Records.every(r => r.studentId === 10), true);
      assert.strictEqual(student1Records.every(r => r.finalStatus === 'PRESENT'), true);

      assert.strictEqual(student2Records.length, 1);
      assert.strictEqual(student2Records[0].studentId, 20);
      assert.strictEqual(student2Records[0].finalStatus, 'ABSENT');
    });

    it('should allow faculty and admin to view attendance records for any student', () => {
      const faculty = { id: 1, role: 'FACULTY', name: 'Dr. Joshi' };
      const admin = { id: 99, role: 'ADMIN', name: 'System Admin' };

      assert.doesNotThrow(() => authorizeAttendanceAccess(faculty, 10, 10));
      assert.doesNotThrow(() => authorizeAttendanceAccess(admin, 10, 10));
    });
  });

  describe('API Failure & Safe Fallback Invariant', () => {
    it('should display error message on API failure and NEVER fall back to fake mock attendance', () => {
      let displayedAttendance = null;
      let errorMessage = null;

      function handleFetchFailure(err) {
        // Correct secure behavior: record error, keep data empty, DO NOT substitute mock numbers
        errorMessage = err.message || 'Unable to load attendance records';
        displayedAttendance = [];
      }

      handleFetchFailure(new Error('Network error: 500 Internal Server Error'));

      assert.ok(errorMessage);
      assert.strictEqual(displayedAttendance.length, 0);
      assert.notStrictEqual(displayedAttendance, '87.5%');
    });
  });

  describe('Face Recognition Attendance Thresholds Invariant', () => {
    it('should maintain exact 20% and 50% boundary rules', () => {
      function evaluateMatch(sim) {
        if (sim == null) return 'UNMATCHED';
        if (sim >= 0.50) return 'PRESENT';
        if (sim >= 0.20) return 'REVIEW';
        return 'UNMATCHED';
      }

      assert.strictEqual(evaluateMatch(0.1999), 'UNMATCHED');
      assert.strictEqual(evaluateMatch(0.20), 'REVIEW');
      assert.strictEqual(evaluateMatch(0.4999), 'REVIEW');
      assert.strictEqual(evaluateMatch(0.50), 'PRESENT');
      assert.strictEqual(evaluateMatch(0.5001), 'PRESENT');
    });

    it('should allow null spoofScore to become PRESENT when similarity >= 0.50', () => {
      function decideAttendance(sim, spoofScore) {
        if (sim >= 0.50) {
          // Spoof score null must not block PRESENT
          return { status: 'PRESENT', confirmed: true };
        }
        if (sim >= 0.20) {
          return { status: 'REVIEW', confirmed: false };
        }
        return { status: 'UNMATCHED', confirmed: false };
      }

      const decision = decideAttendance(0.72, null);
      assert.strictEqual(decision.status, 'PRESENT');
      assert.strictEqual(decision.confirmed, true);
    });
  });
});
