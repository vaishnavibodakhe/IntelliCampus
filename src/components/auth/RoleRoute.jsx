import React, { useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import UnauthorizedPage from './UnauthorizedPage';

export default function RoleRoute({ requiredRole, children }) {
  const { isAuthenticated, currentUser, isAuthLoading, navigate } = useAuth();

  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      const loginRoute = requiredRole === 'MENTOR' ? '/mentor/login' : '/faculty/login';
      navigate(loginRoute, true);
    }
  }, [isAuthLoading, isAuthenticated, requiredRole, navigate]);

  // Prevent any flash of dashboard content while auth is being verified
  if (isAuthLoading) {
    return (
      <div className="auth-viewport" style={{ minHeight: '80vh' }}>
        <div className="auth-ambient-glow faculty" />
        <div className="auth-card-container" style={{ textAlign: 'center', padding: '3rem' }}>
          <div className="loading-spinner" style={{ margin: '0 auto 1.5rem', width: '36px', height: '36px' }} />
          <h2 style={{ fontSize: '1.1rem', color: 'var(--text-secondary)' }}>Verifying access authorization...</h2>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  if (requiredRole && currentUser?.role !== requiredRole) {
    return <UnauthorizedPage requiredRole={requiredRole} />;
  }

  return <>{children}</>;
}
