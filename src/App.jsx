import React, { useState, useEffect } from 'react';
import './App.css';
import Navbar from './components/Navbar';
import RolePortalLanding from './components/RolePortalLanding';
import AdminView from './components/AdminView';
import HodView from './components/HodView';
import FacultyView from './components/FacultyView';
import MentorView from './components/MentorView';
import StudentView from './components/StudentView';
import StudentAuthView from './components/StudentAuthView';
import PublicVerifierView from './components/PublicVerifierView';
import AuditChainModal from './components/AuditChainModal';
import ChatbotDrawer from './components/ChatbotDrawer';

import AdminAuthView from './components/staff/AdminAuthView';
import HodAuthView from './components/staff/HodAuthView';
import StaffAuthView from './components/staff/StaffAuthView';
import StaffResetPasswordView from './components/staff/StaffResetPasswordView';
import { getStoredStaffSession, clearStaffSession, logoutStaffApi } from './utils/staffAuthService';

import {
  INITIAL_USERS,
  INITIAL_ROOMS,
  INITIAL_SUBJECTS,
  INITIAL_SESSIONS,
  INITIAL_MARKS,
  INITIAL_MARK_CORRECTIONS,
  INITIAL_LEAVE_REQUESTS,
  INITIAL_NOTICES,
  INITIAL_VERIFIABLE_DOCUMENTS,
  INITIAL_AUDIT_LOGS,
  INITIAL_KNOWLEDGE_DOCS,
  INITIAL_RISK_STUDENTS
} from './data/mockData';
import { computeAuditEntryHash } from './utils/cryptoAndGeo';
import { api, getStoredUser, clearAuthSession } from './api/client';

