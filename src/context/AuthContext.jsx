import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // Check persistent localStorage first, then session-scoped sessionStorage
  const [token, setToken] = useState(() => {
    return localStorage.getItem('intellicampus_token') || sessionStorage.getItem('intellicampus_token') || null;
  });

  const [currentUser, setCurrentUser] = useState(() => {
    try {
      const stored = localStorage.getItem('intellicampus_user') || sessionStorage.getItem('intellicampus_user');
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
      const storedToken = localStorage.getItem('intellicampus_token') || sessionStorage.getItem('intellicampus_token');
      const storedUser = localStorage.getItem('intellicampus_user') || sessionStorage.getItem('intellicampus_user');

      if (storedToken && storedUser) {
        try {
          const parsed = JSON.parse(storedUser);
          const normalizedRole = (parsed.role || '').toUpperCase();
          parsed.role = normalizedRole;

          setToken(storedToken);
          setCurrentUser(parsed);

          // Asynchronously verify / refresh profile from database
          if (normalizedRole === 'FACULTY' || normalizedRole === 'MENTOR') {
            const meUrl = normalizedRole === 'MENTOR' ? '/api/mentor/me' : '/api/faculty/me';
            fetch(meUrl, {
              headers: { Authorization: `Bearer ${storedToken}` },
            })
              .then((res) => {
                if (res.status === 401 || res.status === 403) {
                  logout();
                } else if (res.ok) {
                  return res.json();
                }
              })
              .then((resData) => {
                if (resData && resData.data) {
                  const updatedUser = { ...resData.data, role: normalizedRole };
                  setCurrentUser(updatedUser);
                  const storage = localStorage.getItem('intellicampus_token') ? localStorage : sessionStorage;
                  storage.setItem('intellicampus_user', JSON.stringify(updatedUser));
                }
              })
              .catch(() => {
                // Silently retain cached user during network drop
              });
          }
        } catch {
          localStorage.removeItem('intellicampus_token');
          localStorage.removeItem('intellicampus_user');
          sessionStorage.removeItem('intellicampus_token');
          sessionStorage.removeItem('intellicampus_user');
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

    const normalizedRole = (authData.role || 'FACULTY').toUpperCase();
    const userProfile = {
      id: authData.id || authData._id,
      name: authData.name,
      email: authData.email,
      employeeId: authData.employeeId,
      userCode: authData.employeeId || authData.userCode,
      role: normalizedRole,
      department: authData.department,
      phone: authData.phone,
      specialization: authData.specialization,
    };

    setToken(authData.token);
    setCurrentUser(userProfile);

    // If "Remember me" is checked, use persistent localStorage; otherwise use sessionStorage
    if (authData.rememberMe) {
      localStorage.setItem('intellicampus_token', authData.token);
      localStorage.setItem('intellicampus_user', JSON.stringify(userProfile));
      sessionStorage.removeItem('intellicampus_token');
      sessionStorage.removeItem('intellicampus_user');
    } else {
      sessionStorage.setItem('intellicampus_token', authData.token);
      sessionStorage.setItem('intellicampus_user', JSON.stringify(userProfile));
      localStorage.removeItem('intellicampus_token');
      localStorage.removeItem('intellicampus_user');
    }
  }, []);

  const logout = useCallback(async () => {
    const activeToken =
      token || localStorage.getItem('intellicampus_token') || sessionStorage.getItem('intellicampus_token');
    const userRole = (currentUser?.role || 'FACULTY').toUpperCase();

    // 1. Wipe client credentials from memory and all storages
    setToken(null);
    setCurrentUser(null);
    localStorage.removeItem('intellicampus_token');
    localStorage.removeItem('intellicampus_user');
    localStorage.removeItem('intellicampus_token_faculty');
    localStorage.removeItem('intellicampus_token_mentor');
    sessionStorage.removeItem('intellicampus_token');
    sessionStorage.removeItem('intellicampus_user');

    // 2. Call backend logout notification
    if (activeToken) {
      try {
        await fetch('/api/auth/logout', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${activeToken}`,
            'Content-Type': 'application/json',
          },
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
  const authFetch = useCallback(
    async (url, options = {}) => {
      const headers = new Headers(options.headers || {});
      const activeToken =
        token || localStorage.getItem('intellicampus_token') || sessionStorage.getItem('intellicampus_token');
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
    },
    [token, logout]
  );

  const value = {
    token,
    currentUser,
    isAuthenticated: !!token && !!currentUser,
    isAuthLoading,
    currentPath,
    login,
    logout,
    navigate,
    authFetch,
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
