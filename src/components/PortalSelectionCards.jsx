import React from 'react';
import { UserCheck, Users, ArrowRight, Shield, Award, Sparkles } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function PortalSelectionCards() {
  const { navigate } = useAuth();

  return (
    <section className="portal-selection-section" style={{ margin: '1.75rem 0', padding: '0.5rem' }}>
      <div style={{ textAlign: 'center', marginBottom: '1.5rem' }}>
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.5rem',
            padding: '0.35rem 0.9rem',
            borderRadius: '9999px',
            fontSize: '0.75rem',
            fontWeight: 700,
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
            background: 'rgba(99, 102, 241, 0.12)',
            color: '#818cf8',
            border: '1px solid rgba(99, 102, 241, 0.3)',
            marginBottom: '0.75rem',
          }}
        >
          <Sparkles size={14} />
          <span>Independent Institutional Portals</span>
        </div>
        <h2 style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--text-primary)', margin: '0 0 0.4rem' }}>
          Choose Your Portal Access
        </h2>
        <p style={{ fontSize: '0.88rem', color: 'var(--text-secondary)', margin: 0 }}>
          Dedicated portals with isolated roles, credentials, and academic workspaces
        </p>
      </div>

      <div
        className="portal-cards-grid"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
          gap: '1.5rem',
          maxWidth: '850px',
          margin: '0 auto',
        }}
      >
        {/* Faculty Portal Card */}
        <div
          className="portal-card faculty"
          style={{
            background: 'var(--bg-glass-card)',
            border: '1px solid rgba(6, 182, 212, 0.35)',
            borderRadius: 'var(--radius-xl)',
            padding: '1.75rem',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
            position: 'relative',
            overflow: 'hidden',
            boxShadow: '0 10px 25px -5px rgba(6, 182, 212, 0.1)',
            transition: 'transform 0.2s ease, border-color 0.2s ease',
          }}
        >
          <div>
            <div
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.4rem',
                padding: '0.3rem 0.75rem',
                borderRadius: '9999px',
                fontSize: '0.72rem',
                fontWeight: 700,
                textTransform: 'uppercase',
                color: 'var(--accent-cyan)',
                background: 'rgba(6, 182, 212, 0.12)',
                border: '1px solid rgba(6, 182, 212, 0.3)',
                marginBottom: '1rem',
              }}
            >
              <UserCheck size={13} />
              <span>FACULTY PORTAL</span>
            </div>

            <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)', marginBottom: '0.5rem' }}>
              Faculty Instruction & Grading
            </h3>

            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', lineHeight: 1.55, marginBottom: '1.25rem' }}>
              Conduct live classroom attendance sessions, submit semester grading rubrics, propose mark revisions to HOD, and review facial recognition attendance logs.
            </p>

            <ul style={{ listStyle: 'none', padding: 0, margin: '0 0 1.5rem', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
              <li style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.4rem' }}>
                <span style={{ color: 'var(--accent-cyan)' }}>✓</span> Live AI Attendance & Biometrics
              </li>
              <li style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.4rem' }}>
                <span style={{ color: 'var(--accent-cyan)' }}>✓</span> Continuous Assessment Grading
              </li>
              <li style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span style={{ color: 'var(--accent-cyan)' }}>✓</span> SHA-256 Audit Trail Tracking
              </li>
            </ul>
          </div>

          <button
            type="button"
            id="landing-faculty-portal-btn"
            className="auth-submit-btn faculty"
            style={{ width: '100%', justifyContent: 'center' }}
            onClick={() => navigate('/faculty/login')}
          >
            <span>Sign In to Faculty Portal</span>
            <ArrowRight size={16} />
          </button>
        </div>

        {/* Mentor Portal Card */}
        <div
          className="portal-card mentor"
          style={{
            background: 'var(--bg-glass-card)',
            border: '1px solid rgba(16, 185, 129, 0.35)',
            borderRadius: 'var(--radius-xl)',
            padding: '1.75rem',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
            position: 'relative',
            overflow: 'hidden',
            boxShadow: '0 10px 25px -5px rgba(16, 185, 129, 0.1)',
            transition: 'transform 0.2s ease, border-color 0.2s ease',
          }}
        >
          <div>
            <div
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.4rem',
                padding: '0.3rem 0.75rem',
                borderRadius: '9999px',
                fontSize: '0.72rem',
                fontWeight: 700,
                textTransform: 'uppercase',
                color: 'var(--accent-emerald)',
                background: 'rgba(16, 185, 129, 0.12)',
                border: '1px solid rgba(16, 185, 129, 0.3)',
                marginBottom: '1rem',
              }}
            >
              <Users size={13} />
              <span>MENTOR & GUIDANCE PORTAL</span>
            </div>

            <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)', marginBottom: '0.5rem' }}>
              Student Advisory & Mentorship
            </h3>

            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', lineHeight: 1.55, marginBottom: '1.25rem' }}>
              Monitor assigned mentee academic risk scores, adjudicate medical leave requests, conduct 1-on-1 counseling, and recommend early interventions.
            </p>

            <ul style={{ listStyle: 'none', padding: 0, margin: '0 0 1.5rem', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
              <li style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.4rem' }}>
                <span style={{ color: 'var(--accent-emerald)' }}>✓</span> Mentee Risk & Early Warning ML
              </li>
              <li style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.4rem' }}>
                <span style={{ color: 'var(--accent-emerald)' }}>✓</span> Student Leave Sanctioning
              </li>
              <li style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span style={{ color: 'var(--accent-emerald)' }}>✓</span> Area of Expertise Advising
              </li>
            </ul>
          </div>

          <button
            type="button"
            id="landing-mentor-portal-btn"
            className="auth-submit-btn mentor"
            style={{ width: '100%', justifyContent: 'center' }}
            onClick={() => navigate('/mentor/login')}
          >
            <span>Sign In to Mentor Portal</span>
            <ArrowRight size={16} />
          </button>
        </div>
      </div>
    </section>
  );
}
