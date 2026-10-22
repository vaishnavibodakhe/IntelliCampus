import React from 'react';
import { 
  Shield, 
  UserCheck, 
  GraduationCap, 
  ArrowRight
} from 'lucide-react';
import './RolePortalLanding.css';

export default function RolePortalLanding({ 
  onSelectRole,
  staffSession,
  currentStudent
}) {
  const isPrincipalLoggedIn = staffSession?.user?.role === 'ADMIN';
  const isStaffLoggedIn = staffSession?.user && ['HOD', 'FACULTY', 'MENTOR'].includes(staffSession.user.role);
  const isStudentLoggedIn = Boolean(currentStudent);

  return (
    <div className="landing-viewport">
      {/* Top Header */}
      <header className="landing-top-header">
        <div className="landing-logo-badge" title="IntelliCampus">
          <GraduationCap size={40} className="landing-logo-icon" />
        </div>
        <h1 className="landing-title">IntelliCampus</h1>
        <p className="landing-subtitle">A best college platform</p>
      </header>

      {/* Three Horizontal Cards */}
      <main className="landing-cards-container">
        {/* Card 1: Principal */}
        <div className="landing-role-card principal">
          <div className="landing-card-icon-wrap principal">
            <Shield size={38} />
          </div>
          <h2 className="landing-card-title">Principal</h2>
          
          {isPrincipalLoggedIn && (
            <div className="landing-active-session-pill">
              <span className="landing-active-dot" />
              <span>Active: {staffSession.user.name}</span>
            </div>
          )}

          <button 
            type="button"
            onClick={() => onSelectRole('ADMIN')}
            className="landing-card-btn"
            id="btn-portal-principal"
          >
            <span>{isPrincipalLoggedIn ? 'Go to Principal Dashboard' : 'Principal Login'}</span>
            <ArrowRight size={16} />
          </button>
        </div>

        {/* Card 2: Staff */}
        <div className="landing-role-card staff">
          <div className="landing-card-icon-wrap staff">
            <UserCheck size={38} />
          </div>
          <h2 className="landing-card-title">Staff</h2>
          
          {isStaffLoggedIn && (
            <div className="landing-active-session-pill">
              <span className="landing-active-dot" />
              <span>Active: {staffSession.user.name} ({staffSession.user.role})</span>
            </div>
          )}

          <button 
            type="button"
            onClick={() => onSelectRole('STAFF')}
            className="landing-card-btn"
            id="btn-portal-staff"
          >
            <span>{isStaffLoggedIn ? 'Go to Staff Dashboard' : 'Staff Login'}</span>
            <ArrowRight size={16} />
          </button>
        </div>

        {/* Card 3: Student */}
        <div className="landing-role-card student">
          <div className="landing-card-icon-wrap student">
            <GraduationCap size={38} />
          </div>
          <h2 className="landing-card-title">Student</h2>
          
          {isStudentLoggedIn && (
            <div className="landing-active-session-pill">
              <span className="landing-active-dot" />
              <span>Active: {currentStudent.name}</span>
            </div>
          )}

          <button 
            type="button"
            onClick={() => onSelectRole('STUDENT')}
            className="landing-card-btn"
            id="btn-portal-student"
          >
            <span>{isStudentLoggedIn ? 'Go to Student Dashboard' : 'Student Login'}</span>
            <ArrowRight size={16} />
          </button>
        </div>
      </main>
    </div>
  );
}
