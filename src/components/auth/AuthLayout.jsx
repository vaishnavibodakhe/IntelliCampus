import React from 'react';
import './Auth.css';
import { UserCheck, Users, ShieldCheck } from 'lucide-react';

export default function AuthLayout({
  role = 'FACULTY',
  onRoleChange,
  title,
  subtitle,
  children,
  wide = false,
  showRoleToggle = true
}) {
  const isFaculty = role === 'FACULTY';

  return (
    <div className="auth-viewport">
      {/* Dynamic ambient backdrop glow */}
      <div className={`auth-ambient-glow ${isFaculty ? 'faculty' : 'mentor'}`} />

      <div className={`auth-card-container ${wide ? 'auth-card-wide' : ''}`}>
        {/* Brand & Portal Header */}
        <div className="auth-header">
          <div className={`auth-badge ${isFaculty ? 'faculty' : 'mentor'}`}>
            {isFaculty ? <UserCheck size={14} /> : <Users size={14} />}
            <span>{isFaculty ? 'Faculty Governance Portal' : 'Mentor & Guidance Portal'}</span>
          </div>

          <h1 className="auth-title">{title}</h1>
          {subtitle && <p className="auth-subtitle">{subtitle}</p>}
        </div>

        {/* Role Toggle Switcher (Faculty | Mentor only) */}
        {showRoleToggle && onRoleChange && (
          <div className="role-toggle-wrapper">
            <span className="role-toggle-label">Select Operating Persona</span>
            <div className="role-toggle-group" role="tablist" aria-label="Role selection">
              <button
                type="button"
                role="tab"
                id="auth-role-toggle-faculty"
                aria-selected={isFaculty}
                className={`role-toggle-btn faculty ${isFaculty ? 'active' : ''}`}
                onClick={() => onRoleChange('FACULTY')}
              >
                <UserCheck size={16} />
                <span>Faculty</span>
              </button>
              <button
                type="button"
                role="tab"
                id="auth-role-toggle-mentor"
                aria-selected={!isFaculty}
                className={`role-toggle-btn mentor ${!isFaculty ? 'active' : ''}`}
                onClick={() => onRoleChange('MENTOR')}
              >
                <Users size={16} />
                <span>Mentor</span>
              </button>
            </div>
          </div>
        )}

        {/* Child Form Slot */}
        {children}
      </div>
    </div>
  );
}
