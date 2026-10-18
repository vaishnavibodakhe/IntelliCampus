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
import LoginView from './components/LoginView';
import { clearToken } from './api/client';

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

export default function App() {
  const [loggedInUser, setLoggedInUser] = useState(() => {
    try {
      const saved = sessionStorage.getItem('intellicampus_user');
      return saved ? JSON.parse(saved) : null;
    } catch (e) {
      console.warn('Failed to restore user from sessionStorage:', e);
      return null;
    }
  });

  const [currentRole, setCurrentRole] = useState(() => {
    try {
      const saved = sessionStorage.getItem('intellicampus_user');
      if (saved) {
        const parsed = JSON.parse(saved);
        if (parsed?.role) return parsed.role;
      }
    } catch (e) {
      // fallback
    }
    return 'LOGIN';
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

  // Synchronize loggedInUser to sessionStorage
  useEffect(() => {
    try {
      if (loggedInUser) {
        sessionStorage.setItem('intellicampus_user', JSON.stringify(loggedInUser));
      } else {
        sessionStorage.removeItem('intellicampus_user');
      }
    } catch (e) {
      console.warn('Failed to persist user in sessionStorage:', e);
    }
  }, [loggedInUser]);

  // Current active student (for Student Role simulation)
  const currentStudent = (loggedInUser && loggedInUser.role === 'STUDENT')
    ? loggedInUser
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

  const handleLogin = (user) => {
    setLoggedInUser(user);
    if (user?.role) {
      setCurrentRole(user.role);
    }
  };

  const handleLogout = () => {
    setLoggedInUser(null);
    try {
      sessionStorage.removeItem('intellicampus_user');
      clearToken();
    } catch (e) {
      console.warn('Failed to clear sessionStorage:', e);
    }
    setCurrentRole('LOGIN');
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
        loggedInUser={loggedInUser}
        onLogout={handleLogout}
        onLoginClick={() => setCurrentRole('LOGIN')}
      />

      {/* Main Role-Based Workflow Viewport */}
      <main className="main-viewport">
        {!loggedInUser ? (
          currentRole === 'PUBLIC' ? (
            <PublicVerifierView 
              issuedDocuments={issuedDocuments}
            />
          ) : (
            <LoginView 
              users={users}
              onLogin={handleLogin}
            />
          )
        ) : (
          <>
            {currentRole === 'PUBLIC' && (
              <PublicVerifierView 
                issuedDocuments={issuedDocuments}
              />
            )}

            {currentRole === 'ADMIN' && loggedInUser.role === 'ADMIN' && (
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

            {currentRole === 'HOD' && loggedInUser.role === 'HOD' && (
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

            {currentRole === 'FACULTY' && loggedInUser.role === 'FACULTY' && (
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

            {currentRole === 'MENTOR' && loggedInUser.role === 'MENTOR' && (
              <MentorView 
                riskStudents={riskStudents}
                setRiskStudents={setRiskStudents}
                leaveRequests={leaveRequests}
                setLeaveRequests={setLeaveRequests}
                users={users}
                onAddAuditLog={handleAddAuditLog}
              />
            )}

            {currentRole === 'STUDENT' && loggedInUser.role === 'STUDENT' && (
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
