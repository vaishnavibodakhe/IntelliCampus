# 🔌 IntelliCampus: REST API Specification

This specification documents the complete REST API interface implemented across **IntelliCampus** as defined in Section 7 of the Software Requirements Specification (IC-SRS-1.0).

---

## 📋 General API Conventions

- **Base Path**: `/api`
- **Data Format**: `application/json; charset=UTF-8` (Except image/PDF uploads which use `multipart/form-data`)
- **Authentication**: `Authorization: Bearer <JWT_ACCESS_TOKEN>`
- **Token Refresh**: Automatic via HTTP-only secure cookie on `POST /api/auth/refresh`
- **Timestamps**: ISO 8601 in UTC (e.g. `2026-10-08T09:30:12Z`)
- **Pagination**: Query parameters `?page=0&size=20` (max size: 100). Responses return standard envelope:
  ```json
  {
    "items": [...],
    "page": 0,
    "size": 20,
    "total": 142
  }
  ```
- **Error Response Envelope**:
  ```json
  {
    "timestamp": "2026-10-08T09:30:12Z",
    "status": 422,
    "code": "OUTSIDE_GEOFENCE",
    "message": "Device location is 83 meters from the classroom center (allowed: 50m).",
    "path": "/api/sessions/51/photo"
  }
  ```

---

## 🔐 1. Authentication & Security Endpoints

### 1.1 User Login
- **Endpoint**: `POST /api/auth/login`
- **Access**: Public
- **Request**:
  ```json
  {
    "userCode": "FAC_CS_101",
    "password": "Password123!"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "userCode": "FAC_CS_101",
    "name": "Prof. Alan Turing",
    "role": "FACULTY",
    "mustChangePassword": false
  }
  ```
- **Error Codes**: `401 INVALID_CREDENTIALS`, `423 ACCOUNT_LOCKED`

