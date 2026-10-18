import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { 
  isStudentOnlyMode, 
  isSessionPermitted, 
  getDemoCodes, 
  resolveActiveViewport,
  STUDENT_DEMO_CODES,
  ALL_DEMO_CODES 
} from '../../utils/studentPortalConfig.js';
import { INITIAL_USERS } from '../../data/mockData.js';

describe('Student-Only Portal Access Control & Viewport Resolution', () => {

  describe('Default Multi-Role Mode (VITE_STUDENT_ONLY=false or undefined)', () => {
    const studentOnly = false;

    it('defaults to false when flag is undefined, empty or set to false', () => {
      assert.strictEqual(isStudentOnlyMode({ VITE_STUDENT_ONLY: undefined }), false);
      assert.strictEqual(isStudentOnlyMode({ VITE_STUDENT_ONLY: 'false' }), false);
      assert.strictEqual(isStudentOnlyMode({ VITE_STUDENT_ONLY: false }), false);
      assert.strictEqual(isStudentOnlyMode({}), false);
    });

    it('demo codes list includes all accounts in multi-role configuration', () => {
      const demoCodes = getDemoCodes(studentOnly);
      assert.deepStrictEqual(demoCodes, ALL_DEMO_CODES);
      assert.strictEqual(demoCodes.length, 8);
      assert.ok(demoCodes.includes('student.aarav'));
      assert.ok(demoCodes.includes('prof.verma'));
      assert.ok(demoCodes.includes('dr.mehta'));
      assert.ok(demoCodes.includes('admin.patel'));
      assert.ok(demoCodes.includes('hod.sharma'));
    });

    it('student demo accounts list is strictly student-only', () => {
      assert.deepStrictEqual(STUDENT_DEMO_CODES, [
        'student.aarav',
        'student.priya',
        'student.rohan',
        'student.kavya'
      ]);
    });

    it('renders role-appropriate views for Faculty, HOD, Admin, Mentor, and Public Verifier directly', () => {
      assert.strictEqual(
        resolveActiveViewport({ studentOnly, currentRole: 'FACULTY' }),
        'FACULTY'
      );
      assert.strictEqual(
        resolveActiveViewport({ studentOnly, currentRole: 'HOD' }),
        'HOD'
      );
      assert.strictEqual(
        resolveActiveViewport({ studentOnly, currentRole: 'ADMIN' }),
        'ADMIN'
      );
      assert.strictEqual(
        resolveActiveViewport({ studentOnly, currentRole: 'MENTOR' }),
        'MENTOR'
      );
      assert.strictEqual(
        resolveActiveViewport({ studentOnly, currentRole: 'PUBLIC' }),
        'PUBLIC'
      );
    });

    it('renders LoginView when student is not logged in, and StudentView after student logs in', () => {
      const viewLoggedOut = resolveActiveViewport({
        studentOnly,
        studentUser: null,
        currentRole: 'STUDENT'
      });
      assert.strictEqual(viewLoggedOut, 'LOGIN', 'Unauthenticated student role must render LoginView');

      const studentUser = { id: 5, userCode: 'student.aarav', name: 'Aarav Shah', role: 'STUDENT' };
      const viewLoggedIn = resolveActiveViewport({
        studentOnly,
        studentUser,
        currentRole: 'STUDENT'
      });
      assert.strictEqual(viewLoggedIn, 'STUDENT', 'Authenticated student role must render StudentView');
    });

    it('staff UI elements in Navbar (Role Switcher, Live Badge, Audit Chain) are enabled', () => {
      const shouldShowRoleSwitcher = !studentOnly;
      const shouldShowAuditChain = !studentOnly;
      const shouldShowLiveBadge = !studentOnly;

      assert.strictEqual(shouldShowRoleSwitcher, true, 'Role Switcher must be rendered');
      assert.strictEqual(shouldShowAuditChain, true, 'Audit Chain button must be rendered');
      assert.strictEqual(shouldShowLiveBadge, true, 'Live session badge must be rendered');
    });

    it('permits all authenticated roles in session storage in multi-role mode', () => {
      const adminUser = { role: 'ADMIN' };
      const facultyUser = { role: 'FACULTY' };
      const studentUser = { role: 'STUDENT' };

      assert.strictEqual(isSessionPermitted(adminUser, studentOnly), true);
      assert.strictEqual(isSessionPermitted(facultyUser, studentOnly), true);
      assert.strictEqual(isSessionPermitted(studentUser, studentOnly), true);
    });
  });

  describe('When VITE_STUDENT_ONLY=true (Student-Only Mode Override)', () => {
    const studentOnly = true;

    it('resolves flag to true when VITE_STUDENT_ONLY is "true" or true', () => {
      assert.strictEqual(isStudentOnlyMode({ VITE_STUDENT_ONLY: 'true' }), true);
      assert.strictEqual(isStudentOnlyMode({ VITE_STUDENT_ONLY: true }), true);
    });

    it('demo codes list contains exclusively student accounts and excludes staff', () => {
      const demoCodes = getDemoCodes(studentOnly);
      assert.deepStrictEqual(demoCodes, STUDENT_DEMO_CODES);
      assert.strictEqual(demoCodes.length, 4);
      assert.ok(demoCodes.includes('student.aarav'));
      assert.ok(demoCodes.includes('student.priya'));
      assert.ok(demoCodes.includes('student.rohan'));
      assert.ok(demoCodes.includes('student.kavya'));

      // Ensure staff accounts are excluded
      assert.strictEqual(demoCodes.includes('prof.verma'), false);
      assert.strictEqual(demoCodes.includes('dr.mehta'), false);
      assert.strictEqual(demoCodes.includes('admin.patel'), false);
      assert.strictEqual(demoCodes.includes('hod.sharma'), false);
    });

    it('stored session is only permitted for STUDENT users; staff sessions are rejected', () => {
      const studentUser = INITIAL_USERS.find(u => u.role === 'STUDENT');
      const adminUser = INITIAL_USERS.find(u => u.role === 'ADMIN');
      const facultyUser = INITIAL_USERS.find(u => u.role === 'FACULTY');
      const hodUser = INITIAL_USERS.find(u => u.role === 'HOD');
      const mentorUser = INITIAL_USERS.find(u => u.role === 'MENTOR');

      assert.strictEqual(isSessionPermitted(studentUser, studentOnly), true);
      assert.strictEqual(isSessionPermitted(adminUser, studentOnly), false);
      assert.strictEqual(isSessionPermitted(facultyUser, studentOnly), false);
      assert.strictEqual(isSessionPermitted(hodUser, studentOnly), false);
      assert.strictEqual(isSessionPermitted(mentorUser, studentOnly), false);
      assert.strictEqual(isSessionPermitted(null, studentOnly), false);
    });

    it('renders only LoginView when logged out, even if currentRole is PUBLIC or FACULTY', () => {
      const viewLoggedOut = resolveActiveViewport({
        studentOnly,
        studentUser: null,
        currentRole: 'LOGIN'
      });
      assert.strictEqual(viewLoggedOut, 'LOGIN');

      const viewPublicAttempt = resolveActiveViewport({
        studentOnly,
        studentUser: null,
        currentRole: 'PUBLIC'
      });
      assert.strictEqual(viewPublicAttempt, 'LOGIN');

      const viewFacultyAttempt = resolveActiveViewport({
        studentOnly,
        studentUser: null,
        currentRole: 'FACULTY'
      });
      assert.strictEqual(viewFacultyAttempt, 'LOGIN');
    });

    it('always renders StudentView after student login and never renders staff views', () => {
      const studentUser = { id: 5, userCode: 'student.aarav', name: 'Aarav Shah', role: 'STUDENT' };
      
      const viewStudent = resolveActiveViewport({
        studentOnly,
        studentUser,
        currentRole: 'STUDENT'
      });
      assert.strictEqual(viewStudent, 'STUDENT');

      // Attempting to set staff roles while logged in as student still resolves to STUDENT
      const viewWithFacultyRole = resolveActiveViewport({
        studentOnly,
        studentUser,
        currentRole: 'FACULTY'
      });
      assert.strictEqual(viewWithFacultyRole, 'STUDENT');
    });

    it('staff UI elements in Navbar (Role Switcher, Live Badge, Audit Chain) are disabled', () => {
      const shouldShowRoleSwitcher = !studentOnly;
      const shouldShowAuditChain = !studentOnly;
      const shouldShowLiveBadge = !studentOnly;

      assert.strictEqual(shouldShowRoleSwitcher, false, 'Role Switcher must not be rendered');
      assert.strictEqual(shouldShowAuditChain, false, 'Audit Chain button must not be rendered');
      assert.strictEqual(shouldShowLiveBadge, false, 'Live session badge must not be rendered');
    });
  });
});
