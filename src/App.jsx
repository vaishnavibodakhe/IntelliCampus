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
import StudentLogin from './components/StudentLogin';
import StudentRegister from './components/StudentRegister';

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
  const [currentRole, setCurrentRole] = useState('FACULTY');
  const [theme, setTheme] = useState('dark');

  // Authentication State (JWT & User Session)
  const [authenticatedUser, setAuthenticatedUser] = useState(() => getStoredUser());
  const [authMode, setAuthMode] = useState('NONE'); // 'NONE' | 'LOGIN' | 'REGISTER'
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

  // Synchronize HTML Theme attribute
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  // Current active student (for Student Role simulation)
  const currentStudent = (authenticatedUser && authenticatedUser.role === 'STUDENT')
    ? (users.find(u => u.id === authenticatedUser.id || u.email === authenticatedUser.email) || authenticatedUser)
    : (users.find(u => u.userCode === 'student.aarav') || users[4]);

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
      actorId: 3,
      actorName: 'Prof. Vikram Verma',
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
      setAuthMode('NONE');
      setCurrentRole(authData.user.role || 'STUDENT');
      setAuthNotification({ type: 'success', message: `Welcome back, ${authData.user.name}!` });
      setTimeout(() => setAuthNotification(null), 4000);
    }
  };

  const handleRegisterSuccess = (authData) => {
    if (authData && authData.user) {
      const newUser = {
        id: authData.user.id || (users.length + 1),
        userCode: authData.user.userCode,
        name: authData.user.name,
        email: authData.user.email,
        role: 'STUDENT',
        department: authData.user.department || 'Computer Science',
        status: 'ACTIVE',
        rollNo: authData.student?.rollNo || 'CS-NEW',
        division: authData.student?.division || 'Div A',
        year: authData.student?.year || 'Year 3',
        attendanceRate: 0.0,
        faceEnrolled: false
      };

      setUsers(prev => [...prev, newUser]);
      setAuthenticatedUser(newUser);
      setAuthMode('NONE');
      setCurrentRole('STUDENT');
      setAuthNotification({ type: 'success', message: `Registration complete! Welcome, ${newUser.name}.` });
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
      setAuthMode('LOGIN');
      setAuthNotification({ type: 'info', message: 'You have been securely signed out. Session token invalidated.' });
      setTimeout(() => setAuthNotification(null), 4000);
    }
  };

  return (
    <div className="app-wrapper">
      {/* Global Navigation Header */}
      <Navbar 
        currentRole={currentRole}
        setCurrentRole={setCurrentRole}
        theme={theme}
        setTheme={setTheme}
        onOpenAudit={() => setIsAuditModalOpen(true)}
        onOpenChat={() => setIsChatbotOpen(true)}
        activeSession={activeSession}
        notificationCount={notices.length}
        authenticatedUser={authenticatedUser}
        onLogout={handleLogout}
        onOpenAuth={() => setAuthMode('LOGIN')}
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
          />
        )}

        {currentRole === 'MENTOR' && (
          <MentorView 
            riskStudents={riskStudents}
            setRiskStudents={setRiskStudents}
            leaveRequests={leaveRequests}
            setLeaveRequests={setLeaveRequests}
            users={users}
            onAddAuditLog={handleAddAuditLog}
          />
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

      {/* Student Authentication Modal (Registration, Login & Session) */}
      {authMode !== 'NONE' && (
        <div 
          className="modal-backdrop" 
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.75)',
            backdropFilter: 'blur(8px)',
            zIndex: 1000,
            overflowY: 'auto',
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            padding: '2rem 1rem'
          }}
          onClick={(e) => {
            if (e.target === e.currentTarget) {
              setAuthMode('NONE');
            }
          }}
        >
          <div style={{ position: 'relative', width: '100%', maxWidth: '760px' }}>
            <button
              onClick={() => setAuthMode('NONE')}
              style={{
                position: 'absolute',
                top: '12px',
                right: '12px',
                background: 'rgba(255, 255, 255, 0.1)',
                border: 'none',
                borderRadius: '50%',
                width: '32px',
                height: '32px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#ffffff',
                cursor: 'pointer',
                zIndex: 10,
                fontSize: '1.1rem'
              }}
              title="Close Authentication Dialog"
            >
              ✕
            </button>

            {authMode === 'LOGIN' && (
              <StudentLogin 
                onSwitchToRegister={() => setAuthMode('REGISTER')}
                onLoginSuccess={handleLoginSuccess}
              />
            )}

            {authMode === 'REGISTER' && (
              <StudentRegister 
                onSwitchToLogin={() => setAuthMode('LOGIN')}
                onRegisterSuccess={handleRegisterSuccess}
              />
            )}
          </div>
        </div>
      )}
    </div>
  );
}
