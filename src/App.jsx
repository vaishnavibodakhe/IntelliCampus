import React, { useState, useEffect } from 'react';
import './App.css';
import Navbar from './components/Navbar';
import AdminView from './components/AdminView';
import HodView from './components/HodView';
import FacultyView from './components/FacultyView';
import MentorView from './components/MentorView';
import StudentView from './components/StudentView';
import StudentAuthView from './components/StudentAuthView';
import PublicVerifierView from './components/PublicVerifierView';
import AuditChainModal from './components/AuditChainModal';
import ChatbotDrawer from './components/ChatbotDrawer';

import LoginPage from './components/auth/LoginPage';
import RegisterPage from './components/auth/RegisterPage';
import ForgotPasswordPage from './components/auth/ForgotPasswordPage';
import ResetPasswordPage from './components/auth/ResetPasswordPage';
import RoleRoute from './components/auth/RoleRoute';
import UnauthorizedPage from './components/auth/UnauthorizedPage';
import PortalSelectionCards from './components/PortalSelectionCards';
import { AuthProvider, useAuth } from './context/AuthContext';

import AdminAuthView from './components/staff/AdminAuthView';
import HodAuthView from './components/staff/HodAuthView';
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

function MainAppContent() {
  const { currentUser, isAuthenticated, isAuthLoading, logout, navigate, currentPath } = useAuth();

  // If user is logged in, default to their dashboard role; otherwise default to STUDENT (public tab)
  const [currentRole, setCurrentRole] = useState(() => {
    if (currentUser?.role === 'FACULTY') return 'FACULTY';
    if (currentUser?.role === 'MENTOR') return 'MENTOR';
    return 'STUDENT';
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

  // Synchronize HTML Theme attribute
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  // Synchronize active role when logged in user changes
  useEffect(() => {
    if (currentUser?.role) {
      setCurrentRole(currentUser.role);
    }
  }, [currentUser]);

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

  // Append entry to Cryptographic Hash Chain
  const handleAddAuditLog = async ({ action, entityType, entityId, oldValue, newValue, reason }) => {
    const lastEntry = auditLogs[auditLogs.length - 1];
    const prevHash = lastEntry ? lastEntry.entryHash : '0000000000000000000000000000000000000000000000000000000000000000';
    const createdAt = new Date().toISOString();
    const newId = (lastEntry ? lastEntry.id : 0) + 1;

    const actorName = staffSession?.user?.name || currentUser?.name || 'Authorized User';
    const actorId = staffSession?.user?.id || currentUser?.id || 1;

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

  // Auth Lifecycle Handlers for Student & Staff
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

  // Centralized workflow switcher handler enforcing auth redirection for Faculty & Mentor
  const handleWorkflowRoleChange = (selectedRole) => {
    navigateToView(null);

    if (selectedRole === 'FACULTY') {
      if (!isAuthenticated) {
        navigate('/faculty/login');
        return;
      }
      if (currentUser?.role !== 'FACULTY') {
        navigate('/faculty/dashboard'); // RoleRoute will catch and show Unauthorized
        return;
      }
      setCurrentRole('FACULTY');
      navigate('/faculty/dashboard');
      return;
    }

    if (selectedRole === 'MENTOR') {
      if (!isAuthenticated) {
        navigate('/mentor/login');
        return;
      }
      if (currentUser?.role !== 'MENTOR') {
        navigate('/mentor/dashboard'); // RoleRoute will catch and show Unauthorized
        return;
      }
      setCurrentRole('MENTOR');
      navigate('/mentor/dashboard');
      return;
    }

    // Workflow roles: ADMIN, HOD, STUDENT, PUBLIC
    setCurrentRole(selectedRole);
    const currentBase = (currentPath || window.location.pathname).split('?')[0];
    if (currentBase !== '/') {
      navigate('/');
    }
  };

  // Show clean loading spinner while authentication status is being restored
  if (isAuthLoading) {
    return (
      <div className="auth-viewport" style={{ minHeight: '100vh' }}>
        <div className="auth-ambient-glow faculty" />
        <div className="auth-card-container" style={{ textAlign: 'center', padding: '3.5rem' }}>
          <div className="loading-spinner" style={{ margin: '0 auto 1.5rem', width: '38px', height: '38px' }} />
          <h2 style={{ fontSize: '1.15rem', color: 'var(--text-secondary)' }}>Verifying IntelliCampus session...</h2>
        </div>
      </div>
    );
  }

  // Determine pathname without query string
  const pathname = (currentPath || window.location.pathname).split('?')[0];

  // =========================================================================
  // FACULTY AUTHENTICATION ROUTES
  // =========================================================================
  if (pathname === '/faculty/login') {
    return (
      <LoginPage
        role="FACULTY"
        onLoginSuccess={() => navigate('/faculty/dashboard')}
        onNavigateRegister={() => navigate('/faculty/register')}
        onNavigateForgotPassword={() => navigate('/faculty/forgot-password')}
      />
    );
  }

  if (pathname === '/faculty/register') {
    return (
      <RegisterPage
        role="FACULTY"
        onRegisterSuccess={() => navigate('/faculty/login')}
        onNavigateLogin={() => navigate('/faculty/login')}
      />
    );
  }

  if (pathname === '/faculty/forgot-password') {
    return (
      <ForgotPasswordPage
        role="FACULTY"
        onNavigateLogin={() => navigate('/faculty/login')}
      />
    );
  }

  if (pathname === '/faculty/reset-password' || pathname.startsWith('/faculty/reset-password/')) {
    return <ResetPasswordPage role="FACULTY" />;
  }

  // =========================================================================
  // MENTOR AUTHENTICATION ROUTES
  // =========================================================================
  if (pathname === '/mentor/login') {
    return (
      <LoginPage
        role="MENTOR"
        onLoginSuccess={() => navigate('/mentor/dashboard')}
        onNavigateRegister={() => navigate('/mentor/register')}
        onNavigateForgotPassword={() => navigate('/mentor/forgot-password')}
      />
    );
  }

  if (pathname === '/mentor/register') {
    return (
      <RegisterPage
        role="MENTOR"
        onRegisterSuccess={() => navigate('/mentor/login')}
        onNavigateLogin={() => navigate('/mentor/login')}
      />
    );
  }

  if (pathname === '/mentor/forgot-password') {
    return (
      <ForgotPasswordPage
        role="MENTOR"
        onNavigateLogin={() => navigate('/mentor/login')}
      />
    );
  }

  if (pathname === '/mentor/reset-password' || pathname.startsWith('/mentor/reset-password/')) {
    return <ResetPasswordPage role="MENTOR" />;
  }

  // Fallback and Unauthorized Routes
  if (pathname === '/forgot-password') {
    const isMentor = (new URLSearchParams(window.location.search).get('role') || '').toUpperCase() === 'MENTOR';
    return (
      <ForgotPasswordPage
        role={isMentor ? 'MENTOR' : 'FACULTY'}
        onNavigateLogin={() => navigate(isMentor ? '/mentor/login' : '/faculty/login')}
      />
    );
  }

  if (pathname === '/reset-password') {
    const isMentor = (new URLSearchParams(window.location.search).get('role') || '').toUpperCase() === 'MENTOR';
    return <ResetPasswordPage role={isMentor ? 'MENTOR' : 'FACULTY'} />;
  }

  if (pathname === '/unauthorized') {
    return <UnauthorizedPage />;
  }

  // =========================================================================
  // PROTECTED DASHBOARD ROUTES
  // =========================================================================
  if (pathname === '/faculty/dashboard') {
    return (
      <RoleRoute requiredRole="FACULTY">
        <div className="app-wrapper">
          <Navbar 
            currentRole="FACULTY"
            setCurrentRole={handleWorkflowRoleChange}
            theme={theme}
            setTheme={setTheme}
            onOpenAudit={() => setIsAuditModalOpen(true)}
            onOpenChat={() => setIsChatbotOpen(true)}
            activeSession={activeSession}
            notificationCount={notices.length}
            currentUser={currentUser}
            onLogout={logout}
          />
          <main className="main-viewport">
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
              currentUser={currentUser}
              onLogout={logout}
            />
          </main>
          {isAuditModalOpen && (
            <AuditChainModal 
              auditLogs={auditLogs}
              setAuditLogs={setAuditLogs}
              onClose={() => setIsAuditModalOpen(false)}
            />
          )}
          <ChatbotDrawer 
            isOpen={isChatbotOpen}
            onClose={() => setIsChatbotOpen(false)}
            knowledgeDocs={knowledgeDocs}
          />
        </div>
      </RoleRoute>
    );
  }

  if (pathname === '/mentor/dashboard') {
    return (
      <RoleRoute requiredRole="MENTOR">
        <div className="app-wrapper">
          <Navbar 
            currentRole="MENTOR"
            setCurrentRole={handleWorkflowRoleChange}
            theme={theme}
            setTheme={setTheme}
            onOpenAudit={() => setIsAuditModalOpen(true)}
            onOpenChat={() => setIsChatbotOpen(true)}
            activeSession={activeSession}
            notificationCount={notices.length}
            currentUser={currentUser}
            onLogout={logout}
          />
          <main className="main-viewport">
            <MentorView 
              riskStudents={riskStudents}
              setRiskStudents={setRiskStudents}
              leaveRequests={leaveRequests}
              setLeaveRequests={setLeaveRequests}
              users={users}
              onAddAuditLog={handleAddAuditLog}
              currentUser={currentUser}
              onLogout={logout}
            />
          </main>
          {isAuditModalOpen && (
            <AuditChainModal 
              auditLogs={auditLogs}
              setAuditLogs={setAuditLogs}
              onClose={() => setIsAuditModalOpen(false)}
            />
          )}
          <ChatbotDrawer 
            isOpen={isChatbotOpen}
            onClose={() => setIsChatbotOpen(false)}
            knowledgeDocs={knowledgeDocs}
          />
        </div>
      </RoleRoute>
    );
  }

  // =========================================================================
  // DEFAULT WORKSPACE VIEW
  // =========================================================================
  return (
    <div className="app-wrapper">
      {/* Global Navigation Header */}
      <Navbar 
        currentRole={currentRole}
        setCurrentRole={handleWorkflowRoleChange}
        theme={theme}
        setTheme={setTheme}
        onOpenAudit={() => setIsAuditModalOpen(true)}
        onOpenChat={() => setIsChatbotOpen(true)}
        activeSession={activeSession}
        notificationCount={notices.length}
        currentUser={currentUser}
        onLogout={logout}
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
              if (targetRole === 'ADMIN' || targetRole === 'HOD') {
                setCurrentRole(targetRole);
              }
              navigateToView(null);
            }}
          />
        )}

        {/* Regular Role Viewports when not on a specialized ?view= route */}
        {activeUrlView !== 'reset-password' && (
          <>
            {/* Institutional Portal Selection Cards for Faculty & Mentor if not signed in */}
            {!isAuthenticated && !staffSession && !currentStudent && (
              <PortalSelectionCards />
            )}

            {currentRole === 'ADMIN' && (
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
                  }}
                  noticeMessage={staffSession ? "Admin access required" : ""}
                  onSwitchRole={(targetRole) => setCurrentRole(targetRole)}
                />
              )
            )}

            {currentRole === 'HOD' && (
              staffSession?.user?.role === 'HOD' ? (
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
                />
              ) : (
                <HodAuthView 
                  onAuthSuccess={(session) => {
                    setStaffSession(session);
                  }}
                  noticeMessage={staffSession ? "HOD access required" : ""}
                  onSwitchRole={(targetRole) => setCurrentRole(targetRole)}
                />
              )
            )}

            {currentRole === 'FACULTY' && (
              <RoleRoute requiredRole="FACULTY">
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
                  currentUser={currentUser}
                  onLogout={logout}
                />
              </RoleRoute>
            )}

            {currentRole === 'MENTOR' && (
              <RoleRoute requiredRole="MENTOR">
                <MentorView 
                  riskStudents={riskStudents}
                  setRiskStudents={setRiskStudents}
                  leaveRequests={leaveRequests}
                  setLeaveRequests={setLeaveRequests}
                  users={users}
                  onAddAuditLog={handleAddAuditLog}
                  currentUser={currentUser}
                  onLogout={logout}
                />
              </RoleRoute>
            )}

            {currentRole === 'STUDENT' && (
              currentStudent ? (
                <StudentView 
                  currentUser={currentStudent}
                  activeSession={activeSession}
                  sessions={sessions}
                  setSessions={setSessions}
                  marks={marks}
                  leaveRequests={leaveRequests}
                  setLeaveRequests={setLeaveRequests}
                  notices={notices}
                  issuedDocuments={issuedDocuments}
                  onOpenChat={() => setIsChatbotOpen(true)}
                  onAddAuditLog={handleAddAuditLog}
                  onLogout={handleLogout}
                />
              ) : (
                <StudentAuthView 
                  onLoginSuccess={handleLoginSuccess}
                  onRegisterSuccess={handleRegisterSuccess}
                />
              )
            )}

            {currentRole === 'PUBLIC' && (
              <PublicVerifierView 
                issuedDocuments={issuedDocuments}
              />
            )}
          </>
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

export default function App() {
  return (
    <AuthProvider>
      <MainAppContent />
    </AuthProvider>
  );
}
