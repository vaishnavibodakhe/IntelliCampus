import React from 'react';
import {
  ShieldCheck,
  UserCheck,
  GraduationCap,
  QrCode,
  Sun,
  Moon,
  LogOut,
  LayoutGrid
} from 'lucide-react';
import NotificationBell from './NotificationBell';

export default function Navbar({
  currentPath = '/',
  currentRole,
  onNavigateHome,
  theme,
  setTheme,
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
  currentUser = null,
  onLogout
}) {
  // Determine active portal mode
  const isLanding = currentPath === '/' && (!currentRole || currentRole === 'LANDING');
  const isPrincipalPortal = currentPath === '/admin/login' || currentRole === 'ADMIN';
  const isStaffPortal = currentPath === '/staff/login' || ['STAFF', 'HOD', 'FACULTY', 'MENTOR'].includes(currentRole);
  const isStudentPortal = currentPath === '/student/login' || currentRole === 'STUDENT';
  const isPublicPortal = currentPath === '/verify' || currentRole === 'PUBLIC';

  const staffRole = staffSession?.user?.role;
  const staffName = staffSession?.user?.name;
  const studentName = currentUser?.name;

  return (
    <header className="navbar-container">
      {/* Brand logo & return to portal landing */}
      <div
        className="navbar-brand"
        onClick={onNavigateHome}
        style={{ cursor: 'pointer' }}
        title="Return to IntelliCampus Portal Selection"
      >
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

      {/* Role-Specific Portal Indicator without mixing options */}
      <div className="navbar-portal-center">
        {isLanding ? (
          <div className="portal-indicator-badge landing">
            <LayoutGrid size={15} style={{ color: '#818cf8' }} />
            <span>Role Selection Portal</span>
          </div>
        ) : isPrincipalPortal ? (
          <div className="portal-indicator-badge principal">
            <ShieldCheck size={16} />
            <span>Principal Portal</span>
          </div>
        ) : isStaffPortal ? (
          <div className="portal-indicator-badge staff">
            <UserCheck size={16} />
            <span>Staff Portal</span>
            {staffRole && <span className="staff-subrole-badge">{staffRole}</span>}
          </div>
        ) : isStudentPortal ? (
          <div className="portal-indicator-badge student">
            <GraduationCap size={16} />
            <span>Student Portal</span>
          </div>
        ) : isPublicPortal ? (
          <div className="portal-indicator-badge public">
            <QrCode size={16} />
            <span>Public Document Verifier</span>
          </div>
        ) : null}
      </div>

      {/* Action shortcuts */}
      <div className="navbar-actions">
        {/* Active Class Live Indicator (only for Staff and Students) */}
        {(isStaffPortal || isStudentPortal) && activeSession && activeSession.status === 'OPEN' && (
          <div className="live-session-badge animate-pulse-glow" title="Active attendance session ongoing">
            <span className="live-dot" />
            <span>Live: {activeSession.challengeCode}</span>
          </div>
        )}

        {/* Notice Broadcast Alerts Indicator & Dropdown (only for Staff and Students) */}
        {(isStaffPortal || isStudentPortal) && (
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
        )}

        {/* Switch Portal / Return to Home button when inside a specific portal */}
        {!isLanding && (
          <button
            type="button"
            onClick={onNavigateHome}
            className="action-icon-btn home-nav-btn"
            title="Switch to another Role Portal"
          >
            <LayoutGrid size={16} />
            <span className="btn-label">Portals</span>
          </button>
        )}

        {/* User Identity Chip when logged in */}
        {isPrincipalPortal && staffSession?.user?.role === 'ADMIN' && (
          <div className="navbar-user-chip" title="Connected as Principal">
            <span className="dot" />
            <span>{staffName || 'Principal'}</span>
          </div>
        )}

        {isStaffPortal && staffSession?.user && (
          <div className="navbar-user-chip" title={`Connected as ${staffRole}`}>
            <span className="dot" />
            <span>{staffName}</span>
          </div>
        )}

        {isStudentPortal && currentUser && (
          <div className="navbar-user-chip" title="Connected as Student">
            <span className="dot" />
            <span>{studentName}</span>
          </div>
        )}

        {/* Logout button in navbar when logged into any portal */}
        {onLogout && (
          (isPrincipalPortal && staffSession?.user?.role === 'ADMIN') ||
          (isStaffPortal && staffSession?.user) ||
          (isStudentPortal && currentUser)
        ) && (
          <button
            type="button"
            onClick={onLogout}
            className="action-icon-btn"
            style={{ color: '#ef4444', borderColor: 'rgba(239, 68, 68, 0.3)' }}
            title="Sign Out of Session"
          >
            <LogOut size={16} />
            <span className="btn-label">Sign Out</span>
          </button>
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
