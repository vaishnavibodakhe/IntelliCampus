# Smart Attendance & Real Face Recognition Specification

**Module**: Smart Attendance & Facial Biometric Verification  
**Lead Engineer**: Bhumika Khairnar (`@khairnarbhumika34-ship-it` | `member-1`)  
**Standard**: IC-SRS-1.0 Section 8.2 & 8.6 | Test Cases TC-02, TC-03, TC-04  
**Biometric Compliance**: DPDP Act 2023 (Digital Personal Data Protection Act)

---

## 1. Architectural Overview

The **Smart Attendance & Real Face Recognition System** replaces legacy QR codes and mock simulations with production-grade AI biometric recognition powered by **InsightFace (buffalo_l, ONNX Runtime CPU, ArcFace 512-d embeddings)**.

### Core Architectural Pillars:
1. **Biometric Face Enrollment with Explicit Consent**:
   - Captured at student registration (webcam or file upload) or via authenticated enrollment endpoint `POST /api/students/{id}/face-enroll`.
   - Explicit DPDP Act 2023 consent timestamped (`consent_at`) and versioned (`consent_version`).
   - Enrollment photo stored in isolated storage (`app.storage.face-dir`, default `./uploads/student-faces`), accessible only to `ADMIN` and `HOD` via authenticated endpoint.
   - `ai-service` detects exactly one face, validates sharpness (Laplacian variance $\ge 10.0$) and minimum size ($\ge 40\text{px}$), returning a normalized 512-dimensional vector stored in `students.face_embedding`.
2. **Multi-Angle Classroom Photo Recognition**:
   - Instructors capture up to 5 wide-angle classroom photos per open session (`POST /api/attendance/sessions/{id}/photo`).
   - Raw classroom photos are **never stored on disk** for student privacy; only SHA-256 and pHash perceptual hashes are recorded in `session_photos`.
   - `ai-service` detects all faces in the classroom and matches them against division student embeddings using cosine similarity.
3. **1-to-1 Bipartite Student Conflict Resolution**:
   - Prevents duplicate student check-ins. If multiple detected faces correlate with the same enrolled student, a greedy/Hungarian bipartite assignment matches the candidate with the highest similarity.
4. **Configurable Similarity Thresholds & Borderline REVIEW**:
   - $\text{similarity} \ge \text{threshold}$ (default $0.45$) and margin $\ge 0.05$ over second candidate $\implies$ `PRESENT`.
   - $\text{similarity} \in [\text{threshold} - 0.10, \text{threshold}) \implies$ `REVIEW` (flagged for faculty inline Approve/Reject).
   - $\text{similarity} < \text{threshold} - 0.10 \implies$ unmatched.
   - Monotonic status guarantee: records already marked `PRESENT` or manually overridden are never downgraded.
5. **Automated Finalisation & Manual Override**:
   - On session finalisation (`PUT /api/attendance/sessions/{id}/status?status=FINALISED`), all unconfirmed/unmatched records transition to `ABSENT`.
   - Faculty members can override any record with a mandatory justification of at least 10 characters.

---

## 2. Mathematical & Biometric Foundations

### 2.1. ArcFace Cosine Similarity
Face recognition compares the 512-dimensional normalized ArcFace embedding $\mathbf{u} \in \mathbb{R}^{512}$ of a detected face against enrolled student embeddings $\mathbf{v}_i \in \mathbb{R}^{512}$:

$$\text{similarity}(\mathbf{u}, \mathbf{v}_i) = \frac{\mathbf{u} \cdot \mathbf{v}_i}{\|\mathbf{u}\|_2 \|\mathbf{v}_i\|_2} = \mathbf{u} \cdot \mathbf{v}_i \quad (\text{since } \|\mathbf{u}\|_2 = \|\mathbf{v}_i\|_2 = 1)$$

### 2.2. Decision Boundaries & Margin Rule
For the highest candidate match $k = \arg\max_i \text{similarity}(\mathbf{u}, \mathbf{v}_i)$ with primary similarity $s_1$ and secondary similarity $s_2$:

