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
  LogOut
} from 'lucide-react';

export default function Navbar({ 
  currentRole, 
  setCurrentRole, 
  theme, 
  setTheme, 
  onOpenAudit, 
  onOpenChat,
  activeSession,
  notificationCount = 2,
  currentUser = null,
  onLogout = null
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

      {/* Role Workflow Switcher */}
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

      {/* Action shortcuts */}
      <div className="navbar-actions">
        {/* Active Class Live Indicator */}
        {activeSession && activeSession.status === 'OPEN' && (
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

        {/* Cryptographic Audit Hash Chain Inspector */}
        <button 
          onClick={onOpenAudit}
          className="action-icon-btn audit-btn"
          title="Cryptographic SHA-256 Audit Chain Inspector"
        >
          <Link2 size={17} />
          <span className="btn-label">Audit Chain</span>
        </button>

        {/* RAG Chatbot Button */}
        <button 
          onClick={onOpenChat}
          className="action-icon-btn chat-btn"
          title="Ask IntelliCampus Document AI Chatbot"
        >
          <Sparkles size={17} />
          <span className="btn-label">Ask AI</span>
        </button>

        {/* Authenticated User Badge & Logout Option */}
        {currentUser && (
          <div className="auth-nav-user" style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', background: 'var(--bg-surface)', padding: '3px 8px 3px 10px', borderRadius: 'var(--radius-full)', border: '1px solid var(--border-medium)' }}>
            <span style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-primary)', maxWidth: '130px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {currentUser.name}
            </span>
            <button
              onClick={onLogout}
              id="navbar-logout-btn"
              className="action-icon-btn"
              title="Logout from session"
              style={{ padding: '4px', background: 'transparent', border: 'none', color: 'var(--danger-text)', cursor: 'pointer', display: 'flex', alignItems: 'center' }}
            >
              <LogOut size={14} />
            </button>
          </div>
        )}

        {/* Theme Toggle */}
        <button 
          onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          className="action-icon-btn"
          title={`Switch to ${theme === 'dark' ? 'Light' : 'Dark'} Mode`}
        >
          {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
        </button>
      </div>
    </header>
  );
}