export default function App() {
  // Path-based routing state (/admin/login, /staff/login, /student/login, /verify, /)
  const [currentPath, setCurrentPath] = useState(() => {
    if (typeof window !== 'undefined') {
      let p = window.location.pathname;
      if (p === '/hod/login') {
        window.history.replaceState({}, '', '/staff/login?role=HOD');
        return '/staff/login';
      }
      if (p === '/principal' || p === '/principal/login') {
        window.history.replaceState({}, '', '/admin/login');
        return '/admin/login';
      }
      if (p === '/staff') {
        window.history.replaceState({}, '', '/staff/login');
        return '/staff/login';
      }
      if (p === '/student') {
        window.history.replaceState({}, '', '/student/login');
        return '/student/login';
      }
      if (p === '/public') {
        window.history.replaceState({}, '', '/verify');
        return '/verify';
      }
      return p;
    }
    return '/';
  });

  const [currentRole, setCurrentRole] = useState(() => {
    if (typeof window !== 'undefined') {
      const p = window.location.pathname;
      if (p === '/admin/login' || p === '/principal' || p === '/principal/login') return 'ADMIN';
      if (p === '/staff/login' || p === '/staff' || p === '/hod/login') return getStoredStaffSession()?.user?.role || 'STAFF';
      if (p === '/student/login' || p === '/student') return 'STUDENT';
      if (p === '/verify' || p === '/public') return 'PUBLIC';
      if (p === '/') return 'LANDING';
    }
    return 'LANDING';
  });
  const [theme, setTheme] = useState('dark');

  // Authentication State (JWT & User Session for students)
  const [authenticatedUser, setAuthenticatedUser] = useState(() => {
    const stored = getStoredUser();
    if (stored && stored.role === 'STUDENT' && stored.status === 'PENDING') {
      return null;
    }
    return stored;
  });
  const [authNotification, setAuthNotification] = useState(null);

  // Application Data Stores
  const [users, setUsers] = useState(INITIAL_USERS);
  const [rooms, setRooms] = useState(INITIAL_ROOMS);
  const [subjects, setSubjects] = useState(INITIAL_SUBJECTS);
  const [sessions, setSessions] = useState(INITIAL_SESSIONS);
  const [marks, setMarks] = useState(INITIAL_MARKS);
  const [markCorrections, setMarkCorrections] = useState(INITIAL_MARK_CORRECTIONS);
  const [leaveRequests, setLeaveRequests] = useState(INITIAL_LEAVE_REQUESTS);
  const [notices, setNotices] = useState(INITIAL_NOTICES);
  const [issuedDocuments, setIssuedDocuments] = useState(INITIAL_VERIFIABLE_DOCUMENTS);
  const [auditLogs, setAuditLogs] = useState(INITIAL_AUDIT_LOGS);
  const [knowledgeDocs, setKnowledgeDocs] = useState(INITIAL_KNOWLEDGE_DOCS);
  const [riskStudents, setRiskStudents] = useState(INITIAL_RISK_STUDENTS);

  // Load real records from backend database on initial mount
  useEffect(() => {
    let isMounted = true;

    async function loadBackendData() {
      // 1. Users
      try {
        const userList = await api.getUsers();
        if (isMounted && Array.isArray(userList)) setUsers(userList);
      } catch (err) {
        console.warn('API getUsers unavailable:', err.message);
      }

      // 2. Academic Rooms
      try {
        const roomList = await api.getRooms();
        if (isMounted && Array.isArray(roomList)) setRooms(roomList);
      } catch (err) {
        console.warn('API getRooms unavailable:', err.message);
      }

      // 3. Academic Subjects
      try {
        const subjectList = await api.getSubjects();
        if (isMounted && Array.isArray(subjectList)) setSubjects(subjectList);
      } catch (err) {
        console.warn('API getSubjects unavailable:', err.message);
      }

      // 4. Circulars & Notices
      try {
        const noticeList = await api.listNotices();
        if (isMounted && Array.isArray(noticeList)) setNotices(noticeList);
      } catch (err) {
        console.warn('API listNotices unavailable:', err.message);
      }

      // 5. Verifiable Documents
      try {
        const docList = await api.getAllDocuments();
        if (isMounted && Array.isArray(docList)) setIssuedDocuments(docList);
      } catch (err) {
        console.warn('API getAllDocuments unavailable:', err.message);
      }

      // 6. Cryptographic Audit Chain
      try {
        const chain = await api.getAuditChain();
        if (isMounted && Array.isArray(chain)) setAuditLogs(chain);
      } catch (err) {
        console.warn('API getAuditChain unavailable:', err.message);
      }

      // 7. Leaves & Permissions
      try {
        const leaveList = await api.getAllLeaves();
        if (isMounted && Array.isArray(leaveList)) setLeaveRequests(leaveList);
      } catch (err) {
        console.warn('API getAllLeaves unavailable:', err.message);
      }

      // 8. High Risk Students
      try {
        const riskList = await api.getHighRiskStudents();
        if (isMounted && Array.isArray(riskList)) setRiskStudents(riskList);
      } catch (err) {
        console.warn('API getHighRiskStudents unavailable:', err.message);
      }

      // 9. Mark Corrections
      try {
        const correctionList = await api.getPendingCorrections();
        if (isMounted && Array.isArray(correctionList)) setMarkCorrections(correctionList);
      } catch (err) {
        console.warn('API getPendingCorrections unavailable:', err.message);
      }
    }

    loadBackendData();

    return () => {
      isMounted = false;
    };
  }, []);

  // Modals & Drawers
  const [isAuditModalOpen, setIsAuditModalOpen] = useState(false);
  const [isChatbotOpen, setIsChatbotOpen] = useState(false);

  // Authenticated Staff Session (stored in localStorage)
  const [staffSession, setStaffSession] = useState(() => getStoredStaffSession());

  // URL-based view routing state (?view=...)
  const [activeUrlView, setActiveUrlView] = useState(() => {
    if (typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      return params.get('view') || null;
    }
    return null;
  });

  const [activeUrlToken, setActiveUrlToken] = useState(() => {
    if (typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      return params.get('token') || '';
    }
    return '';
  });

  // Synchronize URL changes with browser history and popstate events
  useEffect(() => {
    const handlePopState = () => {
      const params = new URLSearchParams(window.location.search);
      setActiveUrlView(params.get('view') || null);
      setActiveUrlToken(params.get('token') || '');
      let p = window.location.pathname;
      if (p === '/hod/login') {
        window.history.replaceState({}, '', '/staff/login?role=HOD');
        p = '/staff/login';
      }
      if (p === '/principal' || p === '/principal/login') {
        p = '/admin/login';
      }
      if (p === '/staff') {
        p = '/staff/login';
      }
      if (p === '/student') {
        p = '/student/login';
      }
      if (p === '/public') {
        p = '/verify';
      }
      setCurrentPath(p);

      if (p === '/admin/login') setCurrentRole('ADMIN');
      else if (p === '/staff/login') setCurrentRole(getStoredStaffSession()?.user?.role || 'STAFF');
      else if (p === '/student/login') setCurrentRole('STUDENT');
      else if (p === '/verify') setCurrentRole('PUBLIC');
      else if (p === '/') setCurrentRole('LANDING');
    };

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const navigateToView = (viewName, extraParams = {}) => {
    if (typeof window !== 'undefined') {
      const url = new URL(window.location.href);
      if (viewName) {
        url.searchParams.set('view', viewName);
        Object.entries(extraParams).forEach(([k, v]) => {
          if (v) url.searchParams.set(k, v);
          else url.searchParams.delete(k);
        });
      } else {
        url.searchParams.delete('view');
        url.searchParams.delete('token');
      }
      window.history.pushState({}, '', url.toString());
      setActiveUrlView(viewName || null);
      setActiveUrlToken(extraParams.token || '');
    }
  };

  const navigateToPath = (pathname, search = '') => {
    if (typeof window !== 'undefined') {
      window.history.pushState({}, '', pathname + (search ? (search.startsWith('?') ? search : `?${search}`) : ''));
      setCurrentPath(pathname);
      setActiveUrlView(null);
    }
  };

  const handleSelectPortalRole = (roleKey) => {
    if (roleKey === 'ADMIN') {
      navigateToPath('/admin/login');
      setCurrentRole('ADMIN');
    } else if (roleKey === 'STAFF') {
      navigateToPath('/staff/login');
      setCurrentRole(staffSession?.user?.role || 'STAFF');
    } else if (roleKey === 'STUDENT') {
      navigateToPath('/student/login');
      setCurrentRole('STUDENT');
    } else if (roleKey === 'PUBLIC') {
      navigateToPath('/verify');
      setCurrentRole('PUBLIC');
    }
  };

  const handleNavigateHome = () => {
    navigateToPath('/');
    setCurrentRole('LANDING');
  };

  // Synchronize HTML Theme attribute
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  // Current active student session (only when a valid authenticated student session exists)
  const currentStudent = (authenticatedUser && authenticatedUser.role === 'STUDENT' && authenticatedUser.status !== 'PENDING')
    ? (() => {
        const found = users.find(u => u.id === authenticatedUser.id || u.email === authenticatedUser.email);
        const resolved = found ? { ...found, ...authenticatedUser } : authenticatedUser;
        return {
          ...resolved,
          status: resolved.status || authenticatedUser.status || 'PENDING',
          faceEnrolled: resolved.faceEnrolled !== undefined ? resolved.faceEnrolled : Boolean(authenticatedUser.faceEnrolled)
        };
      })()
    : null;

  // Active attendance session
  const activeSession = sessions.find(s => s.status === 'OPEN') || null;

  // Student Notice Feed & Live Read Receipts
  const [studentNotices, setStudentNotices] = useState(() => INITIAL_NOTICES);
  const [isLoadingStudentNotices, setIsLoadingStudentNotices] = useState(false);
  const [studentNoticeError, setStudentNoticeError] = useState(null);

  const getAudienceFilteredNotices = (noticeList, student) => {
    if (!student) return noticeList;
    return noticeList.filter(n => {
      if (n.targetStudentIds && n.targetStudentIds.length > 0) {
        return n.targetStudentIds.includes(student.id) || n.targetStudentIds.includes(Number(student.id));
      }
      const matchDept = !n.targetDepartment || n.targetDepartment === 'All Departments' || n.targetDepartment === student.department;
      const matchYear = !n.targetYear || n.targetYear === 'All Years' || n.targetYear === student.year;
      const matchDiv = !n.targetDivision || n.targetDivision === 'All Divisions' || n.targetDivision === student.division;
      return matchDept && matchYear && matchDiv;
    });
  };

  const fetchStudentNotices = async () => {
    const studentId = currentStudent?.id || (authenticatedUser?.role === 'STUDENT' ? authenticatedUser?.id : null);
    if (!studentId) {
      setStudentNotices(notices);
      return;
    }

    setIsLoadingStudentNotices(true);
    setStudentNoticeError(null);
    try {
      const feed = await api.getMyNoticeFeed(studentId);
      if (Array.isArray(feed) && feed.length > 0) {
        setStudentNotices(feed);
      } else {
        setStudentNotices(getAudienceFilteredNotices(notices, currentStudent));
      }
    } catch (err) {
      console.warn('Live notice feed unavailable, falling back to local dataset:', err);
      setStudentNotices(getAudienceFilteredNotices(notices, currentStudent));
    } finally {
      setIsLoadingStudentNotices(false);
    }
  };

  useEffect(() => {
    if (currentRole === 'STUDENT' && (currentStudent?.id || authenticatedUser?.id)) {
      fetchStudentNotices();
    } else {
      setStudentNotices(notices);
    }
  }, [currentRole, currentStudent?.id, currentStudent?.department, currentStudent?.year, currentStudent?.division, notices]);

  const handleMarkNoticeRead = async (noticeId) => {
    const studentId = currentStudent?.id || (authenticatedUser?.role === 'STUDENT' ? authenticatedUser?.id : null);
    await api.markNoticeRead(noticeId, studentId);
    setStudentNotices(prev => prev.map(n =>
      n.id === noticeId ? { ...n, read: true, readsCount: (n.readsCount || 0) + 1 } : n
    ));
    setNotices(prev => prev.map(n =>
      n.id === noticeId ? { ...n, read: true, readsCount: (n.readsCount || 0) + 1 } : n
    ));
  };

  const unreadNoticeCount = currentRole === 'STUDENT'
    ? studentNotices.filter(n => !n.read).length
    : notices.filter(n => !n.read).length;

  // Append entry to Cryptographic Hash Chain
  const handleAddAuditLog = async ({ action, entityType, entityId, oldValue, newValue, reason }) => {
    const lastEntry = auditLogs[auditLogs.length - 1];
    const prevHash = lastEntry ? lastEntry.entryHash : '0000000000000000000000000000000000000000000000000000000000000000';
    const createdAt = new Date().toISOString();
    const newId = (lastEntry ? lastEntry.id : 0) + 1;

    const actorName = staffSession?.user?.name || authenticatedUser?.name || 'Authorized Staff';
    const actorId = staffSession?.user?.id || authenticatedUser?.id || 1;

    const partialEntry = {
      id: newId,
      prevHash,
      actorId,
      actorName,
      action,
      entityType,
      entityId,
      oldValue: oldValue || '{}',
      newValue: newValue || '{}',
      reason,
      createdAt
    };

    const entryHash = await computeAuditEntryHash(partialEntry);
    const completedBlock = { ...partialEntry, entryHash };

    setAuditLogs(prev => [...prev, completedBlock]);
  };

  // Auth Lifecycle Handlers
  const handleLoginSuccess = (authData) => {
    if (authData && authData.user) {
      setAuthenticatedUser(authData.user);
      setCurrentRole(authData.user.role || 'STUDENT');
      setAuthNotification({ type: 'success', message: `Welcome back, ${authData.user.name}!` });
      setTimeout(() => setAuthNotification(null), 4000);
    }
  };

  const handleRegisterSuccess = (authData) => {
    if (authData && authData.user) {
      const backendStatus = authData.user.status || (authData.student?.faceEnrolled ? 'ACTIVE' : 'PENDING');
      const backendFaceEnrolled = authData.student?.faceEnrolled !== undefined
        ? Boolean(authData.student.faceEnrolled)
        : (authData.user?.faceEnrolled !== undefined ? Boolean(authData.user.faceEnrolled) : (backendStatus === 'ACTIVE'));

      const newUser = {
        id: authData.user.id || (users.length + 1),
        userCode: authData.user.userCode,
        name: authData.user.name,
        email: authData.user.email,
        role: 'STUDENT',
        department: authData.user.department || 'Computer Science',
        status: backendStatus,
        rollNo: authData.student?.rollNo || 'CS-NEW',
        division: authData.student?.division || 'Div A',
        year: authData.student?.year || 'Year 3',
        attendanceRate: 0.0,
        faceEnrolled: backendFaceEnrolled
      };

      setUsers(prev => {
        const idx = prev.findIndex(u => u.id === newUser.id || u.email === newUser.email);
        if (idx >= 0) {
          const updated = [...prev];
          updated[idx] = { ...updated[idx], ...newUser };
          return updated;
        }
        return [...prev, newUser];
      });

      // Do not auto-login before face enrollment succeeds (status must be ACTIVE with faceEnrolled)
      if (backendStatus === 'ACTIVE' && backendFaceEnrolled) {
        setAuthenticatedUser(newUser);
        setCurrentRole('STUDENT');
        setAuthNotification({ type: 'success', message: `Registration & Face ID enrollment complete! Welcome, ${newUser.name}.` });
      } else {
        setAuthNotification({ type: 'info', message: 'Registration submitted (status: PENDING). Complete facial enrollment before logging in.' });
      }
      setTimeout(() => setAuthNotification(null), 5000);
    }
  };

  const handleLogout = async () => {
    try {
      await api.logout();
    } catch (err) {
      console.warn('Logout API call:', err);
    } finally {
      clearAuthSession();
      setAuthenticatedUser(null);
      setAuthNotification({ type: 'info', message: 'You have been securely signed out. Session token invalidated.' });
      setTimeout(() => setAuthNotification(null), 4000);
    }
  };

  const handleStaffLogout = async () => {
    if (staffSession) {
      await logoutStaffApi(staffSession.refreshToken, staffSession.accessToken || staffSession.token);
    }
    clearStaffSession();
    setStaffSession(null);
    navigateToView(null);
  };

  return (
    <div className="app-wrapper">
      {/* Global Navigation Header */}
      <Navbar
        currentPath={currentPath}
        currentRole={currentRole}
        onNavigateHome={handleNavigateHome}
        theme={theme}
        setTheme={setTheme}
        activeSession={activeSession}
        staffSession={staffSession}
        notificationCount={unreadNoticeCount}
        unreadCount={unreadNoticeCount}
        notices={currentRole === 'STUDENT' ? studentNotices : notices}
        isLoadingNotices={isLoadingStudentNotices}
        noticeError={studentNoticeError}
        onMarkNoticeRead={handleMarkNoticeRead}
        onRefreshNotices={fetchStudentNotices}
        onViewAllNotices={() => {
          if (currentRole !== 'STUDENT') {
            handleSelectPortalRole('STUDENT');
          }
        }}
        currentUser={currentStudent || authenticatedUser}
        onLogout={
          (staffSession?.user?.role === 'ADMIN' || ['HOD', 'FACULTY', 'MENTOR'].includes(staffSession?.user?.role))
            ? handleStaffLogout
            : (currentStudent || authenticatedUser)
              ? handleLogout
              : null
        }
      />

      {/* Global Auth Status Notification Toast */}
      {authNotification && (
        <div style={{
          position: 'fixed',
          top: '75px',
          right: '25px',
          zIndex: 9999,
          background: authNotification.type === 'success' ? '#065f46' : '#1e3a8a',
          color: '#ffffff',
          padding: '10px 18px',
          borderRadius: '8px',
          boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
          border: '1px solid rgba(255,255,255,0.2)',
          fontSize: '0.9rem',
          fontWeight: 600,
          display: 'flex',
          alignItems: 'center',
          gap: '10px'
        }}>
          <span>{authNotification.message}</span>
          <button
            onClick={() => setAuthNotification(null)}
            style={{ background: 'transparent', border: 'none', color: '#ffffff', cursor: 'pointer', fontWeight: 'bold' }}
          >
            ✕
          </button>
        </div>
      )}

      {/* Main Role-Based Workflow Viewport */}
      <main className="main-viewport">
        {/* URL-driven Reset Password View (?view=reset-password&token=...) */}
        {activeUrlView === 'reset-password' && (
          <StaffResetPasswordView
            initialToken={activeUrlToken}
            onNavigate={(targetRole) => {
              if (targetRole === 'ADMIN') {
                handleSelectPortalRole('ADMIN');
              } else {
                navigateToPath('/staff/login', `role=${targetRole}`);
                setCurrentRole(targetRole);
              }
            }}
          />
        )}

        {/* Route /: Role Portal Landing Page */}
        {currentPath === '/' && activeUrlView !== 'reset-password' && (
          <RolePortalLanding
            onSelectRole={handleSelectPortalRole}
            staffSession={staffSession}
            currentStudent={currentStudent}
          />
        )}

        {/* Dedicated Route: /admin/login (Principal Portal) */}
        {currentPath === '/admin/login' && activeUrlView !== 'reset-password' && (
          staffSession?.user?.role === 'ADMIN' ? (
            <AdminView
              users={users}
              setUsers={setUsers}
              rooms={rooms}
              setRooms={setRooms}
              subjects={subjects}
              setSubjects={setSubjects}
              notices={notices}
              setNotices={setNotices}
              issuedDocuments={issuedDocuments}
              setIssuedDocuments={setIssuedDocuments}
              knowledgeDocs={knowledgeDocs}
              setKnowledgeDocs={setKnowledgeDocs}
              onAddAuditLog={handleAddAuditLog}
              staffSession={staffSession}
              onLogout={handleStaffLogout}
            />
          ) : (
            <AdminAuthView
              onAuthSuccess={(session) => {
                setStaffSession(session);
                setCurrentRole('ADMIN');
                navigateToPath('/admin/login');
              }}
              noticeMessage={staffSession ? "Principal access required" : ""}
            />
          )
        )}

        {/* Dedicated Route: /staff/login (Staff Portal) */}
        {currentPath === '/staff/login' && activeUrlView !== 'reset-password' && (
          (() => {
            const role = staffSession?.user?.role;
            if (role === 'HOD') {
              return (
                <HodView
                  leaveRequests={leaveRequests}
                  setLeaveRequests={setLeaveRequests}
                  markCorrections={markCorrections}
                  setMarkCorrections={setMarkCorrections}
                  marks={marks}
                  setMarks={setMarks}
                  users={users}
                  onAddAuditLog={handleAddAuditLog}
                  staffSession={staffSession}
                  onLogout={handleStaffLogout}
                  notices={notices}
                  setNotices={setNotices}
                />
              );
            }
            if (role === 'FACULTY') {
              return (
                <FacultyView
                  sessions={sessions}
                  setSessions={setSessions}
                  subjects={subjects}
                  rooms={rooms}
                  users={users}
                  marks={marks}
                  setMarks={setMarks}
                  markCorrections={markCorrections}
                  setMarkCorrections={setMarkCorrections}
                  onAddAuditLog={handleAddAuditLog}
                  notices={notices}
                  setNotices={setNotices}
                  staffSession={staffSession}
                  onLogout={handleStaffLogout}
                />
              );
            }
            if (role === 'MENTOR') {
              return (
                <MentorView
                  riskStudents={riskStudents}
                  setRiskStudents={setRiskStudents}
                  leaveRequests={leaveRequests}
                  setLeaveRequests={setLeaveRequests}
                  users={users}
                  onAddAuditLog={handleAddAuditLog}
                  notices={notices}
                  setNotices={setNotices}
                  staffSession={staffSession}
                  onLogout={handleStaffLogout}
                />
              );
            }
            return (
              <StaffAuthView
                initialRole={new URLSearchParams(window.location.search).get('role') || 'FACULTY'}
                onAuthSuccess={(session) => {
                  setStaffSession(session);
                  setCurrentRole(session.user.role);
                  navigateToPath('/staff/login');
                }}
                noticeMessage=""
              />
            );
          })()
        )}

        {/* Dedicated Route: /student/login (Student Portal) */}
        {currentPath === '/student/login' && activeUrlView !== 'reset-password' && (
          currentStudent ? (
            <StudentView
              currentUser={currentStudent}
              activeSession={activeSession}
              sessions={sessions}
              setSessions={setSessions}
              marks={marks}
              leaveRequests={leaveRequests}
              setLeaveRequests={setLeaveRequests}
              notices={studentNotices}
              onMarkNoticeRead={handleMarkNoticeRead}
              onRefreshNotices={fetchStudentNotices}
              issuedDocuments={issuedDocuments}
              onOpenChat={() => setIsChatbotOpen(true)}
              onAddAuditLog={handleAddAuditLog}
              onLogout={handleLogout}
            />
          ) : (
            <StudentAuthView
              onLoginSuccess={(authData) => {
                handleLoginSuccess(authData);
                navigateToPath('/student/login');
              }}
              onRegisterSuccess={handleRegisterSuccess}
            />
          )
        )}

        {/* Dedicated Route: /verify (Public Document Verifier) */}
        {currentPath === '/verify' && activeUrlView !== 'reset-password' && (
          <PublicVerifierView
            issuedDocuments={issuedDocuments}
          />
        )}

        {/* Fallback Viewport for non-standard routes */}
        {currentPath !== '/' &&
         currentPath !== '/admin/login' &&
         currentPath !== '/staff/login' &&
         currentPath !== '/student/login' &&
         currentPath !== '/verify' &&
         activeUrlView !== 'reset-password' && (
          <RolePortalLanding
            onSelectRole={handleSelectPortalRole}
            staffSession={staffSession}
            currentStudent={currentStudent}
          />
        )}
      </main>

      {/* Cryptographic SHA-256 Audit Chain Inspector Modal */}
      {isAuditModalOpen && (
        <AuditChainModal
          auditLogs={auditLogs}
          setAuditLogs={setAuditLogs}
          onClose={() => setIsAuditModalOpen(false)}
        />
      )}

      {/* Document-Grounded RAG Knowledge Chatbot Drawer */}
      <ChatbotDrawer
        isOpen={isChatbotOpen}
        onClose={() => setIsChatbotOpen(false)}
        knowledgeDocs={knowledgeDocs}
      />
    </div>
  );
}
