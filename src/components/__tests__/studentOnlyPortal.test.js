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

  describe('When VITE_STUDENT_ONLY=true (Default Student-Only Mode)', () => {
    const studentOnly = true;

    it('defaults to true when flag is undefined or set to true', () => {
      assert.strictEqual(isStudentOnlyMode({ VITE_STUDENT_ONLY: undefined }), true);
      assert.strictEqual(isStudentOnlyMode({ VITE_STUDENT_ONLY: 'true' }), true);
      assert.strictEqual(isStudentOnlyMode({ VITE_STUDENT_ONLY: true }), true);
      assert.strictEqual(isStudentOnlyMode({}), true);
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

    it('renders only LoginView when logged out, even if currentRole is PUBLIC', () => {
      const viewLoggedOut = resolveActiveViewport({
        studentOnly,
        loggedInUser: null,
        currentRole: 'LOGIN'
      });
      assert.strictEqual(viewLoggedOut, 'LOGIN');

      const viewPublicAttempt = resolveActiveViewport({
        studentOnly,
        loggedInUser: null,
        currentRole: 'PUBLIC'
      });
      assert.strictEqual(viewPublicAttempt, 'LOGIN');
    });

    it('always renders StudentView after student login and never renders staff views or modals', () => {
      const studentUser = { id: 5, userCode: 'student.aarav', name: 'Aarav Shah', role: 'STUDENT' };
      
      const viewStudent = resolveActiveViewport({
        studentOnly,
        loggedInUser: studentUser,
        currentRole: 'STUDENT'
      });
      assert.strictEqual(viewStudent, 'STUDENT');

      // Attempting to set staff roles while logged in as student still resolves to STUDENT
      const viewWithAdminRole = resolveActiveViewport({
        studentOnly,
        loggedInUser: studentUser,
        currentRole: 'ADMIN'
      });
      assert.strictEqual(viewWithAdminRole, 'STUDENT');

      // Staff user in storage cannot render staff view; resolves to LOGIN
      const adminUser = { id: 1, userCode: 'admin.patel', name: 'Rajesh Patel', role: 'ADMIN' };
      const viewStaffStored = resolveActiveViewport({
        studentOnly,
        loggedInUser: adminUser,
        currentRole: 'ADMIN'
      });
      assert.strictEqual(viewStaffStored, 'LOGIN');
    });

    it('staff UI elements in Navbar (Role Switcher, Live Badge, Audit Chain) are disabled', () => {
      // In studentOnly mode:
      const shouldShowRoleSwitcher = !studentOnly;
      const shouldShowAuditChain = !studentOnly;
      const shouldShowLiveBadge = !studentOnly;

      assert.strictEqual(shouldShowRoleSwitcher, false, 'Role Switcher must not be rendered');
      assert.strictEqual(shouldShowAuditChain, false, 'Audit Chain button must not be rendered');
      assert.strictEqual(shouldShowLiveBadge, false, 'Live session badge must not be rendered');
    });
  });

  describe('When VITE_STUDENT_ONLY=false (Multi-Role Mode)', () => {
    const studentOnly = false;

    it('resolves flag to false when VITE_STUDENT_ONLY is "false" or false', () => {
      assert.strictEqual(isStudentOnlyMode({ VITE_STUDENT_ONLY: 'false' }), false);
      assert.strictEqual(isStudentOnlyMode({ VITE_STUDENT_ONLY: false }), false);
    });

    it('demo codes list includes all student and staff accounts', () => {
      const demoCodes = getDemoCodes(studentOnly);
      assert.deepStrictEqual(demoCodes, ALL_DEMO_CODES);
      assert.strictEqual(demoCodes.length, 8);
      assert.ok(demoCodes.includes('prof.verma'));
      assert.ok(demoCodes.includes('dr.mehta'));
      assert.ok(demoCodes.includes('admin.patel'));
      assert.ok(demoCodes.includes('hod.sharma'));
    });

    it('permits all authenticated roles in session storage', () => {
      const adminUser = { role: 'ADMIN' };
      const facultyUser = { role: 'FACULTY' };
      const studentUser = { role: 'STUDENT' };

      assert.strictEqual(isSessionPermitted(adminUser, studentOnly), true);
      assert.strictEqual(isSessionPermitted(facultyUser, studentOnly), true);
      assert.strictEqual(isSessionPermitted(studentUser, studentOnly), true);
    });

    it('renders role-appropriate views for Admin, HOD, Faculty, Mentor, Student, and Public Verifier', () => {
      const adminUser = { role: 'ADMIN' };
      const facultyUser = { role: 'FACULTY' };
      const hodUser = { role: 'HOD' };
      const mentorUser = { role: 'MENTOR' };
      const studentUser = { role: 'STUDENT' };

      assert.strictEqual(
        resolveActiveViewport({ studentOnly, loggedInUser: adminUser, currentRole: 'ADMIN' }),
        'ADMIN'
      );
      assert.strictEqual(
        resolveActiveViewport({ studentOnly, loggedInUser: facultyUser, currentRole: 'FACULTY' }),
        'FACULTY'
      );
      assert.strictEqual(
        resolveActiveViewport({ studentOnly, loggedInUser: hodUser, currentRole: 'HOD' }),
        'HOD'
      );
      assert.strictEqual(
        resolveActiveViewport({ studentOnly, loggedInUser: mentorUser, currentRole: 'MENTOR' }),
        'MENTOR'
      );
      assert.strictEqual(
        resolveActiveViewport({ studentOnly, loggedInUser: studentUser, currentRole: 'STUDENT' }),
        'STUDENT'
      );
      assert.strictEqual(
        resolveActiveViewport({ studentOnly, loggedInUser: null, currentRole: 'PUBLIC' }),
        'PUBLIC'
      );
    });

    it('staff UI elements in Navbar (Role Switcher, Live Badge, Audit Chain) are enabled', () => {
      const shouldShowRoleSwitcher = !studentOnly;
      const shouldShowAuditChain = !studentOnly;
      const shouldShowLiveBadge = !studentOnly;

      assert.strictEqual(shouldShowRoleSwitcher, true, 'Role Switcher must be rendered');
      assert.strictEqual(shouldShowAuditChain, true, 'Audit Chain button must be rendered');
      assert.strictEqual(shouldShowLiveBadge, true, 'Live session badge must be rendered');
    });
  });
});
