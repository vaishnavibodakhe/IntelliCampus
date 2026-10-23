/**
 * IntelliCampus REST API Client
 * Connects frontend to Spring Boot backend via Vite proxy (/api -> http://localhost:8080)
 */

const TOKEN_STORAGE_KEY = 'intellicampus_auth_token';
const USER_STORAGE_KEY = 'intellicampus_auth_user';

export function getStoredToken(roleContext = 'staff') {
  try {
    const staffSessionRaw = localStorage.getItem('intellicampus_staff_session');
    const studentToken = localStorage.getItem(TOKEN_STORAGE_KEY);

    if (roleContext === 'student') {
      if (studentToken) return studentToken;
      if (staffSessionRaw) {
        const parsed = JSON.parse(staffSessionRaw);
        return parsed.accessToken || parsed.token || null;
      }
    } else {
      if (staffSessionRaw) {
        const parsed = JSON.parse(staffSessionRaw);
        const staffToken = parsed.accessToken || parsed.token;
        if (staffToken) return staffToken;
      }
      if (studentToken) return studentToken;
    }
    return null;
  } catch {
    return null;
  }
}

export function setStoredToken(token, user = null) {
  try {
    if (token) {
      localStorage.setItem(TOKEN_STORAGE_KEY, token);
    } else {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
    }
    if (user) {
      localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
    } else if (token === null) {
      localStorage.removeItem(USER_STORAGE_KEY);
    }
  } catch (err) {
    console.warn('Storage access unavailable:', err);
  }
}

