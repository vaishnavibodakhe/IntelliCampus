import React, { useState } from 'react';
import { LogIn, UserPlus } from 'lucide-react';
import StudentLogin from './StudentLogin';
import StudentRegister from './StudentRegister';

export default function StudentAuthView({ onLoginSuccess, onRegisterSuccess, onAuthSuccess, defaultTab = 'login' }) {
  const [activeTab, setActiveTab] = useState(defaultTab);

  const handleLogin = (authData) => {
    if (onLoginSuccess) onLoginSuccess(authData);
    else if (onAuthSuccess) onAuthSuccess(authData);
  };

  const handleRegister = (authData) => {
    if (onRegisterSuccess) onRegisterSuccess(authData);
    else if (onAuthSuccess) onAuthSuccess(authData);
  };

  return (
    <div className="student-auth-viewport animate-fade-in">
      <div className="student-auth-tabs-wrap">
        <div className="student-auth-tab-pills" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'login'}
            onClick={() => setActiveTab('login')}
            className={`student-tab-pill ${activeTab === 'login' ? 'active' : ''}`}
            id="tab-student-login"
          >
            <LogIn size={16} />
            <span>Sign In to Dashboard</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'register'}
            onClick={() => setActiveTab('register')}
            className={`student-tab-pill ${activeTab === 'register' ? 'active' : ''}`}
            id="tab-student-register"
          >
            <UserPlus size={16} />
            <span>New Student Registration</span>
          </button>
        </div>
      </div>

      <div className="student-auth-pane">
        {activeTab === 'login' ? (
          <StudentLogin
            onSwitchToRegister={() => setActiveTab('register')}
            onLoginSuccess={handleLogin}
          />
        ) : (
          <StudentRegister
            onSwitchToLogin={() => setActiveTab('login')}
            onRegisterSuccess={handleRegister}
          />
        )}
      </div>
    </div>
  );
}
