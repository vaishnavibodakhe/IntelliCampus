import React, { useState, useEffect } from 'react';
import './App.css';
import Navbar from './components/Navbar';
import AdminView from './components/AdminView';
import HodView from './components/HodView';
import FacultyView from './components/FacultyView';
import MentorView from './components/MentorView';
import StudentView from './components/StudentView';
import PublicVerifierView from './components/PublicVerifierView';
import AuditChainModal from './components/AuditChainModal';
import ChatbotDrawer from './components/ChatbotDrawer';

import LoginPage from './components/auth/LoginPage';
import RegisterPage from './components/auth/RegisterPage';
import ForgotPasswordPage from './components/auth/ForgotPasswordPage';
import ResetPasswordPage from './components/auth/ResetPasswordPage';
import RoleRoute from './components/auth/RoleRoute';
import UnauthorizedPage from './components/auth/UnauthorizedPage';
import { AuthProvider, useAuth } from './context/AuthContext';

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

function MainAppContent() {
  const { currentUser, isAuthenticated, isAuthLoading, logout, navigate, currentPath } = useAuth();

  // If user is logged in, default to their dashboard role; otherwise default to STUDENT (public tab)
  const [currentRole, setCurrentRole] = useState(() => {
    if (currentUser?.role === 'FACULTY') return 'FACULTY';
    if (currentUser?.role === 'MENTOR') return 'MENTOR';
    return 'STUDENT';
  });
  const [theme, setTheme] = useState('dark');

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

  // Current active student (for Student Role simulation)
  const currentStudent = users.find(u => u.userCode === 'student.aarav') || users[4];

  // Active attendance session
  const activeSession = sessions.find(s => s.status === 'OPEN') || null;

  // Append entry to Cryptographic Hash Chain
  const handleAddAuditLog = async ({ action, entityType, entityId, oldValue, newValue, reason }) => {
    const lastEntry = auditLogs[auditLogs.length - 1];
    const prevHash = lastEntry ? lastEntry.entryHash : '0000000000000000000000000000000000000000000000000000000000000000';
    const createdAt = new Date().toISOString();
    const newId = (lastEntry ? lastEntry.id : 0) + 1;

    const partialEntry = {
      id: newId,
      prevHash,
      actorId: currentUser?.id || 1,
      actorName: currentUser?.name || 'Authorized User',
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

  // Centralized workflow switcher handler enforcing auth redirection for Faculty & Mentor
  const handleWorkflowRoleChange = (selectedRole) => {
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

    // Unauthenticated/public workflow roles: ADMIN, HOD, STUDENT, PUBLIC
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
  // AUTH ROUTES DISPATCHER
  // =========================================================================
  if (pathname === '/faculty/login') {
    return (
      <LoginPage
        initialRole="FACULTY"
        onLoginSuccess={(data) => {
          navigate('/faculty/dashboard');
        }}
        onNavigateRegister={() => navigate('/faculty/register')}
        onNavigateForgotPassword={() => navigate('/forgot-password?role=FACULTY')}
      />
    );
  }

  if (pathname === '/mentor/login') {
    return (
      <LoginPage
        initialRole="MENTOR"
        onLoginSuccess={(data) => {
          navigate('/mentor/dashboard');
        }}
        onNavigateRegister={() => navigate('/mentor/register')}
        onNavigateForgotPassword={() => navigate('/forgot-password?role=MENTOR')}
      />
    );
  }

  if (pathname === '/faculty/register') {
    return (
      <RegisterPage
        initialRole="FACULTY"
        onRegisterSuccess={() => navigate('/faculty/login')}
        onNavigateLogin={() => navigate('/faculty/login')}
      />
    );
  }

  if (pathname === '/mentor/register') {
    return (
      <RegisterPage
        initialRole="MENTOR"
        onRegisterSuccess={() => navigate('/mentor/login')}
        onNavigateLogin={() => navigate('/mentor/login')}
      />
    );
  }

  if (pathname === '/forgot-password') {
    return (
      <ForgotPasswordPage
        onNavigateLogin={(role) => navigate(role === 'MENTOR' ? '/mentor/login' : '/faculty/login')}
      />
    );
  }

  if (pathname === '/reset-password') {
    return <ResetPasswordPage />;
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

      {/* Main Role-Based Workflow Viewport */}
      <main className="main-viewport">
        {currentRole === 'ADMIN' && (
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
          />
        )}

        {currentRole === 'HOD' && (
          <HodView 
            leaveRequests={leaveRequests}
            setLeaveRequests={setLeaveRequests}
            markCorrections={markCorrections}
            setMarkCorrections={setMarkCorrections}
            marks={marks}
            setMarks={setMarks}
            users={users}
            onAddAuditLog={handleAddAuditLog}
          />
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
          />
        )}

        {currentRole === 'PUBLIC' && (
          <PublicVerifierView 
            issuedDocuments={issuedDocuments}
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

export default function App() {
  return (
    <AuthProvider>
      <MainAppContent />
    </AuthProvider>
  );
}