export function getStoredUser() {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function clearAuthSession() {
  try {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
  } catch {}
}

function getAuthHeaders(extraHeaders = {}, roleContext = 'staff') {
  const token = getStoredToken(roleContext);
  const headers = { ...extraHeaders };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
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
    throw new Error(errorMsg);
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
        headers: getAuthHeaders({}, 'student'),
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
      headers: getAuthHeaders({}, 'student'),
    });
    return handleResponse(res);
  },
  // Attendance Sessions
  async startSession({ facultyId = 3, subjectId, divisionId = 1, roomId, challengeCode, photoWindowMinutes = 10 }) {
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
    const res = await fetch(`/api/attendance/sessions/${sessionId}`, {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async uploadClassroomPhoto(sessionId, fileOrBlob, latitude = 18.52043, longitude = 73.85674, accuracyM = 10, sessionToken = null) {
    const formData = new FormData();
    formData.append('photo', fileOrBlob, 'classroom_capture.jpg');
    if (latitude != null) formData.append('latitude', latitude);
    if (longitude != null) formData.append('longitude', longitude);
    if (accuracyM != null) formData.append('accuracyM', accuracyM);

    const headers = getAuthHeaders(sessionToken ? { 'X-Session-Token': sessionToken } : {});

    const res = await fetch(`/api/attendance/sessions/${sessionId}/photo`, {
      method: 'POST',
      headers,
      body: formData,
    });
    return handleResponse(res);
  },

  async updateSessionStatus(sessionId, status) {
    const res = await fetch(`/api/attendance/sessions/${sessionId}/status?status=${encodeURIComponent(status)}`, {
      method: 'PUT',
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async finaliseSession(sessionId) {
    return this.updateSessionStatus(sessionId, 'FINALISED');
  },

  async overrideAttendanceRecord(sessionId, studentId, status, reason) {
    const res = await fetch(`/api/attendance/sessions/${sessionId}/records/${studentId}/override`, {
      method: 'PUT',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ status, reason }),
    });
    return handleResponse(res);
  },

  async getActiveSession(divisionId) {
    const res = await fetch(`/api/attendance/sessions/active?divisionId=${divisionId}`, {
      headers: getAuthHeaders(),
    });
    if (res.status === 204) return null;
    return handleResponse(res);
  },

  async getStudentAttendanceRecords(studentId) {
    const res = await fetch(`/api/attendance/sessions/records/student/${studentId}`, {
      headers: getAuthHeaders({}, 'student'),
    });
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
    // If payload is an object with token & frames (3-frame camera flow)
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

    throw new Error('Biometric face enrollment requires valid camera frames and a one-time capture token.');
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

  async searchStudents(query = '') {
    const params = new URLSearchParams();
    params.append('role', 'STUDENT');
    if (query && query.trim()) {
      params.append('search', query.trim());
    }
    const res = await fetch(`/api/users?${params.toString()}`, {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  // Academic Setup (Rooms & Subjects)
  async getRooms() {
    const res = await fetch('/api/academic/rooms', {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async createRoom(roomData) {
    const res = await fetch('/api/academic/rooms', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(roomData),
    });
    return handleResponse(res);
  },

  async getSubjects() {
    const res = await fetch('/api/academic/subjects', {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async createSubject(subjectData) {
    const res = await fetch('/api/academic/subjects', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(subjectData),
    });
    return handleResponse(res);
  },

  // Academic Marks & Corrections
  async saveMark(markData) {
    const res = await fetch('/api/marks', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(markData),
    });
    return handleResponse(res);
  },

  async getStudentMarks(studentId, onlyPublished = true) {
    const res = await fetch(`/api/marks/student/${studentId}?onlyPublished=${onlyPublished}`, {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async getSubjectMarks(subjectId) {
    const res = await fetch(`/api/marks/subject/${subjectId}`, {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async requestCorrection(correctionData) {
    const res = await fetch('/api/marks/corrections', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(correctionData),
    });
    return handleResponse(res);
  },

  async getPendingCorrections() {
    const res = await fetch('/api/marks/corrections/pending', {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async decideCorrection(correctionId, decisionData) {
    const res = await fetch(`/api/marks/corrections/${correctionId}/decision`, {
      method: 'PUT',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(decisionData),
    });
    return handleResponse(res);
  },

  // Leaves & Permissions
  async submitLeave(leaveData) {
    const res = await fetch('/api/leaves', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(leaveData),
    });
    return handleResponse(res);
  },

  async getAllLeaves() {
    const res = await fetch('/api/leaves', {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async getStudentLeaves(studentId) {
    const res = await fetch(`/api/leaves/student/${studentId}`, {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async getPendingMentorLeaves(mentorId) {
    const res = await fetch(`/api/leaves/mentor/${mentorId}/pending`, {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async getPendingHodLeaves() {
    const res = await fetch('/api/leaves/hod/pending', {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async mentorReviewLeave(leaveId, decision) {
    const res = await fetch(`/api/leaves/${leaveId}/mentor`, {
      method: 'PUT',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(decision),
    });
    return handleResponse(res);
  },

  async hodReviewLeave(leaveId, decision) {
    const res = await fetch(`/api/leaves/${leaveId}/hod`, {
      method: 'PUT',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(decision),
    });
    return handleResponse(res);
  },

  // Verifiable Credentials
  async getAllDocuments() {
    const res = await fetch('/api/credentials', {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async getStudentDocuments(studentId) {
    const res = await fetch(`/api/credentials/student/${studentId}`, {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async issueDocument(docData) {
    const res = await fetch('/api/credentials/issue', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(docData),
    });
    return handleResponse(res);
  },

  async revokeDocument(code, revokeData) {
    const res = await fetch(`/api/credentials/${encodeURIComponent(code)}/revoke`, {
      method: 'PUT',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(revokeData),
    });
    return handleResponse(res);
  },

  async verifyDocument(code) {
    const res = await fetch(`/api/public/verify/${encodeURIComponent(code)}`);
    return handleResponse(res);
  },

  // Cryptographic Audit Chain
  async getAuditChain() {
    const res = await fetch('/api/audit/chain', {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async verifyAuditChain() {
    const res = await fetch('/api/audit/verify', {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  // AI & Risk Assessment
  async getHighRiskStudents() {
    const res = await fetch('/api/ai/risk/high', {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async getLatestStudentRisk(studentId) {
    const res = await fetch(`/api/ai/risk/student/${studentId}/latest`, {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  // Notices & Circulars
  async getMyNoticeFeed(userId) {
    const uid = userId !== undefined && userId !== null ? userId : getStoredUser()?.id;
    const qs = uid !== undefined && uid !== null ? `?userId=${encodeURIComponent(uid)}` : '';
    const res = await fetch(`/api/notices/my-feed${qs}`, {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async publishNotice(noticeData) {
    const res = await fetch('/api/notices', {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(noticeData),
    });
    return handleResponse(res);
  },

  async markNoticeRead(noticeId, userId) {
    const uid = userId !== undefined && userId !== null ? userId : getStoredUser()?.id;
    const qs = uid !== undefined && uid !== null ? `?userId=${encodeURIComponent(uid)}` : '';
    const res = await fetch(`/api/notices/${encodeURIComponent(noticeId)}/read${qs}`, {
      method: 'POST',
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },

  async listNotices() {
    const res = await fetch('/api/notices', {
      headers: getAuthHeaders(),
    });
    return handleResponse(res);
  },
};

// Named function exports for flexible imports
export async function getMyNoticeFeed(userId) {
  return api.getMyNoticeFeed(userId);
}

export async function publishNotice(noticeData) {
  return api.publishNotice(noticeData);
}

export async function markNoticeRead(noticeId, userId) {
  return api.markNoticeRead(noticeId, userId);
}

export async function searchStudents(query) {
  return api.searchStudents(query);
}

export async function listNotices() {
  return api.listNotices();
}

export default api;