$$\text{status} = \begin{cases}
\text{PRESENT}, & s_1 \ge \theta_{\text{match}} \land (s_1 - s_2) \ge \delta_{\text{margin}} \\
\text{REVIEW}, & (\theta_{\text{match}} - 0.10) \le s_1 < \theta_{\text{match}} \\
\text{UNMATCHED}, & s_1 < (\theta_{\text{match}} - 0.10)
\end{cases}$$

Where default configuration in `application.yml`:
- $\theta_{\text{match}} = 0.45$ (`app.attendance.face-match-threshold`)
- $\delta_{\text{margin}} = 0.05$ (`app.attendance.face-match-margin`)

### 2.3. Geofence Distance (Preserved Utility)
Physical location coordinates use the Haversine formula to verify campus room vicinity ($R = 6,371,000 \text{ m}$):

$$d = 2 R \cdot \arcsin\left(\sqrt{\sin^2\left(\frac{\Delta\phi}{2}\right) + \cos(\phi_1)\cos(\phi_2)\sin^2\left(\frac{\Delta\lambda}{2}\right)}\right)$$

---

## 3. End-to-End Workflow Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Admin / Faculty
    actor Student as Student
    participant UI as IntelliCampus Frontend
    participant API as Spring Boot Backend
    participant AI as Python FastAPI (InsightFace)
    participant DB as MySQL Database
    participant Disk as Secure Face Dir (Docker Vol)

    Note over Admin,Disk: Step 1: Student Biometric Enrollment
    Admin->>UI: Upload student face photo + check DPDP consent
    UI->>API: POST /api/students/{id}/face-enroll (photo, consentVersion="1.0")
    API->>API: Validate JPEG/PNG & size <= 15MB
    API->>AI: POST /ai/enroll (multipart: photo)
    AI->>AI: InsightFace buffalo_l detection
    alt 0 faces or >1 faces or blurry
        AI-->>API: 400 Bad Request ("Expected exactly 1 face")
        API-->>UI: 400 Bad Request
    else Exactly 1 valid face
        AI-->>API: 200 OK (512-d embedding JSON, quality=0.96)
        API->>Disk: Save photo as student_{id}.jpg
        API->>DB: UPDATE student_profiles SET face_enrolled=true, face_embedding=JSON, face_photo_path=path, consent_at=NOW()
        API-->>UI: 200 OK (StudentProfileDto)
    end

    Note over Admin,API: Step 2: Live Classroom Attendance Session
    Admin->>UI: Select Subject, Room, Division & Click "Start Session"
    UI->>API: POST /api/attendance/sessions (facultyId, roomId, divisionId, photoWindow=10)
    API->>DB: INSERT attendance_session (status='OPEN', challengeCode='K7Q2')
    API->>DB: Populate attendance_records for division students (status='PENDING')
    API-->>UI: 201 Created (SessionResponseDto)

    Note over UI,AI: Step 3: Classroom Multi-Photo Face Recognition
    Admin->>UI: Capture classroom photo via camera / upload file
    UI->>API: POST /api/attendance/sessions/{id}/photo (multipart: classroom photo)
    API->>DB: Verify session is OPEN & within photoWindow (<= 5 photos)
    API->>DB: Compute SHA-256 & pHash -> INSERT session_photos
    API->>DB: Load enrolled student embeddings for session division
    API->>AI: POST /ai/recognize (image + candidate embeddings)
    AI->>AI: ArcFace multi-face detection & cosine similarity matrix
    AI->>AI: Greedy/Hungarian 1-to-1 student bipartite resolution
    AI-->>API: 200 OK (facesDetected, matchedStudentId, similarity, secondSimilarity)
    API->>API: Apply thresholds: sim >= 0.45 -> PRESENT; [0.35, 0.45) -> REVIEW
    API->>DB: UPDATE attendance_records (finalStatus, faceScore, confirmed, statusReason)
    API-->>UI: 200 OK (PhotoRecognitionResultDto: detected, matched, review, unmatched)

    Note over UI,DB: Step 4: Finalisation & Roster Lock
    Admin->>UI: Review borderline rows (Approve / Reject) or Finalise Session
    UI->>API: PUT /api/attendance/sessions/{id}/status?status=FINALISED
    API->>DB: UPDATE attendance_session SET status='FINALISED', finalised_at=NOW()
    API->>DB: UPDATE attendance_records SET final_status='ABSENT' WHERE final_status != 'PRESENT'
    API-->>UI: 200 OK (Roster finalized and locked)
