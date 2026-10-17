import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('intellicampus_token') || null);
  const [currentUser, setCurrentUser] = useState(() => {
    try {
      const stored = localStorage.getItem('intellicampus_user');
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });
  const [isAuthLoading, setIsAuthLoading] = useState(true);
  const [currentPath, setCurrentPath] = useState(() => window.location.pathname + window.location.search);

  // Synchronize authentication on initial mount and on pageshow (bfcache restoration)
  useEffect(() => {
    const syncAuth = () => {
      const storedToken = localStorage.getItem('intellicampus_token');
      const storedUser = localStorage.getItem('intellicampus_user');
      if (storedToken && storedUser) {
        try {
          const parsed = JSON.parse(storedUser);
          setToken(storedToken);
          setCurrentUser(parsed);

          // Asynchronously refresh real profile from database via me endpoint
          if (parsed.role === 'FACULTY' || parsed.role === 'MENTOR') {
            const meUrl = parsed.role === 'MENTOR' ? '/api/mentor/me' : '/api/faculty/me';
            fetch(meUrl, {
              headers: { 'Authorization': `Bearer ${storedToken}` }
            })
            .then(res => {
              if (res.status === 401) {
                logout();
              } else if (res.ok) {
                return res.json();
              }
            })
            .then(resData => {
              if (resData && resData.data) {
                setCurrentUser(resData.data);
                localStorage.setItem('intellicampus_user', JSON.stringify(resData.data));
              }
            })
            .catch(() => {
              // Silently retain cached user if offline or starting up
            });
          }
        } catch {
          localStorage.removeItem('intellicampus_token');
          localStorage.removeItem('intellicampus_user');
          setToken(null);
          setCurrentUser(null);
        }
      } else {
        setToken(null);
        setCurrentUser(null);
      }
      setIsAuthLoading(false);
    };

    syncAuth();

    const handlePopState = () => {
      setCurrentPath(window.location.pathname + window.location.search);
      syncAuth();
    };

    const handlePageShow = (event) => {
      if (event.persisted) {
        syncAuth();
      }
    };

    window.addEventListener('popstate', handlePopState);
    window.addEventListener('pageshow', handlePageShow);
    return () => {
      window.removeEventListener('popstate', handlePopState);
      window.removeEventListener('pageshow', handlePageShow);
    };
  }, []);

  // Programmatic navigation helper with optional history replacement
  const navigate = useCallback((path, replace = false) => {
    if (window.location.pathname + window.location.search !== path) {
      if (replace) {
        window.history.replaceState(null, '', path);
      } else {
        window.history.pushState({}, '', path);
      }
      setCurrentPath(path);
      window.scrollTo(0, 0);
    }
  }, []);

  const login = useCallback((authData) => {
    if (!authData || !authData.token) return;

    const userProfile = {
      id: authData.id,
      name: authData.name,
      email: authData.email,
      userCode: authData.userCode,
      role: authData.role,
      department: authData.department,
      phone: authData.phone
    };

    setToken(authData.token);
    setCurrentUser(userProfile);

    localStorage.setItem('intellicampus_token', authData.token);
    localStorage.setItem('intellicampus_user', JSON.stringify(userProfile));
  }, []);

  const logout = useCallback(async () => {
    const activeToken = token || localStorage.getItem('intellicampus_token');
    const userRole = currentUser?.role || 'FACULTY';

    // 1. Immediately wipe client credentials from memory & localStorage
    setToken(null);
    setCurrentUser(null);
    localStorage.removeItem('intellicampus_token');
    localStorage.removeItem('intellicampus_user');
    localStorage.removeItem('intellicampus_token_faculty');
    localStorage.removeItem('intellicampus_token_mentor');

    // 2. Call backend logout API to blacklist token in database
    if (activeToken) {
      try {
        await fetch('/api/auth/logout', {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${activeToken}`,
            'Content-Type': 'application/json'
          }
        });
      } catch (err) {
        console.warn('Backend logout notification skipped:', err);
      }
    }

    // 3. Replace history state so Back button cannot return to dashboard
    const loginTarget = userRole === 'MENTOR' ? '/mentor/login' : '/faculty/login';
    window.history.replaceState(null, '', loginTarget);
    setCurrentPath(loginTarget);
  }, [token, currentUser]);

  // Intercepting fetch that attaches Authorization header and catches 401s
  const authFetch = useCallback(async (url, options = {}) => {
    const headers = new Headers(options.headers || {});
    const activeToken = token || localStorage.getItem('intellicampus_token');
    if (activeToken) {
      headers.set('Authorization', `Bearer ${activeToken}`);
    }
    if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json');
    }

    const response = await fetch(url, { ...options, headers });

    if (response.status === 401) {
      console.warn('Received 401 Unauthorized - invalidating token and session');
      logout();
    }

    return response;
  }, [token, logout]);

  const value = {
    token,
    currentUser,
    isAuthenticated: !!token && !!currentUser,
    isAuthLoading,
    currentPath,
    login,
    logout,
    navigate,
    authFetch
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
