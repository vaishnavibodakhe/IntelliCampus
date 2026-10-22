import React, { useState, useEffect, useRef } from 'react';
import { 
  Bell, 
  Check, 
  CheckCircle2, 
  Clock, 
  AlertCircle, 
  RefreshCw, 
  X, 
  Loader2, 
  ChevronDown, 
  ChevronUp,
  ExternalLink
} from 'lucide-react';

/**
 * NotificationBell Component
 * 
 * Provides an interactive notification bell indicator and dropdown panel
 * for students to browse targeted announcements, view read/unread states,
 * and mark circulars as read with backend persistence.
 */
export default function NotificationBell({
  notices = [],
  unreadCount = 0,
  isLoading = false,
  error = null,
  onMarkNoticeRead,
  onRefresh,
  onViewAll,
  currentRole = 'STUDENT',
  currentUser = null
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [expandedNoticeId, setExpandedNoticeId] = useState(null);
  const [markingIds, setMarkingIds] = useState(new Set());
  const [actionError, setActionError] = useState(null);

  const containerRef = useRef(null);

  // Close dropdown on outside clicks
  useEffect(() => {
    function handleClickOutside(event) {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    }

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      document.addEventListener('touchstart', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('touchstart', handleClickOutside);
    };
  }, [isOpen]);

  // Close dropdown on Escape key
  useEffect(() => {
    function handleKeyDown(e) {
      if (e.key === 'Escape' && isOpen) {
        setIsOpen(false);
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen]);

  const handleToggle = () => {
    setIsOpen(prev => !prev);
    setActionError(null);
  };

  const handleNoticeClick = async (notice) => {
    // Toggle expanded view
    setExpandedNoticeId(prev => prev === notice.id ? null : notice.id);

    // If notice is unread, automatically mark as read
    if (!notice.read && onMarkNoticeRead) {
      await handleMarkAsRead(notice.id);
    }
  };

  const handleMarkAsRead = async (noticeId, e) => {
    if (e) e.stopPropagation();
    if (markingIds.has(noticeId) || !onMarkNoticeRead) return;

    setMarkingIds(prev => new Set(prev).add(noticeId));
    setActionError(null);

    try {
      await onMarkNoticeRead(noticeId);
    } catch (err) {
      console.error('Failed to mark notice read:', err);
      setActionError(`Could not mark notice #${noticeId} as read. Please try again.`);
      // Notice read state is NOT modified if backend fails
    } finally {
      setMarkingIds(prev => {
        const next = new Set(prev);
        next.delete(noticeId);
        return next;
      });
    }
  };

  const handleMarkAllRead = async (e) => {
    if (e) e.stopPropagation();
    if (!onMarkNoticeRead) return;

    const unreadNotices = notices.filter(n => !n.read);
    if (unreadNotices.length === 0) return;

    setActionError(null);
    for (const notice of unreadNotices) {
      try {
        await onMarkNoticeRead(notice.id);
      } catch (err) {
        console.error(`Failed to mark notice #${notice.id} as read:`, err);
        setActionError('Failed to mark all notices as read. Server error.');
        break; // Stop on first failure
      }
    }
  };

  // Safe publisher name fallback
  const getPublisherDisplay = (notice) => {
    return notice.publisherName || notice.publisherRole || 'Campus Administration';
  };

  // Format date helper
  const formatDateDisplay = (dateStr) => {
    if (!dateStr) return 'Recent';
    try {
      const d = new Date(dateStr);
      if (isNaN(d.getTime())) return dateStr;
      return d.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric'
      });
    } catch {
      return dateStr;
    }
  };

  return (
    <div className="notice-bell-container" ref={containerRef} style={{ position: 'relative' }}>
      {/* Existing notification bell button styled with standard classes */}
      <button
        type="button"
        id="student-notice-bell-btn"
        className="action-icon-btn notice-bell-btn"
        title={`${unreadCount} Unread Campus ${unreadCount === 1 ? 'Notice' : 'Notices'}`}
        aria-label="Campus Notice Alerts"
        aria-expanded={isOpen}
        onClick={handleToggle}
        style={{
          position: 'relative',
          cursor: 'pointer',
          background: isOpen ? 'var(--bg-surface-elevated)' : undefined,
          borderColor: isOpen ? 'var(--border-accent)' : undefined
        }}
      >
        <Bell size={17} />
        {unreadCount > 0 && (
          <span 
            className="badge-notification-count"
            id="notice-bell-badge"
            style={{
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
              padding: '0 3px',
              boxShadow: '0 0 8px rgba(239, 68, 68, 0.6)'
            }}
          >
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {/* Dropdown panel */}
      {isOpen && (
        <div 
          className="notice-dropdown-panel glass-card animate-fade-in"
          id="notice-dropdown-panel"
          style={{
            position: 'absolute',
            top: 'calc(100% + 8px)',
            right: 0,
            width: 'min(400px, 92vw)',
            maxHeight: '520px',
            display: 'flex',
            flexDirection: 'column',
            zIndex: 1000,
            borderRadius: 'var(--radius-lg, 14px)',
            padding: 0,
            overflow: 'hidden',
            boxShadow: '0 16px 40px rgba(0, 0, 0, 0.5), 0 0 0 1px var(--border-medium, rgba(255,255,255,0.12))',
            background: 'var(--bg-surface, #181d2d)'
          }}
        >
          {/* Panel Header */}
          <div 
            className="notice-dropdown-header"
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '12px 16px',
              borderBottom: '1px solid var(--border-subtle, rgba(255,255,255,0.08))',
              background: 'var(--bg-surface-elevated, #21273d)'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Bell size={16} className="text-primary" />
              <strong style={{ fontSize: '0.95rem', color: 'var(--text-primary)' }}>
                Campus Notices
              </strong>
              {unreadCount > 0 ? (
                <span className="badge-pill accent" style={{ fontSize: '0.7rem', padding: '2px 7px' }}>
                  {unreadCount} unread
                </span>
              ) : (
                <span className="badge-pill success" style={{ fontSize: '0.7rem', padding: '2px 7px' }}>
                  All read
                </span>
              )}
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              {unreadCount > 0 && onMarkNoticeRead && (
                <button
                  type="button"
                  onClick={handleMarkAllRead}
                  className="btn-mark-all-read"
                  style={{
                    background: 'transparent',
                    border: 'none',
                    color: '#818cf8',
                    fontSize: '0.75rem',
                    fontWeight: 600,
                    cursor: 'pointer',
                    padding: '3px 6px',
                    borderRadius: '4px'
                  }}
                  title="Mark all circulars as read"
                >
                  Mark all read
                </button>
              )}
              {onRefresh && (
                <button
                  type="button"
                  onClick={onRefresh}
                  disabled={isLoading}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    color: 'var(--text-secondary)',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    padding: '4px'
                  }}
                  title="Refresh notice feed"
                >
                  <RefreshCw size={13} className={isLoading ? 'animate-spin' : ''} />
                </button>
              )}
              <button
                type="button"
                onClick={() => setIsOpen(false)}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: 'var(--text-secondary)',
                  cursor: 'pointer',
                  fontSize: '1rem',
                  lineHeight: 1,
                  padding: '4px'
                }}
                title="Close panel"
              >
                ✕
              </button>
            </div>
          </div>

          {/* Action error banner if an action failed */}
          {actionError && (
            <div style={{
              background: 'rgba(239, 68, 68, 0.15)',
              color: '#f87171',
              padding: '8px 14px',
              fontSize: '0.78rem',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              borderBottom: '1px solid rgba(239, 68, 68, 0.25)'
            }}>
              <AlertCircle size={14} style={{ flexShrink: 0 }} />
              <span style={{ flex: 1 }}>{actionError}</span>
              <button 
                type="button" 
                onClick={() => setActionError(null)}
                style={{ background: 'none', border: 'none', color: '#f87171', cursor: 'pointer' }}
              >
                ✕
              </button>
            </div>
          )}

          {/* Panel Body: States & List */}
          <div 
            className="notice-dropdown-body"
            style={{
              overflowY: 'auto',
              flex: 1,
              maxHeight: '380px'
            }}
          >
            {/* Loading state */}
            {isLoading && notices.length === 0 && (
              <div style={{ padding: '36px 20px', textAlign: 'center', color: 'var(--text-secondary)' }}>
                <Loader2 size={24} className="animate-spin" style={{ margin: '0 auto 8px', display: 'block', color: 'var(--primary, #6366f1)' }} />
                <p style={{ margin: 0, fontSize: '0.85rem' }}>Loading targeted notices...</p>
              </div>
            )}

            {/* Error state */}
            {error && (
              <div style={{ padding: '24px 16px', textAlign: 'center', color: '#f87171' }}>
                <AlertCircle size={28} style={{ margin: '0 auto 8px', display: 'block' }} />
                <p style={{ margin: '0 0 10px', fontSize: '0.85rem' }}>{error}</p>
                {onRefresh && (
                  <button 
                    type="button" 
                    onClick={onRefresh}
                    className="btn-secondary"
                    style={{ fontSize: '0.78rem', padding: '4px 12px' }}
                  >
                    Retry
                  </button>
                )}
              </div>
            )}

            {/* Empty state */}
            {!isLoading && !error && notices.length === 0 && (
              <div 
                className="notice-empty-state"
                id="notice-empty-state"
                style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--text-muted)' }}
              >
                <CheckCircle2 size={36} style={{ margin: '0 auto 10px', display: 'block', opacity: 0.6, color: '#10b981' }} />
                <strong style={{ display: 'block', fontSize: '0.95rem', color: 'var(--text-primary)', marginBottom: '4px' }}>
                  You're all caught up!
                </strong>
                <p style={{ margin: 0, fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                  No circulars or announcements addressed to your profile right now.
                </p>
              </div>
            )}

            {/* Notices List */}
            {notices.length > 0 && (
              <div className="notice-dropdown-list" style={{ display: 'flex', flexDirection: 'column' }}>
                {notices.map((notice) => {
                  const isExpanded = expandedNoticeId === notice.id;
                  const isMarking = markingIds.has(notice.id);
                  const isUnread = !notice.read;

                  return (
                    <div
                      key={notice.id}
                      className={`notice-dropdown-item ${isUnread ? 'unread' : 'read'}`}
                      onClick={() => handleNoticeClick(notice)}
                      style={{
                        padding: '12px 16px',
                        borderBottom: '1px solid var(--border-subtle, rgba(255,255,255,0.06))',
                        background: isUnread 
                          ? 'rgba(99, 102, 241, 0.08)' 
                          : 'transparent',
                        borderLeft: isUnread 
                          ? '3px solid #6366f1' 
                          : '3px solid transparent',
                        cursor: 'pointer',
                        transition: 'background 0.15s ease'
                      }}
                    >
                      {/* Title & Badge Header */}
                      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '8px', marginBottom: '4px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flex: 1 }}>
                          {isUnread && (
                            <span 
                              className="notice-unread-dot" 
                              title="Unread circular"
                              style={{
                                width: '7px',
                                height: '7px',
                                borderRadius: '50%',
                                background: '#6366f1',
                                flexShrink: 0
                              }} 
                            />
                          )}
                          <h4 style={{ 
                            margin: 0, 
                            fontSize: '0.88rem', 
                            fontWeight: isUnread ? 700 : 600,
                            color: isUnread ? 'var(--text-primary, #ffffff)' : 'var(--text-secondary, #cbd5e1)',
                            lineHeight: 1.3
                          }}>
                            {notice.title}
                          </h4>
                        </div>

                        {/* Read/Unread Pill */}
                        <span 
                          className={`badge-pill ${isUnread ? 'accent' : 'success'}`}
                          style={{ fontSize: '0.65rem', padding: '1px 6px', flexShrink: 0 }}
                        >
                          {isUnread ? 'Unread' : 'Read'}
                        </span>
                      </div>

                      {/* Notice Message Preview or Full */}
                      <p style={{
                        margin: '4px 0 8px',
                        fontSize: '0.8rem',
                        lineHeight: 1.45,
                        color: 'var(--text-secondary, #94a3b8)',
                        display: isExpanded ? 'block' : '-webkit-box',
                        WebkitLineClamp: isExpanded ? 'unset' : 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden'
                      }}>
                        {notice.body || notice.message}
                      </p>

                      {/* Metadata Row: Publisher, Date, Actions */}
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        fontSize: '0.72rem',
                        color: 'var(--text-muted, #64748b)',
                        flexWrap: 'wrap',
                        gap: '6px'
                      }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                          <span>📢 {getPublisherDisplay(notice)}</span>
                          <span>•</span>
                          <span>{formatDateDisplay(notice.publishedAt)}</span>
                          {notice.deadline && (
                            <span className="badge-pill warning" style={{ fontSize: '0.65rem', padding: '1px 5px' }}>
                              <Clock size={10} style={{ marginRight: '3px' }} />
                              Due: {notice.deadline}
                            </span>
                          )}
                        </div>

                        {/* Mark Read Button for unread circular */}
                        {isUnread && onMarkNoticeRead && (
                          <button
                            type="button"
                            className="btn-mark-read"
                            onClick={(e) => handleMarkAsRead(notice.id, e)}
                            disabled={isMarking}
                            style={{
                              background: 'rgba(99, 102, 241, 0.18)',
                              border: '1px solid rgba(99, 102, 241, 0.4)',
                              color: '#c7d2fe',
                              fontSize: '0.72rem',
                              fontWeight: 600,
                              borderRadius: '4px',
                              padding: '2px 8px',
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              gap: '4px'
                            }}
                            title="Mark this notice as read"
                          >
                            {isMarking ? (
                              <Loader2 size={11} className="animate-spin" />
                            ) : (
                              <Check size={11} />
                            )}
                            Mark Read
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Panel Footer */}
          {notices.length > 0 && (
            <div 
              className="notice-dropdown-footer"
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '10px 16px',
                borderTop: '1px solid var(--border-subtle, rgba(255,255,255,0.08))',
                background: 'var(--bg-surface-elevated, #21273d)',
                fontSize: '0.75rem',
                color: 'var(--text-secondary)'
              }}
            >
              <span>{unreadCount} unread • {notices.length} total</span>
              {onViewAll && (
                <button
                  type="button"
                  onClick={() => {
                    setIsOpen(false);
                    onViewAll();
                  }}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#818cf8',
                    cursor: 'pointer',
                    fontSize: '0.75rem',
                    fontWeight: 600,
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                    padding: 0
                  }}
                >
                  <span>View All in Notices</span>
                  <ExternalLink size={12} />
                </button>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
