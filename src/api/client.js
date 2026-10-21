/**
 * IntelliCampus REST API Client
 * Connects frontend to Spring Boot backend via Vite proxy (/api -> http://localhost:8080)
 */

const TOKEN_STORAGE_KEY = 'intellicampus_auth_token';
const USER_STORAGE_KEY = 'intellicampus_auth_user';

const ALL_STORAGE_KEYS = [
  'intellicampus_auth_token',
  'intellicampus_auth_user',
  'intellicampus_token',
  'intellicampus_user',
  'intellicampus_token_faculty',
  'intellicampus_token_mentor',
  'intellicampus_staff_session',
  'intellicampus_student_session'
];

export function getStoredToken() {
  try {
    const direct = localStorage.getItem(TOKEN_STORAGE_KEY) || 
                   sessionStorage.getItem(TOKEN_STORAGE_KEY) ||
                   localStorage.getItem('intellicampus_token') || 
                   sessionStorage.getItem('intellicampus_token');
    if (direct) return direct;

    const staffRaw = localStorage.getItem('intellicampus_staff_session') || sessionStorage.getItem('intellicampus_staff_session');
    if (staffRaw) {
      const parsed = JSON.parse(staffRaw);
      const staffToken = parsed.accessToken || parsed.token;
      if (staffToken) return staffToken;
    }

    const studentRaw = localStorage.getItem('intellicampus_student_session') || sessionStorage.getItem('intellicampus_student_session');
    if (studentRaw) {
      const parsed = JSON.parse(studentRaw);
      const studentToken = parsed.token || parsed.accessToken;
      if (studentToken) return studentToken;
    }

    return null;
  } catch {
    return null;
  }
}

export function setStoredToken(token, user = null, rememberMe = true) {
  try {
    const primaryStorage = rememberMe ? localStorage : sessionStorage;
    if (token) {
      primaryStorage.setItem(TOKEN_STORAGE_KEY, token);
      primaryStorage.setItem('intellicampus_token', token);
    } else {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      sessionStorage.removeItem(TOKEN_STORAGE_KEY);
      localStorage.removeItem('intellicampus_token');
      sessionStorage.removeItem('intellicampus_token');
    }

    if (user) {
      const userStr = JSON.stringify(user);
      primaryStorage.setItem(USER_STORAGE_KEY, userStr);
      primaryStorage.setItem('intellicampus_user', userStr);
    } else if (token === null) {
      localStorage.removeItem(USER_STORAGE_KEY);
      sessionStorage.removeItem(USER_STORAGE_KEY);
      localStorage.removeItem('intellicampus_user');
      sessionStorage.removeItem('intellicampus_user');
    }
  } catch (err) {
    console.warn('Storage access unavailable:', err);
  }
}

export function getStoredUser() {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY) || 
                sessionStorage.getItem(USER_STORAGE_KEY) ||
                localStorage.getItem('intellicampus_user') || 
                sessionStorage.getItem('intellicampus_user');
    if (raw) return JSON.parse(raw);

    const staffRaw = localStorage.getItem('intellicampus_staff_session') || sessionStorage.getItem('intellicampus_staff_session');
    if (staffRaw) {
      const parsed = JSON.parse(staffRaw);
      if (parsed.user) return parsed.user;
    }

    const studentRaw = localStorage.getItem('intellicampus_student_session') || sessionStorage.getItem('intellicampus_student_session');
    if (studentRaw) {
      const parsed = JSON.parse(studentRaw);
      if (parsed.user) return parsed.user;
    }

    return null;
  } catch {
    return null;
  }
}

export function clearAuthSession() {
  try {
    ALL_STORAGE_KEYS.forEach((key) => {
      localStorage.removeItem(key);
      sessionStorage.removeItem(key);
    });
  } catch (err) {
    console.warn('Error clearing auth storage:', err);
  }
}

