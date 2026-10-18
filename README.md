# IntelliCampus

> **Intelligent College Campus Management & Verification Platform**  
> *Smart Attendance, Cryptographic Verifiable Credentials, Academic Governance & AI Early-Warning System.*

---

## 🏛️ Project Overview

**IntelliCampus** is an enterprise-grade academic operating system built to modernize campus attendance, credential issuance, academic record keeping, and student support. 

### Core System Modules
1. **Smart Attendance & Real Face Recognition**: Production InsightFace ArcFace 512-d embeddings, student biometric registration with DPDP Act 2023 consent, multi-angle classroom photo recognition (up to 5 photos per session), 1-to-1 bipartite student matching, and automated finalisation.
2. **Academic Grading & Multi-Tier Leave Workflow**: Draft/Publish marks lifecycle, HOD grade correction approval with cryptographic audit trails, and multi-tier leave approval pipelines.
3. **User Administration, Setup & Notices**: Bulk CSV ingestion with line-level validation, classroom geofence configuration, and targeted campus notices.
4. **Verifiable Credentials & Cryptographic Audit Ledger**: SHA-256 chained audit ledger ensuring immutable event logs, tamper detection, and public verifiable documents.
5. **AI Early-Warning System (EWS)**: FastAPI ML microservice identifying high-risk students based on attendance degradation, marks trends, and backlogs.

---

## 📡 Smart Attendance & Real Face Recognition Architecture

Full Specification: [**docs/ATTENDANCE.md**](docs/ATTENDANCE.md)

### Key Features
- **Production Face Recognition**: Powered by InsightFace (`buffalo_l`, ONNX Runtime CPU, ArcFace 512-d embeddings) with cosine similarity matching and Hungarian/greedy bipartite 1-to-1 student conflict resolution.
- **Biometric Enrollment with DPDP Act 2023 Consent**: Student facial photos captured on registration or via authenticated enrollment endpoint with explicit biometric consent timestamp and versioning.
- **Classroom Multi-Angle Photo Recognition**: Instructors capture up to 5 photos per open session to cover wide lecture halls. Raw classroom photos are never stored to preserve student privacy; only SHA-256 and pHash hashes are saved.
- **Decision Boundaries**: Similarity $\ge 0.45$ (with $\ge 0.05$ margin over second best) $\implies$ `PRESENT`; $[0.35, 0.45) \implies$ `REVIEW` (faculty Approve/Reject); $< 0.35 \implies$ unmatched.
- **Session Lifecycle Governance**: Unconfirmed students transition to `ABSENT` upon finalization with faculty manual override capabilities.

### Quick Start: Attendance & Biometric Enrollment
```bash
# Enroll student face photo
curl -X POST http://localhost:8080/api/students/10/face-enroll \
  -F "photo=@student_face.jpg" \
  -F "consentVersion=1.0"

# Start live attendance session
curl -X POST http://localhost:8080/api/attendance/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "facultyId": 1,
    "subjectId": 5,
    "divisionId": 3,
    "roomId": 7,
    "challengeCode": "K7Q2",
    "photoWindowMinutes": 10
  }'

# Upload classroom multi-face photo
curl -X POST http://localhost:8080/api/attendance/sessions/100/photo \
  -F "photo=@classroom.jpg" \
  -F "latitude=18.5204" \
  -F "longitude=73.8567"
```

---

## 📚 Academic Grading & Multi-Tier Leave Workflow

Full Specification: [**docs/ACADEMICS_AND_LEAVES.md**](docs/ACADEMICS_AND_LEAVES.md)

### Key Features
- **Marks Lifecycle Isolation**: Enforces `DRAFT` status for in-progress grading (hidden from student profiles) until published by faculty.
- **HOD Grade Correction Sign-Off**: Mandatory $\ge 15$-character justification for mark changes, recording every approval or rejection on the SHA-256 chained audit ledger.
- **Multi-Stage Leave Approval**: Structured state machine: `Student Application` $\rightarrow$ `Mentor Recommendation` $\rightarrow$ `HOD Final Approval`.
- **Medical Certification Policy**: Mandatory certificate upload for absences longer than 2 days.

### Quick Start: Submitting a Leave Application
```bash
# Student submits leave request
curl -X POST http://localhost:8080/api/leave/requests \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": 10,
    "fromDate": "2026-10-20",
    "toDate": "2026-10-23",
    "reason": "Recovery from seasonal viral fever",
    "attachmentName": "medical_certificate.pdf",
    "attachmentUrl": "https://storage.intellicampus.edu/med/cert_10.pdf"
  }'

# Mentor recommends leave
curl -X PUT http://localhost:8080/api/leave/requests/501/mentor-review \
  -H "Content-Type: application/json" \
  -d '{
    "approved": true,
    "approverId": 2,
    "status": "RECOMMENDED",
    "comment": "Doctor certificate verified."
  }'
```

---

## 📢 User Administration, Academic Setup & Campus Notices

Full Specification: [**docs/ADMIN_SETUP.md**](docs/ADMIN_SETUP.md)

### Key Features
- **Fault-Tolerant Bulk CSV Ingestion**: High-throughput student roster processing that isolates malformed records with line numbers while importing valid rows.
- **Classroom GPS Geofencing Administration**: High-precision facility coordinate configuration with configurable perimeter boundaries.
- **Hierarchical Notice Targeting**: Notice broadcasts filtered across `Department` $\times$ `Academic Year` $\times$ `Division` dimensions.
- **Auditable Read Receipts**: Real-time readership tracking and duplicate view prevention.

