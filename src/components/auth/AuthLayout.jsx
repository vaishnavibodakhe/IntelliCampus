import React from 'react';
import './Auth.css';
import { UserCheck, Users } from 'lucide-react';

export default function AuthLayout({
  role = 'FACULTY',
  title,
  subtitle,
  children,
  wide = false,
  badgeText,
}) {
  const isFaculty = role.toUpperCase() === 'FACULTY';
  const roleClass = isFaculty ? 'faculty' : 'mentor';
  const defaultBadgeText = isFaculty ? 'FACULTY PORTAL' : 'MENTOR & GUIDANCE PORTAL';

  return (
    <div className="auth-viewport">
      {/* Dynamic ambient backdrop glow tailored to portal role */}
      <div className={`auth-ambient-glow ${roleClass}`} />

      <div className={`auth-card-container ${wide ? 'auth-card-wide' : ''}`}>
        {/* Brand & Portal Header */}
        <div className="auth-header">
          <div className={`auth-badge ${roleClass}`}>
            {isFaculty ? <UserCheck size={14} /> : <Users size={14} />}
            <span>{badgeText || defaultBadgeText}</span>
          </div>

          <h1 className="auth-title">{title}</h1>
          {subtitle && <p className="auth-subtitle">{subtitle}</p>}
        </div>

        {/* Child Form Slot (Persona toggle completely removed) */}
        {children}
      </div>
    </div>
  );
}