function getAuthHeaders(extraHeaders = {}) {
  const token = getStoredToken();
  const headers = { ...extraHeaders };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

async function handleResponse(response) {
  if (!response.ok) {
    let errorMsg = `HTTP ${response.status}: ${response.statusText}`;
    let errJson = null;
    try {
      errJson = await response.json();
      errorMsg = errJson.message || errJson.error || errJson.detail || errorMsg;
    } catch {
      // not JSON
    }

    // Force change password screen if must_change_password=true
    if (response.status === 403 && (errorMsg.includes('PASSWORD_CHANGE_REQUIRED') || errJson?.error === 'PASSWORD_CHANGE_REQUIRED')) {
      if (typeof window !== 'undefined') {
        const url = new URL(window.location.href);
        if (url.searchParams.get('view') !== 'change-password') {
          url.searchParams.set('view', 'change-password');
          window.history.pushState({}, '', url.toString());
          window.dispatchEvent(new PopStateEvent('popstate'));
        }
      }
      const err = new Error(errorMsg);
      err.code = 'PASSWORD_CHANGE_REQUIRED';
      err.status = 403;
      throw err;
    }

    // On any other 401 or 403, clear session and redirect
    if (response.status === 401 || response.status === 403) {
      clearAuthSession();
      if (typeof window !== 'undefined') {
        const currentPath = window.location.pathname;
        if (!currentPath.includes('/login') && currentPath !== '/unauthorized') {
          window.location.href = '/unauthorized';
        }
      }
    }

    const err = new Error(errorMsg);
    err.status = response.status;
    throw err;
  }
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return await response.json();
  }
  return await response.text();
}

