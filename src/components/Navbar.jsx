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
  BookOpen
} from 'lucide-react';
import NotificationBell from './NotificationBell';

export default function Navbar({ 
  currentRole, 
  setCurrentRole, 
  theme, 
  setTheme, 
  onOpenAudit, 
  onOpenChat,
  activeSession,
  notificationCount = 0,
  staffSession,
  notices = [],
  unreadCount,
  isLoadingNotices = false,
  noticeError = null,
  onMarkNoticeRead,
  onRefreshNotices,
  onViewAllNotices,
  currentUser = null
}) {
  const roles = [
    { id: 'ADMIN', label: 'Admin', icon: ShieldCheck, color: '#6366f1' },
    { id: 'STAFF', label: 'Staff', icon: UserCheck, color: '#8b5cf6' },
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
          <div className="brand-title">IntelliCampus</div>
          <div className="brand-subtitle">Intelligent College Platform</div>
        </div>
      </div>

      {/* Role Workflow Switcher */}
      <div className="role-switcher-container">
        <span className="role-switcher-label">Select Active Workflow:</span>
        <div className="role-pill-group">
          {roles.map(r => {
            const Icon = r.icon;
            const isActive = r.id === 'STAFF'
              ? (currentRole === 'STAFF' || currentRole === 'HOD' || currentRole === 'FACULTY' || currentRole === 'MENTOR')
              : currentRole === r.id;
            return (
              <button
                key={r.id}
                onClick={() => {
                  if (r.id === 'STAFF' && staffSession && ['HOD', 'FACULTY', 'MENTOR'].includes(staffSession?.user?.role)) {
                    setCurrentRole(staffSession.user.role);
                  } else {
                    setCurrentRole(r.id);
                  }
                }}
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

        {/* Notice Broadcast Alerts Indicator & Dropdown */}
        <NotificationBell 
          notices={notices}
          unreadCount={unreadCount !== undefined ? unreadCount : notificationCount}
          isLoading={isLoadingNotices}
          error={noticeError}
          onMarkNoticeRead={onMarkNoticeRead}
          onRefresh={onRefreshNotices}
          onViewAll={onViewAllNotices}
          currentRole={currentRole}
          currentUser={currentUser}
        />

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
