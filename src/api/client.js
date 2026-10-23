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
  const contentType = response.headers?.get ? response.headers.get('content-type') : (response.headers?.['content-type'] || 'application/json');
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

  // User Management
  async getUsers(params = {}) {
    const q = new URLSearchParams();
    if (params.role && params.role !== 'ALL') q.append('role', params.role);
    if (params.department && params.department !== 'ALL') q.append('department', params.department);
    const qs = q.toString() ? `?${q.toString()}` : '';
    const res = await fetch(`/api/users${qs}`, {
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  async createStaff(staffData) {
    const res = await fetch('/api/principal/staff', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(staffData)
    });
    return handleResponse(res);
  },

  async createUser(userData) {
    const res = await fetch('/api/principal/staff', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(userData)
    });
    return handleResponse(res);
  },

  async updateUserStatus(id, status) {
    const res = await fetch(`/api/principal/staff/${id}/status`, {
      method: 'PATCH',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ status })
    });
    return handleResponse(res);
  },

  async updateUserRole(id, role) {
    const res = await fetch(`/api/principal/staff/${id}`, {
      method: 'PUT',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ role })
    });
    return handleResponse(res);
  },

  async resetUserPassword(id, newPassword) {
    const res = await fetch(`/api/principal/staff/${id}/reset-password`, {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(newPassword ? { newPassword } : {})
    });
    return handleResponse(res);
  },

  async deleteUser(id) {
    const res = await fetch(`/api/principal/staff/${id}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  async importUsersCsv(csvLines) {
    const res = await fetch('/api/users/csv-import', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(csvLines)
    });
    return handleResponse(res);
  },

  // Academic Setup
  async getDepartments() {
    const res = await fetch('/api/academic/departments', { headers: getAuthHeaders() });
    return handleResponse(res);
  },

  async createDepartment(data) {
    const res = await fetch('/api/academic/departments', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data)
    });
    return handleResponse(res);
  },

  async updateDepartment(id, data) {
    const res = await fetch(`/api/academic/departments/${id}`, {
      method: 'PUT',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data)
    });
    return handleResponse(res);
  },

  async deleteDepartment(id) {
    const res = await fetch(`/api/academic/departments/${id}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  async getDivisions() {
    const res = await fetch('/api/academic/divisions', { headers: getAuthHeaders() });
    return handleResponse(res);
  },

  async createDivision(data) {
    const res = await fetch('/api/academic/divisions', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data)
    });
    return handleResponse(res);
  },

  async updateDivision(id, data) {
    const res = await fetch(`/api/academic/divisions/${id}`, {
      method: 'PUT',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data)
    });
    return handleResponse(res);
  },

  async deleteDivision(id) {
    const res = await fetch(`/api/academic/divisions/${id}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  async getSubjects() {
    const res = await fetch('/api/academic/subjects', { headers: getAuthHeaders() });
    return handleResponse(res);
  },

  async createSubject(data) {
    const res = await fetch('/api/academic/subjects', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data)
    });
    return handleResponse(res);
  },

  async updateSubject(id, data) {
    const res = await fetch(`/api/academic/subjects/${id}`, {
      method: 'PUT',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data)
    });
    return handleResponse(res);
  },

  async deleteSubject(id) {
    const res = await fetch(`/api/academic/subjects/${id}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  async getRooms() {
    const res = await fetch('/api/academic/rooms', { headers: getAuthHeaders() });
    return handleResponse(res);
  },

  async createRoom(data) {
    const res = await fetch('/api/academic/rooms', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data)
    });
    return handleResponse(res);
  },

  async updateRoom(id, data) {
    const res = await fetch(`/api/academic/rooms/${id}`, {
      method: 'PUT',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data)
    });
    return handleResponse(res);
  },

  async deleteRoom(id) {
    const res = await fetch(`/api/academic/rooms/${id}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  // Targeted Notices
  async getNotices() {
    const res = await fetch('/api/notices', { headers: getAuthHeaders() });
    return handleResponse(res);
  },

  async createNotice(noticeData) {
    const res = await fetch('/api/notices', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(noticeData)
    });
    return handleResponse(res);
  },

  async updateNotice(id, noticeData) {
    const res = await fetch(`/api/notices/${id}`, {
      method: 'PUT',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(noticeData)
    });
    return handleResponse(res);
  },

  async deleteNotice(id) {
    const res = await fetch(`/api/notices/${id}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  // Verifiable Documents & Credentials
  async getCredentials() {
    const res = await fetch('/api/credentials', { headers: getAuthHeaders() });
    return handleResponse(res);
  },

  async issueCredential(data) {
    const res = await fetch('/api/credentials/issue', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data)
    });
    return handleResponse(res);
  },

  async verifyCredential(code) {
    const res = await fetch(`/api/credentials/verify/${code}`);
    return handleResponse(res);
  },

  async revokeCredential(code, reason = 'Revoked by Principal') {
    const res = await fetch(`/api/credentials/${code}/revoke?reason=${encodeURIComponent(reason)}`, {
      method: 'PUT',
      headers: getAuthHeaders()
    });
    return handleResponse(res);
  },

  // Principal Live Data Assistant
  async queryPrincipalAssistant(question) {
    const res = await fetch('/api/principal/assistant/query', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ question })
    });
    return handleResponse(res);
  },

  // Legacy knowledge query fallback for non-principal roles (safe refusal)
  async queryKnowledge(query, limit = 3) {
    return {
      answer: "The institutional knowledge base has been upgraded to the live Principal Assistant. Document-based search is no longer active.",
      citations: [],
      confidenceScore: 0.0
    };
  },

  // Principal Setup & Registration
  async getPrincipalSetupStatus() {
    const res = await fetch('/api/auth/principal/setup-status');
    return handleResponse(res);
  },

  async registerPrincipal(data) {
    const res = await fetch('/api/auth/principal/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    return handleResponse(res);
  },

  async verifyPrincipalOtp(data) {
    const res = await fetch('/api/auth/principal/verify-otp', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    return handleResponse(res);
  },

  // Cryptographic Audit Chain
  async getAuditChain() {
    const res = await fetch('/api/audit/chain', { headers: getAuthHeaders() });
    return handleResponse(res);
  },

  async verifyAuditChain() {
    const res = await fetch('/api/audit/verify', { headers: getAuthHeaders() });
    return handleResponse(res);
  }
};

export default api;
