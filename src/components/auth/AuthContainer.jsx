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
          initialRole={activeRole}
          onLoginSuccess={onLoginSuccess}
          onNavigateRegister={(r) => {
            setActiveRole(r);
            setAuthView('register');
          }}
          onNavigateForgotPassword={(r) => {
            setActiveRole(r);
            setAuthView('forgot');
          }}
        />
      )}

      {authView === 'register' && (
        <RegisterPage
          initialRole={activeRole}
          onRegisterSuccess={(newUser) => {
            setAuthView('login');
          }}
          onNavigateLogin={(r) => {
            setActiveRole(r);
            setAuthView('login');
          }}
        />
      )}

      {authView === 'forgot' && (
        <ForgotPasswordPage
          initialRole={activeRole}
          onNavigateLogin={(r) => {
            setActiveRole(r);
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