### 1.2 Refresh Access Token
- **Endpoint**: `POST /api/auth/refresh`
- **Access**: Public (Requires refresh token cookie)
- **Response** (`200 OK`):
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
  ```

### 1.3 Force / Change Password
- **Endpoint**: `POST /api/auth/password`
- **Access**: Logged In
- **Request**:
  ```json
  {
    "oldPassword": "InitialPassword123!",
    "newPassword": "NewStrongPassword456!"
  }
  ```
- **Response**: `204 No Content`
- **Error Codes**: `400 WEAK_PASSWORD`

---

## 👨‍💼 2. Administration & Setup Endpoints

### 2.1 Bulk User CSV Import
- **Endpoint**: `POST /api/admin/users/import`
- **Access**: Admin
- **Request**: `multipart/form-data` with file parameter `file` (CSV format)
- **Response** (`200 OK`):
  ```json
  {
    "createdCount": 48,
    "failedCount": 2,
    "errors": [
      { "rowNumber": 14, "reason": "Duplicate roll number CS2026-014" },
      { "rowNumber": 29, "reason": "Invalid email format" }
    ]
  }
  ```

---

## 📸 3. Biometrics & Attendance Pipeline Endpoints

### 3.1 Face Enrollment (Explicit Student Consent)
- **Endpoint**: `POST /api/students/{id}/face`
- **Access**: Student (Self) or Admin
- **Request**: `multipart/form-data`
  - `frontImage`: image file
  - `leftImage`: image file
  - `rightImage`: image file
  - `consent`: `true`
  - `consentVersion`: `"v1.0-DPDP-2026"`
- **Response** (`201 Created`):
  ```json
  {
    "studentId": 1042,
    "enrolled": true,
    "consentAt": "2026-10-08T09:30:12Z"
  }
  ```
- **Error Codes**: `400 NO_CONSENT`, `400 NO_FACE_DETECTED`

### 3.2 Start Classroom Attendance Session
- **Endpoint**: `POST /api/sessions/start`
- **Access**: Faculty
- **Request**:
  ```json
  {
    "subjectId": 12,
    "divisionId": 4,
    "roomId": 7
  }
  ```
- **Response** (`201 Created`):
  ```json
  {
    "sessionId": 51,
    "challengeCode": "K7Q2",
    "photoWindowEndsAt": "2026-10-08T09:40:00Z"
  }
  ```
- **Error Codes**: `409 ACTIVE_SESSION_EXISTS`

### 3.3 Upload Classroom Snapshot
- **Endpoint**: `POST /api/sessions/{id}/photo`
- **Access**: Faculty
- **Request**: `multipart/form-data`
  - `image`: image file
  - `lat`: `18.5204`
  - `lng`: `73.8567`
  - `accuracy`: `12.5`
- **Response** (`202 Accepted`):
  ```json
  {
    "status": "PROCESSING",
    "message": "Photo accepted for facial recognition & anti-spoofing analysis"
  }
  ```
- **Error Codes**: `410 WINDOW_CLOSED`, `422 OUTSIDE_GEOFENCE`, `422 DUPLICATE_PHOTO`, `422 NEAR_DUPLICATE_PHOTO`

### 3.4 Query Session Match Results
- **Endpoint**: `GET /api/sessions/{id}/result`
- **Access**: Faculty
- **Response** (`200 OK`):
  ```json
  {
    "sessionId": 51,
    "status": "REVIEW",
    "confirmed": [{ "studentId": 101, "name": "Alice Smith", "score": 0.94 }],
    "matchedUnconfirmed": [{ "studentId": 102, "name": "Bob Jones", "score": 0.88 }],
    "review": [{ "studentId": 103, "name": "Charlie Brown", "reason": "LOW_SPOOF_SCORE" }],
    "absent": [{ "studentId": 104, "name": "Diana Prince" }]
  }
  ```

### 3.5 Student 2-Minute Confirmation
- **Endpoint**: `POST /api/attendance/{sessionId}/confirm`
- **Access**: Student
- **Request**:
  ```json
  {
    "lat": 18.5204,
    "lng": 73.8567,
    "accuracy": 15.0,
    "deviceId": "dev_fp_9a8b7c6d5e4f"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "confirmed": true,
    "status": "PRESENT"
  }
  ```
- **Error Codes**: `403 DEVICE_NOT_APPROVED`, `410 WINDOW_CLOSED`, `422 OUTSIDE_GEOFENCE`

### 3.6 Rotating Dynamic QR Attendance Fallback
- **Endpoint**: `POST /api/attendance/{sessionId}/qr`
- **Access**: Student
- **Request**:
  ```json
  {
    "qrToken": "K7Q2-hmac-98124b",
    "lat": 18.5204,
    "lng": 73.8567,
    "deviceId": "dev_fp_9a8b7c6d5e4f"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "status": "PRESENT_QR"
  }
  ```
- **Error Codes**: `410 EXPIRED_QR_TOKEN`

### 3.7 Manual Faculty Attendance Override
- **Endpoint**: `POST /api/attendance/{sessionId}/override`
- **Access**: Faculty
- **Request**:
  ```json
  {
    "studentId": 103,
    "newStatus": "PRESENT",
    "reason": "Camera missed student sitting in shaded rear corner of room."
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "studentId": 103,
    "status": "PRESENT",
    "auditLogId": 9842
  }
  ```
- **Error Codes**: `400 REASON_REQUIRED`

### 3.8 Finalize Attendance Session
- **Endpoint**: `POST /api/sessions/{id}/finalise`
- **Access**: Faculty
- **Response** (`200 OK`):
  ```json
  {
    "sessionId": 51,
    "status": "FINALISED",
    "counts": {
      "present": 45,
      "review": 1,
      "absent": 4
    }
  }
  ```

---

## 📝 4. Marks & Tamper-Evident Audit Endpoints

### 4.1 Record / Edit Marks
- **Endpoint**: `POST /api/marks`
- **Access**: Faculty
- **Request**:
  ```json
  {
    "subjectId": 12,
    "exam": "MIDTERM_1",
    "marksList": [
      { "studentId": 101, "marks": 24.5, "maxMarks": 30.0 }
    ]
  }
  ```
- **Response** (`200 OK`):
  ```json
  { "savedCount": 1 }
  ```
- **Error Codes**: `403 UNASSIGNED_SUBJECT`, `409 MARKS_PUBLISHED`

### 4.2 Submit Mark Correction Request
- **Endpoint**: `POST /api/marks/{id}/correction`
- **Access**: Faculty
- **Request**:
  ```json
  {
    "newMarks": 28.0,
    "reason": "Re-evaluation of Question 4 following student grievance."
  }
  ```
- **Response** (`202 Accepted`):
  ```json
  {
    "correctionId": 301,
    "status": "PENDING_HOD_APPROVAL"
  }
  ```

### 4.3 HOD Authorize / Reject Grade Correction
- **Endpoint**: `PUT /api/marks/corrections/{id}/decision`
- **Access**: HOD
- **Request**:
  ```json
  {
    "decision": "APPROVED",
    "comment": "Verified answer paper scan; score discrepancy confirmed."
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "correctionId": 301,
    "status": "APPROVED",
    "auditEntryId": 1205
  }
  ```

### 4.4 Verify Cryptographic Audit Chain Integrity
- **Endpoint**: `GET /api/audit/verify`
- **Access**: Admin
- **Response** (`200 OK`):
  ```json
  {
    "status": "INTACT",
    "totalBlocksVerified": 10420,
    "verifiedAt": "2026-10-08T09:30:12Z"
  }
  ```
  *(If altered)*:
  ```json
  {
    "status": "BROKEN",
    "firstBrokenId": 8412,
    "brokenAt": "2026-10-08T09:30:12Z"
  }
  ```

---

## 🤖 5. Grounded RAG Chatbot Endpoints

### 5.1 Ask Knowledge Chatbot
- **Endpoint**: `POST /api/chat/ask`
- **Access**: Logged In
- **Request**:
  ```json
  {
    "question": "What is the penalty fee for submitting the exam form after October 15?",
    "language": "en"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "questionId": 501,
    "answer": "According to the Examination Circular, late submission between October 16 and October 20 incurs a late fee of ₹500.",
    "sources": [
      {
        "documentName": "Exam_Ordinance_Autumn_2026.pdf",
        "page": 4,
        "relevanceScore": 0.89
      }
    ]
  }
  ```

---

## 📜 6. Verifiable Credentials & Leave Endpoints

### 6.1 Issue Tamper-Evident Document
- **Endpoint**: `POST /api/documents/issue`
- **Access**: Admin
- **Request**:
  ```json
  {
    "studentId": 101,
    "type": "FEE_RECEIPT",
    "data": { "term": "Fall 2026", "amountPaid": 45000, "txnRef": "TXN981241" }
  }
  ```
- **Response** (`201 Created`):
  ```json
  {
    "documentCode": "DOC-2026-89AF12",
    "verificationUrl": "https://intellicampus.edu/verify/DOC-2026-89AF12",
    "fileHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
  }
  ```

### 6.2 Public Document Verification
- **Endpoint**: `GET /api/verify/{code}`
- **Access**: Public (Unauthenticated)
- **Response** (`200 OK`):
  ```json
  {
    "status": "VALID",
    "documentCode": "DOC-2026-89AF12",
    "documentType": "FEE_RECEIPT",
    "holderNameMasked": "J*** D**",
    "issueDate": "2026-10-01"
  }
  ```

### 6.3 Submit Leave Request
- **Endpoint**: `POST /api/leave`
- **Access**: Student
- **Request**:
  ```json
  {
    "fromDate": "2026-10-12",
    "toDate": "2026-10-14",
    "reason": "Attending national inter-college hackathon event.",
    "attachmentUrl": "/uploads/leave/hackathon_invite.pdf"
  }
  ```
- **Response** (`201 Created`):
  ```json
  {
    "leaveId": 801,
    "status": "SUBMITTED"
  }
  ```