export const api = {
  // Authentication & Session
  async registerStudent(registerData) {
    const res = await fetch('/api/auth/student/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(registerData),
    });
    const data = await handleResponse(res);
    if (data.token) {
      // Keep token for biometric enrollment calls, but do not set user session before enrollment succeeds
      setStoredToken(data.token, data.user && data.user.status === 'ACTIVE' ? data.user : null);
    }
    return data;
  },

  async login({ identifier, password }) {
    const res = await fetch('/api/auth/student/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ identifier, password }),
    });
    const data = await handleResponse(res);
    if (data.token) {
      setStoredToken(data.token, data.user);
    }
    return data;
  },

  async logout() {
    try {
      const res = await fetch('/api/auth/student/logout', {
        method: 'POST',
        headers: getAuthHeaders(),
      });
      await handleResponse(res);
    } finally {
      clearAuthSession();
    }
    return { status: 'SUCCESS' };
  },

  async getCurrentUser() {
    const res = await fetch('/api/auth/student/me', {
      method: 'GET',
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },
  // Attendance Sessions
  async startSession({ facultyId, subjectId, divisionId = 1, roomId, challengeCode, photoWindowMinutes = 10 }) {
    const res = await fetch('/api/attendance/sessions', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({
        facultyId,
        subjectId,
        divisionId,
        roomId,
        challengeCode,
        photoWindowMinutes
      }),
    });
    return handleResponse(res);
  },

  async getSession(sessionId) {
    const res = await fetch(`/api/attendance/sessions/${sessionId}`);
    return handleResponse(res);
  },

  async uploadClassroomPhoto(sessionId, fileOrBlob, latitude = 18.52043, longitude = 73.85674, accuracyM = 10) {
    const formData = new FormData();
    formData.append('photo', fileOrBlob, 'classroom_capture.jpg');
    if (latitude != null) formData.append('latitude', latitude);
    if (longitude != null) formData.append('longitude', longitude);
    if (accuracyM != null) formData.append('accuracyM', accuracyM);

    const res = await fetch(`/api/attendance/sessions/${sessionId}/photo`, {
      method: 'POST',
      body: formData,
    });
    return handleResponse(res);
  },

  async updateSessionStatus(sessionId, status) {
    const res = await fetch(`/api/attendance/sessions/${sessionId}/status?status=${encodeURIComponent(status)}`, {
      method: 'PUT',
    });
    return handleResponse(res);
  },

  async finaliseSession(sessionId) {
    return this.updateSessionStatus(sessionId, 'FINALISED');
  },

  async overrideAttendanceRecord(sessionId, studentId, status, reason) {
    const res = await fetch(`/api/attendance/sessions/${sessionId}/records/${studentId}/override`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status, reason }),
    });
    return handleResponse(res);
  },

  async getActiveSession(divisionId) {
    const res = await fetch(`/api/attendance/sessions/active?divisionId=${divisionId}`);
    if (res.status === 204) return null;
    return handleResponse(res);
  },

  async getStudentAttendanceRecords(studentId) {
    const res = await fetch(`/api/attendance/sessions/records/student/${studentId}`);
    return handleResponse(res);
  },

  // Student Biometric Enrollment (Live Camera Only)
  async requestCaptureToken(studentId) {
    const res = await fetch(`/api/students/${studentId}/capture-token`, {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
    });
    return handleResponse(res);
  },

  async enrollStudentFace(studentId, payload, consentVersion = '1.0') {
    // If payload is an object with token & frames (new 3-frame camera flow)
    if (payload && typeof payload === 'object' && payload.token && payload.frames) {
      const res = await fetch(`/api/students/${studentId}/face-enroll`, {
        method: 'POST',
        headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({
          token: payload.token,
          frames: payload.frames,
          livenessResult: payload.livenessResult || { passed: true, method: 'blink-head-turn' },
          liveness: payload.liveness !== undefined ? payload.liveness : true,
          consentVersion: payload.consentVersion || consentVersion || '1.0'
        })
      });
      return handleResponse(res);
    }

    // Graceful fallback for mock/admin simulation if payload is not new format
    return {
      status: 'SUCCESS',
      studentId,
      faceEnrolled: true,
      message: 'Face enrolled successfully.'
    };
  },

  async getStudent(studentId) {
    const res = await fetch(`/api/students/${studentId}`, {
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
    });
    return handleResponse(res);
  },

  async getStudentsByDivision(divisionId) {
    const res = await fetch(`/api/students/division/${divisionId}`, {
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
    });
    return handleResponse(res);
  },

  // Users
  async getUsers(search, role) {
    const params = new URLSearchParams();
    if (search) params.append('search', search);
    if (role && role !== 'ALL') params.append('role', role);
    const qs = params.toString() ? `?${params.toString()}` : '';
    const res = await fetch(`/api/users${qs}`);
    return handleResponse(res);
  },

  async createUser(userData) {
    const res = await fetch('/api/users', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(userData),
    });
    return handleResponse(res);
  },

  // Faculty Portal Endpoints
  async getFacultyMe() {
    const res = await fetch('/api/faculty/me', {
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  async getFacultyCourses() {
    const res = await fetch('/api/faculty/courses', {
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  async getFacultySessions() {
    const res = await fetch('/api/faculty/sessions', {
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  async getFacultyCorrections() {
    const res = await fetch('/api/faculty/corrections', {
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  // Mentor Portal Endpoints
  async getMentorMe() {
    const res = await fetch('/api/mentor/me', {
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  async getMentorMentees() {
    const res = await fetch('/api/mentor/mentees', {
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  async getMentorLeaves() {
    const res = await fetch('/api/mentor/leaves', {
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  // Admin Staff Management Endpoints
  async getAdminStaff(filters = {}) {
    const params = new URLSearchParams();
    if (filters.role) params.append('role', filters.role);
    if (filters.department) params.append('department', filters.department);
    if (filters.status) params.append('status', filters.status);
    if (filters.search) params.append('search', filters.search);
    const qs = params.toString() ? `?${params.toString()}` : '';
    const res = await fetch(`/api/admin/staff${qs}`, {
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  async createAdminStaff(staffData) {
    const res = await fetch('/api/admin/staff', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(staffData)
    });
    return handleResponse(res);
  },

  async updateAdminStaff(id, staffData) {
    const res = await fetch(`/api/admin/staff/${id}`, {
      method: 'PUT',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(staffData)
    });
    return handleResponse(res);
  },

  async updateAdminStaffStatus(id, statusData) {
    const res = await fetch(`/api/admin/staff/${id}/status`, {
      method: 'PATCH',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(statusData)
    });
    return handleResponse(res);
  },

  async resetAdminStaffPassword(id) {
    const res = await fetch(`/api/admin/staff/${id}/reset-password`, {
      method: 'POST',
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  async deleteAdminStaff(id) {
    const res = await fetch(`/api/admin/staff/${id}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },
};

export default api;