```

---

## 4. REST API Endpoint Reference

### 4.1. Student Face Biometric Enrollment
Enrolls or re-enrolls a student's facial template for ArcFace recognition.

- **Method**: `POST`
- **Path**: `/api/students/{studentId}/face-enroll`
- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `photo`: File (JPEG or PNG, $\le 15\text{MB}$)
  - `consentVersion`: String (default `"1.0"`)
- **Response (`200 OK`)**: `StudentProfileDto` with `faceEnrolled: true`
- **Errors**:
  - `400 Bad Request`: Photo contains 0 faces, multiple faces, is blurry, or format invalid.

---

### 4.2. Download Student Face Photo (Admin/HOD Only)
Retrieves the stored student face photo. Strictly restricted to authorized roles.

- **Method**: `GET`
- **Path**: `/api/students/{studentId}/face-photo`
- **Query Parameter**: `requesterId` (User ID of requester)
- **Response (`200 OK`)**: Image stream (`image/jpeg` or `image/png`)
- **Errors**:
  - `403 Forbidden`: Requester is not `ADMIN` or `HOD`.
  - `404 Not Found`: Student has not enrolled a photo.

---

### 4.3. Start Attendance Session
Creates a new live attendance session and initializes division student records as `PENDING`.

- **Method**: `POST`
- **Path**: `/api/attendance/sessions`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
```json
{
  "facultyId": 1,
  "subjectId": 5,
  "divisionId": 3,
  "roomId": 7,
  "challengeCode": "K7Q2",
  "photoWindowMinutes": 10
}
```
- **Response (`201 Created`)**: `SessionResponseDto`

---

### 4.4. Upload Classroom Photo & Run Recognition
Processes a classroom multi-face photograph during an open session.

- **Method**: `POST`
- **Path**: `/api/attendance/sessions/{id}/photo`
- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `photo`: Classroom photo file (`image/jpeg` or `image/png`)
  - `latitude`: Double (optional)
  - `longitude`: Double (optional)
  - `accuracyM`: Double (optional)
- **Response (`200 OK`)**:
```json
{
  "sessionId": 100,
  "photoId": 1,
  "photoIndex": 1,
  "totalPhotos": 1,
  "facesDetected": 24,
  "studentsMatched": 21,
  "studentsInReview": 2,
  "unmatchedFaceCount": 1,
  "message": "Classroom photo processed successfully: 24 faces detected, 21 matched, 2 in review."
}
```

---

### 4.5. Finalise Session & Lock Roster
Finalises the attendance session and marks unconfirmed students `ABSENT`.

- **Method**: `PUT`
- **Path**: `/api/attendance/sessions/{id}/status?status=FINALISED`
- **Response (`200 OK`)**: `SessionResponseDto` with locked counts.

---

### 4.6. Faculty Manual Attendance Override
Enables instructors to override a student's attendance record with audit justification.

- **Method**: `PUT`
- **Path**: `/api/attendance/sessions/{id}/records/{studentId}/override`
- **Request Body**:
```json
{
  "status": "PRESENT",
  "reason": "Faculty confirmed student was seated in back row; shadow prevented ArcFace detection"
}
```
- **Response (`200 OK`)**: `AttendanceRecordResponseDto`

---

## 5. Automated Test Suites Reference

All test suites verify zero regression and complete removal of QR code logic:
- **Backend Unit Tests**: [`AttendanceServiceTest.java`](file:///c:/Users/Bhumika/IntelliCampus/backend/src/test/java/com/intellicampus/attendance/AttendanceServiceTest.java) (66 tests passed)
- **Backend Enrollment Tests**: [`FaceEnrollmentServiceTest.java`](file:///c:/Users/Bhumika/IntelliCampus/backend/src/test/java/com/intellicampus/user/service/FaceEnrollmentServiceTest.java)
- **AI Microservice Tests**: [`test_ai_service.py`](file:///c:/Users/Bhumika/IntelliCampus/ai-service/tests/test_ai_service.py) (7/7 tests passed via `pytest`)
- **Frontend Test Suite**: [`FacultyView.test.js`](file:///c:/Users/Bhumika/IntelliCampus/src/components/__tests__/FacultyView.test.js) (28 tests passed via `node --test`)
