import React from 'react';
import { ShieldAlert, ArrowLeft, Home } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import './Auth.css';

export default function UnauthorizedPage({ requiredRole }) {
  const { currentUser, navigate, logout } = useAuth();

  const handleReturnDashboard = () => {
    if (currentUser?.role === 'FACULTY') {
      navigate('/faculty/dashboard');
    } else if (currentUser?.role === 'MENTOR') {
      navigate('/mentor/dashboard');
    } else {
      navigate('/');
    }
  };

  return (
    <div className="auth-viewport">
      <div className="auth-ambient-glow faculty" />
      <div className="auth-card-container" style={{ textAlign: 'center', maxWidth: '480px' }}>
        <div style={{ display: 'inline-flex', padding: '16px', background: 'rgba(239, 68, 68, 0.12)', borderRadius: '50%', color: '#ef4444', marginBottom: '1.25rem', border: '1px solid rgba(239, 68, 68, 0.25)' }}>
          <ShieldAlert size={48} />
        </div>

        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--text-primary)', marginBottom: '0.5rem' }}>
          403 - Access Prohibited
        </h1>

        <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)', lineHeight: 1.6, marginBottom: '1.75rem' }}>
          Your current account role (<strong>{currentUser?.role || 'Guest'}</strong>) is not authorized to access this portal section. This area requires <strong>{requiredRole || 'higher privileges'}</strong>.
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          <button
            onClick={handleReturnDashboard}
            className="auth-submit-btn faculty"
            style={{ width: '100%', justifyContent: 'center' }}
          >
            <Home size={16} />
            <span>Go to My Authorized Dashboard</span>
          </button>

          <button
            onClick={logout}
            style={{
              padding: '0.75rem',
              background: 'transparent',
              border: '1px solid var(--border-medium)',
              borderRadius: 'var(--radius-md)',
              color: 'var(--text-secondary)',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '0.5rem',
              fontSize: '0.88rem'
            }}
          >
            <ArrowLeft size={15} />
            <span>Sign In with Different Account</span>
          </button>
        </div>
      </div>
    </div>
  );
}
