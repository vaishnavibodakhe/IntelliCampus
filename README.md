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

## 💻 Run on a New Laptop (Setup Guide)

Full Guide: [**docs/SETUP.md**](docs/SETUP.md)

### 1. Prerequisites
- **Java JDK 17+**
- **Node.js 18+ / 20+ (LTS)** and **npm**
- **MySQL Server 8.0+**

### 2. MySQL Setup
Run in MySQL:
```sql
CREATE DATABASE IF NOT EXISTS intellicampus_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'campus_admin'@'localhost' IDENTIFIED BY 'your_db_password_here';
GRANT ALL PRIVILEGES ON intellicampus_db.* TO 'campus_admin'@'localhost';
FLUSH PRIVILEGES;
```

### 3. Environment Variables (.env)
Copy the example file:
```bash
# Windows
copy .env.example .env
# macOS / Linux
cp .env.example .env
```
Fill in your Gmail credentials in `.env`:
- `MAIL_USERNAME=your-email@gmail.com`
- `MAIL_PASSWORD=your16charpassword` *(Generate at Google Account > Security > 2-Step Verification > App passwords; remove spaces)*.

### 4. Start Backend
```bash
cd backend
.\mvnw.cmd spring-boot:run   # Windows
# or ./mvnw spring-boot:run  # macOS / Linux
```
*Expected log lines:*
- `[DOTENV] Successfully loaded 5 properties from .env`
- `SMTP auth check: OK`
- `Tomcat started on port 8080 (http)`

### 5. Start Frontend
```bash
# In the root repository directory:
npm install
npm run dev
```
Open [http://localhost:5173](http://localhost:5173).

### 6. Testing Admin/HOD Register, Login & Forgot-Password OTP
1. On `http://localhost:5173`, select **Admin** or **HOD** and register using a real email inbox.
2. Log in with your new account.
3. Test **Forgot Password**: request a 6-digit OTP, check your inbox (and Spam folder), enter the OTP, and set a new password.

### 7. Troubleshooting
- **502 Bad Gateway (`Failed to request OTP (502)`)**: Backend is not running on port 8080. Start backend via `.\mvnw.cmd spring-boot:run`.
- **535 Authentication Failed (`SMTP auth check: FAILED (535)`)**: Incorrect 16-letter Gmail App Password or 2-Step Verification disabled.
- **"Success message, but no email arrived"**: Email not registered under that specific role (`ADMIN` vs `HOD`), or the message went into the **Spam** folder.


---

## 🧪 Automated Testing

Run the automated test suite:

```bash
# Frontend & Component Unit Tests (28 passing tests)
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
