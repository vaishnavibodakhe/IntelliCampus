/**
 * IntelliCampus REST API Client
 * Connects frontend to Spring Boot backend via Vite proxy (/api -> http://localhost:8080)
 */

export function getToken() {
  try {
    return sessionStorage.getItem('intellicampus_jwt_token');
  } catch (e) {
    return null;
  }
}

export function setToken(token) {
  try {
    if (token) {
      sessionStorage.setItem('intellicampus_jwt_token', token);
    } else {
      sessionStorage.removeItem('intellicampus_jwt_token');
    }
  } catch (e) {
    console.warn('Failed to store token in sessionStorage:', e);
  }
}

export function clearToken() {
  try {
    sessionStorage.removeItem('intellicampus_jwt_token');
  } catch (e) {
    console.warn('Failed to clear token from sessionStorage:', e);
  }
}

export async function authFetch(url, options = {}) {
  const token = getToken();
  const headers = new Headers(options.headers || {});
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  return fetch(url, { ...options, headers });
}

async function handleResponse(response) {
  if (!response.ok) {
    let errorMsg = `HTTP ${response.status}: ${response.statusText}`;
    try {
      const errJson = await response.json();
      errorMsg = errJson.message || errJson.error || errJson.detail || errorMsg;
    } catch {
      // not JSON
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
  // Authentication & Self-Service
  async login({ loginCode, password, portal = 'STUDENT', expectedRole }) {
    const res = await authFetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 
        loginCode, 
        password,
        portal: portal || expectedRole || 'STUDENT',
        expectedRole: expectedRole || portal || 'STUDENT'
      }),
    });
    return handleResponse(res);
  },

  async register(formData) {
    const res = await authFetch('/api/auth/register', {
      method: 'POST',
      // Notice: No 'Content-Type' header when body is FormData
      body: formData,
    });
    return handleResponse(res);
  },

  async forgotPassword({ loginCodeOrEmail }) {
    const res = await authFetch('/api/auth/forgot-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ loginCodeOrEmail }),
    });
    return handleResponse(res);
  },

  async resetPassword({ code, newPassword, confirmPassword, loginCode }) {
    const res = await authFetch('/api/auth/reset-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code, newPassword, confirmPassword, loginCode }),
    });
    return handleResponse(res);
  },

  // Attendance Sessions
  async startSession({ facultyId = 3, subjectId, divisionId = 1, roomId, challengeCode, photoWindowMinutes = 10 }) {
    const res = await authFetch('/api/attendance/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
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
    const res = await authFetch(`/api/attendance/sessions/${sessionId}`);
    return handleResponse(res);
  },

  async uploadClassroomPhoto(sessionId, fileOrBlob, latitude = 18.52043, longitude = 73.85674, accuracyM = 10) {
    const formData = new FormData();
    formData.append('photo', fileOrBlob, 'classroom_capture.jpg');
    if (latitude != null) formData.append('latitude', latitude);
    if (longitude != null) formData.append('longitude', longitude);
    if (accuracyM != null) formData.append('accuracyM', accuracyM);

    const res = await authFetch(`/api/attendance/sessions/${sessionId}/photo`, {
      method: 'POST',
      body: formData,
    });
    return handleResponse(res);
  },

  async updateSessionStatus(sessionId, status) {
    const res = await authFetch(`/api/attendance/sessions/${sessionId}/status?status=${encodeURIComponent(status)}`, {
      method: 'PUT',
    });
    return handleResponse(res);
  },

  async finaliseSession(sessionId) {
    return this.updateSessionStatus(sessionId, 'FINALISED');
  },

  async overrideAttendanceRecord(sessionId, studentId, status, reason) {
    const res = await authFetch(`/api/attendance/sessions/${sessionId}/records/${studentId}/override`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status, reason }),
    });
    return handleResponse(res);
  },

  async getActiveSession(divisionId) {
    const res = await authFetch(`/api/attendance/sessions/active?divisionId=${divisionId}`);
    if (res.status === 204) return null;
    return handleResponse(res);
  },

  async getStudentAttendanceRecords(studentId) {
    const res = await authFetch(`/api/attendance/sessions/records/student/${studentId}`);
    return handleResponse(res);
  },

  // Student Biometric Enrollment
  async enrollStudentFace(studentId, fileOrBlob, consentVersion = '1.0') {
    const formData = new FormData();
    formData.append('photo', fileOrBlob, `student_${studentId}.jpg`);
    formData.append('consentVersion', consentVersion);

    const res = await authFetch(`/api/students/${studentId}/face-enroll`, {
      method: 'POST',
      body: formData,
    });
    return handleResponse(res);
  },

  getStudentFacePhotoUrl(studentId, requesterId) {
    return `/api/students/${studentId}/face-photo${requesterId ? `?requesterId=${requesterId}` : ''}`;
  },

  async getStudent(studentId) {
    const res = await authFetch(`/api/students/${studentId}`);
    return handleResponse(res);
  },

  async getStudentsByDivision(divisionId) {
    const res = await authFetch(`/api/students/division/${divisionId}`);
    return handleResponse(res);
  },

  // Users
  async getUsers(search, role) {
    const params = new URLSearchParams();
    if (search) params.append('search', search);
    if (role && role !== 'ALL') params.append('role', role);
    const qs = params.toString() ? `?${params.toString()}` : '';
    const res = await authFetch(`/api/users${qs}`);
    return handleResponse(res);
  },

  async createUser(userData) {
    const res = await authFetch('/api/users', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(userData),
    });
    return handleResponse(res);
  },
};

export default api;
