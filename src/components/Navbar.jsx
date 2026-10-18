import React from 'react';
import { 
  ShieldCheck, 
  UserCheck, 
  GraduationCap, 
  Users, 
  FileText, 
  QrCode, 
  Bell, 
  Link2, 
  Sun, 
  Moon, 
  Sparkles,
  BookOpen,
  LogOut,
  LogIn
} from 'lucide-react';

import { isStudentOnlyMode } from '../utils/studentPortalConfig';

function getInitials(name) {
  if (!name) return 'U';
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export default function Navbar({ 
  currentRole, 
  setCurrentRole, 
  theme, 
  setTheme, 
  onOpenAudit, 
  onOpenChat,
  activeSession,
  notificationCount = 2,
  loggedInUser = null,
  onLogout,
  onLoginClick,
  studentOnly = isStudentOnlyMode()
}) {
  const roles = [
    { id: 'ADMIN', label: 'Admin', icon: ShieldCheck, color: '#6366f1' },
    { id: 'HOD', label: 'HOD', icon: BookOpen, color: '#8b5cf6' },
    { id: 'FACULTY', label: 'Faculty', icon: UserCheck, color: '#06b6d4' },
    { id: 'MENTOR', label: 'Mentor', icon: Users, color: '#10b981' },
    { id: 'STUDENT', label: 'Student', icon: GraduationCap, color: '#f59e0b' },
    { id: 'PUBLIC', label: 'Public Verifier', icon: QrCode, color: '#ec4899' }
  ];

  return (
    <header className="navbar-container">
      <div className="navbar-brand">
        <div className="brand-logo-wrap">
          <svg className="brand-logo" viewBox="0 0 100 100">
            <defs>
              <linearGradient id="navGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stopColor="#6366F1" />
                <stop offset="100%" stopColor="#06B6D4" />
              </linearGradient>
            </defs>
            <rect width="100" height="100" rx="24" fill="url(#navGrad)" />
            <path d="M50 20 L18 36 L50 52 L82 36 Z" fill="#FFFFFF" />
            <path d="M28 45 L28 64 C28 72 50 78 50 78 C50 78 72 72 72 64 L72 45 L50 56 Z" fill="#E0E7FF" opacity="0.9" />
          </svg>
        </div>
        <div className="brand-text">
          <div className="brand-title">
            IntelliCampus
            <span className="brand-pill">v1.0</span>
          </div>
          <div className="brand-subtitle">Intelligent College Platform</div>
        </div>
      </div>

      {/* Role Workflow Switcher (Hidden in student-only mode) */}
      {!studentOnly && (
        <div className="role-switcher-container">
          <span className="role-switcher-label">Select Active Workflow:</span>
          <div className="role-pill-group">
            {roles.map(r => {
              const Icon = r.icon;
              const isActive = currentRole === r.id;
              return (
                <button
                  key={r.id}
                  onClick={() => setCurrentRole(r.id)}
                  className={`role-pill-btn ${isActive ? 'active' : ''}`}
                  style={{
                    '--accent': r.color
                  }}
                >
                  <Icon size={15} />
                  <span>{r.label}</span>
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* Action shortcuts */}
      <div className="navbar-actions">
        {/* Active Class Live Indicator (Staff feature: hidden in student-only mode) */}
        {!studentOnly && activeSession && activeSession.status === 'OPEN' && (
          <div className="live-session-badge animate-pulse-glow" title="Active attendance session ongoing">
            <span className="live-dot" />
            <span>Live: {activeSession.challengeCode}</span>
          </div>
        )}

        {/* Notice Broadcast Alerts Indicator */}
        <div className="action-icon-btn notice-bell-btn" title={`${notificationCount} Active Campus Notices`} style={{ position: 'relative' }}>
          <Bell size={17} />
          {notificationCount > 0 && (
            <span className="badge-notification-count" style={{
              position: 'absolute',
              top: '-4px',
              right: '-4px',
              background: '#ef4444',
              color: '#ffffff',
              borderRadius: '9999px',
              fontSize: '0.65rem',
              fontWeight: 700,
              minWidth: '16px',
              height: '16px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '0 3px'
            }}>
              {notificationCount}
            </span>
          )}
        </div>

        {/* Cryptographic Audit Hash Chain Inspector (Staff feature: hidden in student-only mode) */}
        {!studentOnly && (
          <button 
            onClick={onOpenAudit}
            className="action-icon-btn audit-btn"
            title="Cryptographic SHA-256 Audit Chain Inspector"
          >
            <Link2 size={17} />
            <span className="btn-label">Audit Chain</span>
          </button>
        )}

        {/* RAG Chatbot Button */}
        <button 
          onClick={onOpenChat}
          className="action-icon-btn chat-btn"
          title="Ask IntelliCampus Document AI Chatbot"
        >
          <Sparkles size={17} />
          <span className="btn-label">Ask AI</span>
        </button>

        {/* Theme Toggle */}
        <button 
          onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          className="action-icon-btn"
          title={`Switch to ${theme === 'dark' ? 'Light' : 'Dark'} Mode`}
        >
          {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
        </button>

        {/* Authentication State & User Controls */}
        {loggedInUser ? (
          <div className="navbar-user-section">
            <div className="user-chip" title={studentOnly ? loggedInUser.name : `${loggedInUser.name} (${loggedInUser.role})`}>
              <div className="user-avatar-initials">
                {getInitials(loggedInUser.name)}
              </div>
              <div className="user-chip-info">
                <span className="user-chip-name">{loggedInUser.name}</span>
                {!studentOnly && <span className="user-chip-role">{loggedInUser.role}</span>}
              </div>
            </div>
            <button 
              onClick={onLogout} 
              className="action-icon-btn logout-btn"
              title="Logout from IntelliCampus"
            >
              <LogOut size={16} />
              <span className="btn-label">Logout</span>
            </button>
          </div>
        ) : (
          <button 
            onClick={onLoginClick} 
            className="action-icon-btn login-btn"
            title="Sign In to IntelliCampus"
          >
            <LogIn size={16} />
            <span className="btn-label">Login</span>
          </button>
        )}
      </div>
    </header>
  );
}