### Quick Start: Publishing a Targeted Notice
```bash
# Publish targeted notice for Computer Science Year 3
curl -X POST http://localhost:8080/api/notices \
  -H "Content-Type: application/json" \
  -d '{
    "title": "TE Computer Science Mini Project Synopsis Deadline",
    "body": "Submission portal closes Friday at 5:00 PM.",
    "targetDepartment": "Computer Science",
    "targetYear": "Year 3",
    "targetDivision": "All Divisions",
    "publisherRole": "HOD Office"
  }'

# Fetch student personalized notice feed
curl http://localhost:8080/api/notices/feed?userId=200
```

---

## 🔒 Cryptographic Audit Ledger & Verifiable Credentials

Full Specification: [**docs/SECURITY_AUDIT.md**](docs/SECURITY_AUDIT.md)

### Key Features
- **SHA-256 Chained Audit Ledger**: Canonical block hash linking ($entryHash = \text{SHA-256}(prevHash \mid \dots)$) detecting historical payload tampering down to a single modified character.
- **Privacy-Preserving Verifiable Credentials**: Public verification via signed QR codes with automated student name masking (`A*** R*** M***`).
- **Enforced Revocation Policy**: Mandatory $\ge 10$-character administrative justification recorded immutably on the audit chain.
- **AI Early-Warning System (EWS)**: Machine learning heuristics computing multi-factor risk scores and extracting explainable factors.

### Quick Start: Verifying Audit Chain & Credentials
```bash
# Verify cryptographic hash chain integrity
curl http://localhost:8080/api/audit/chain/verify

# Public document verification
curl http://localhost:8080/api/credentials/public/DOC-2026-A1B2C3D4E5

# Evaluate student retention risk via AI pipeline
curl -X POST http://localhost:8080/api/ai/risk/evaluate/10
```

---

## 🛠️ Technology Stack

- **Backend**: Java 17, Spring Boot 3.3.4, Spring Data JPA, Spring Security
- **Database**: MySQL 8 with Flyway migrations
- **Frontend**: React 19, Vite, Vanilla CSS, Lucide Icons
- **AI Microservice**: Python, FastAPI, Scikit-Learn
- **Containerization**: Docker, Docker Compose

---

## 🎓 Student-Only Portal & Authentication

IntelliCampus is configured by default as a **Student-Only Portal** for student workflows: attendance verification, academic marks viewing, multi-tier leave submission, campus notices, verifiable credentials, and AI document chat.

### Student-Only Mode Configuration (`VITE_STUDENT_ONLY`)
The portal mode is governed by the Vite environment flag `VITE_STUDENT_ONLY`:
- **`VITE_STUDENT_ONLY=true` (Default)**:
  - Portal is exclusively accessible to students.
  - Heading displays **Student Portal** on login.
  - Staff workflows (`AdminView`, `HodView`, `FacultyView`, `MentorView`, `PublicVerifierView`, and `AuditChainModal`) are completely skipped from rendering.
  - The Navbar role workflow switcher, Audit Chain button, and Live session challenge badge are removed.
  - Navbar retains: branding, campus notice alerts, Ask AI chatbot, theme toggle, and logged-in student user chip with Logout button.
  - Only students can authenticate (`AuthService.login` rejects non-student logins with the generic error `"Invalid login code or password."` to prevent role enumeration).
  - Any non-student session detected in `sessionStorage` is automatically cleared.
- **`VITE_STUDENT_ONLY=false`**:
  - Restores the multi-role environment allowing team members to switch between Admin, HOD, Faculty, Mentor, Student, and Public Verifier workflows.

#### How to Switch Portal Modes
Create or edit your local `.env` file (or export in environment):
```bash
# To run in Student-Only mode (default):
VITE_STUDENT_ONLY=true

# To switch back to legacy multi-role staff mode:
VITE_STUDENT_ONLY=false
```
Then restart the development server or rebuild:
```bash
npm run dev
# or for production build
npm run build
```

### Student Demo Accounts
Login codes are case-insensitive. Default password for all seeded demo accounts is **`CampusPass2026!`** (any non-empty password is accepted in offline mock simulation):

| Login Code | Student Name | Role | Department & Academic Placement |
| :--- | :--- | :--- | :--- |
| `student.aarav` | Aarav Shah | `STUDENT` | Computer Science, Year 3 (TE), Div A (Roll: CS-104) |
| `student.priya` | Priya Nair | `STUDENT` | Computer Science, Year 3 (TE), Div A (Roll: CS-105) |
| `student.rohan` | Rohan Deshmukh | `STUDENT` | Computer Science, Year 3 (TE), Div A (Roll: CS-106) |
| `student.kavya` | Kavya Iyer | `STUDENT` | Computer Science, Year 3 (TE), Div A (Roll: CS-107) |

*(Note: Seeded staff accounts `prof.verma`, `hod.sharma`, `dr.mehta`, and `admin.patel` remain preserved in the backend database for team development, but are blocked from logging in when student-only mode is active).*

---

## 🧪 Automated Testing

Run the automated test suite:

```bash
# Frontend & Component Unit Tests (33 passing tests)
npm test

# Production Build Validation
npm run build
```

---

## 👥 Engineering Team & Task Allocation

| Member | Role | Assigned Module | Issues |
| :--- | :--- | :--- | :---: |
| **Bhumika Khairnar** | Team Leader & PR Reviewer | Smart Attendance & Multimodal Verification | #2 – #7 |
| **Siddhi Kolage** | Developer | Academic Grading & Multi-Tier Leave Workflow | #8 – #13 |
| **Gayatri Jadhav** | Developer | User Administration, Academic Setup & Campus Notices | #14 – #19 |
| **Vaishnavi Bodakhe** | Repo Owner | Verifiable Credentials, Cryptographic Audit & AI | #20 – #25 |

See [**TASKS.md**](TASKS.md) for live GitHub issues tracking.
