import React, { useState } from 'react';
import LoginPage from './LoginPage';
import RegisterPage from './RegisterPage';
import ForgotPasswordPage from './ForgotPasswordPage';

export default function AuthContainer({
  role = 'FACULTY',
  onLoginSuccess
}) {
  const [authView, setAuthView] = useState('login'); // 'login' | 'register' | 'forgot'
  const [activeRole, setActiveRole] = useState(role);

  return (
    <div className="auth-module-wrapper">
      {authView === 'login' && (
        <LoginPage
          role={activeRole}
          onLoginSuccess={onLoginSuccess}
          onNavigateRegister={(r) => {
            if (r) setActiveRole(r);
            setAuthView('register');
          }}
          onNavigateForgotPassword={(r) => {
            if (r) setActiveRole(r);
            setAuthView('forgot');
          }}
        />
      )}

      {authView === 'register' && (
        <RegisterPage
          role={activeRole}
          onRegisterSuccess={() => {
            setAuthView('login');
          }}
          onNavigateLogin={(r) => {
            if (r) setActiveRole(r);
            setAuthView('login');
          }}
        />
      )}

      {authView === 'forgot' && (
        <ForgotPasswordPage
          role={activeRole}
          onNavigateLogin={(r) => {
            if (r) setActiveRole(r);
            setAuthView('login');
          }}
          onResetSuccess={() => {
            setAuthView('login');
          }}
        />
      )}
    </div>
  );
}
