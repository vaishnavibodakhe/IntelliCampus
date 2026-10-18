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
import { isStudentOnlyMode } from './utils/studentPortalConfig';

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
  const isStudentOnly = isStudentOnlyMode();

  // Separate session state for the Student Portal
  const [studentUser, setStudentUser] = useState(() => {
    try {
      const saved = sessionStorage.getItem('intellicampus_student_user');
      if (saved) {
        const parsed = JSON.parse(saved);
        if (parsed?.role === 'STUDENT') return parsed;
      }
      const legacy = sessionStorage.getItem('intellicampus_user');
      if (legacy) {
        const parsed = JSON.parse(legacy);
        if (parsed?.role === 'STUDENT') return parsed;
      }
      return null;
    } catch (e) {
      console.warn('Failed to restore studentUser from sessionStorage:', e);
      return null;
    }
  });

  // Separate session state for staff roles (Faculty, HOD, Admin, Mentor)
  const [staffUsers, setStaffUsers] = useState(() => {
    try {
      const saved = sessionStorage.getItem('intellicampus_staff_users');
      if (saved) return JSON.parse(saved);
      const legacy = sessionStorage.getItem('intellicampus_user');
      if (legacy) {
        const parsed = JSON.parse(legacy);
        if (parsed?.role && parsed.role !== 'STUDENT') {
          return { [parsed.role]: parsed };
        }
      }
      return {};
    } catch (e) {
      return {};
    }
  });

  const [currentRole, setCurrentRole] = useState(() => {
    if (isStudentOnly) return 'STUDENT';
    return 'FACULTY'; // Default active workflow in multi-role mode
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

  // Synchronize student session to sessionStorage
  useEffect(() => {
    try {
      if (studentUser) {
        sessionStorage.setItem('intellicampus_student_user', JSON.stringify(studentUser));
      } else {
        sessionStorage.removeItem('intellicampus_student_user');
      }
    } catch (e) {
      console.warn('Failed to persist studentUser in sessionStorage:', e);
    }
  }, [studentUser]);

  // Synchronize staff sessions to sessionStorage
  useEffect(() => {
    try {
      if (staffUsers && Object.keys(staffUsers).length > 0) {
        sessionStorage.setItem('intellicampus_staff_users', JSON.stringify(staffUsers));
      } else {
        sessionStorage.removeItem('intellicampus_staff_users');
      }
    } catch (e) {
      console.warn('Failed to persist staffUsers in sessionStorage:', e);
    }
  }, [staffUsers]);

  // Enforce student-only constraints when VITE_STUDENT_ONLY=true
  useEffect(() => {
    if (isStudentOnly) {
      if (staffUsers && Object.keys(staffUsers).length > 0) {
        setStaffUsers({});
        sessionStorage.removeItem('intellicampus_staff_users');
      }
      try {
        const legacy = sessionStorage.getItem('intellicampus_user');
        if (legacy) {
          const parsed = JSON.parse(legacy);
          if (parsed?.role !== 'STUDENT') {
            sessionStorage.removeItem('intellicampus_user');
            clearToken();
          }
        }
      } catch (e) {}
    }
  }, [isStudentOnly, staffUsers]);

  // Current active student (for Student Role simulation)
  const currentStudent = studentUser || (users.find(u => u.userCode === 'student.aarav') || users[4]);

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

  const handleStudentLogin = (user) => {
    if (user?.role === 'STUDENT') {
      setStudentUser(user);
    } else {
      // If a staff user authenticated through a login form, store in staff sessions
      setStaffUsers(prev => ({ ...prev, [user.role]: user }));
      setCurrentRole(user.role);
    }
  };

  const handleStudentLogout = () => {
    setStudentUser(null);
    try {
      sessionStorage.removeItem('intellicampus_student_user');
      const legacy = sessionStorage.getItem('intellicampus_user');
      if (legacy) {
        const parsed = JSON.parse(legacy);
        if (parsed?.role === 'STUDENT') {
          sessionStorage.removeItem('intellicampus_user');
          clearToken();
        }
      }
    } catch (e) {
      console.warn('Failed to clear student session:', e);
    }
  };

  const handleStaffLogout = (role) => {
    setStaffUsers(prev => {
      const updated = { ...prev };
      delete updated[role];
      return updated;
    });
  };

  const handleActiveLogout = () => {
    if (currentRole === 'STUDENT') {
      handleStudentLogout();
    } else {
      handleStaffLogout(currentRole);
    }
  };

  // The navbar user chip and Logout button appear only for the role that is currently open and logged in
  const activeUser = currentRole === 'STUDENT' ? studentUser : (staffUsers[currentRole] || null);

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
        loggedInUser={activeUser}
        onLogout={handleActiveLogout}
        onLoginClick={() => {
          if (currentRole !== 'STUDENT') {
            setCurrentRole('STUDENT');
          }
        }}
        studentOnly={isStudentOnly}
      />

      {/* Main Role-Based Workflow Viewport */}
      <main className="main-viewport">
        {isStudentOnly ? (
          !studentUser ? (
            <LoginView 
              users={users}
              onLogin={handleStudentLogin}
            />
          ) : (
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
              onLogout={handleStudentLogout}
            />
          )
        ) : (
          <>
            {currentRole === 'PUBLIC' && (
              <PublicVerifierView 
                issuedDocuments={issuedDocuments}
              />
            )}

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
              !studentUser ? (
                <LoginView 
                  users={users}
                  onLogin={handleStudentLogin}
                />
              ) : (
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
                  onLogout={handleStudentLogout}
                />
              )
            )}
          </>
        )}
      </main>

      {/* Cryptographic SHA-256 Audit Chain Inspector Modal (Disabled in student-only mode) */}
      {!isStudentOnly && isAuditModalOpen && (
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
