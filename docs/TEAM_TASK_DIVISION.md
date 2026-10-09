# 👥 IntelliCampus: 4-Collaborator Task Division & Role Ownership

This document defines the official engineering division of work across **4 team collaborators** for the **IntelliCampus** capstone project. Each collaborator owns a designated **User Role**, corresponding **Full-Stack Modules (Backend + Frontend + Database)**, and specific **AI/Security integrations**.

---

## 📌 High-Level Responsibility Matrix

```
+--------------------------------------------------------------------------------------------------------------------+
|                                      TEAM WORKLOAD DISTRIBUTION OVERVIEW                                           |
+-------------------+----------------------------+-------------------------------------+-----------------------------+
| Collaborator      | Assigned User Role         | Primary Functional Domains          | Specialized Technologies    |
+-------------------+----------------------------+-------------------------------------+-----------------------------+
| Collaborator 1    | Admin & System Lead        | Auth, RBAC, Academic Setup, Audit   | Spring Security, JWT, DB    |
| Collaborator 2    | Faculty & Vision Lead      | Attendance Capture, Marks, Overrides| InsightFace, Anti-Spoof, CV |
| Collaborator 3    | Student & AI Chatbot Lead  | Attendance Confirm, Dynamic QR, RAG | Spring AI, pgvector, STOMP  |
| Collaborator 4    | Mentor, HOD & Analytics    | Early Warning ML, Leave, Governance | Scikit-Learn, Random Forest |
+-------------------+----------------------------+-------------------------------------+-----------------------------+
```

---

## 🧑‍💻 Collaborator 1: Admin & Platform Infrastructure Lead

> **Primary User Role:** `ADMIN`  
> **Secondary Scope:** `PUBLIC VERIFIER`  
> **Core Focus:** Institutional Administration, Authentication, Infrastructure & Cryptographic Auditing

### 1. Backend Modules & APIs Owned
- **`com.intellicampus.auth`**:
  - User login (`POST /api/auth/login`) with BCrypt salted password verification.
  - JWT token provider: 15-minute access token + 7-day HTTP-only refresh token rotation (`POST /api/auth/refresh`).
  - Account lockout service (5 failed attempts $\rightarrow$ 15-minute lock).
  - Password complexity policy & forced initial password change (`POST /api/auth/password`).
- **`com.intellicampus.admin`**:
  - Bulk user creation via CSV parsing with row-by-row error reporting (`POST /api/admin/users/import`).
  - Academic hierarchy configuration (Departments, Divisions, Subjects, Classrooms).
  - Classroom geofencing coordinates setup (Latitude, Longitude, Radius in meters).
  - Mentor-to-student mapping engine (ensures exactly 1 mentor per student).
- **`com.intellicampus.documents`**:
  - Verifiable document generator (Fee receipts, bonafide certificates) with SHA-256 file digest.
  - Document revocation mechanism with mandatory reason (`POST /api/documents/{code}/revoke`).
  - Unauthenticated public verification endpoint (`GET /api/verify/{code}`).
- **`com.intellicampus.audit`**:
  - Hash-chain engine: $\text{SHA-256}(\text{prev\_hash} + \dots + \text{created\_at})$.
  - Cryptographic integrity checker (`GET /api/audit/verify`) scanning entire database chain.

### 2. Database Tables Owned
- `users`, `departments`, `divisions`, `subjects`, `rooms`, `issued_documents`, `audit_logs`

### 3. Frontend Screens Owned
- **S-01**: Login Screen (with account lockout countdown)
- **S-02**: Force Password Change Modal
- **S-03**: Admin Dashboard & Metrics Overview
- **S-04**: User Management & CSV Import Uploader
- **S-05**: Academic Setup (Interactive Map Pin for Room GPS)
- **S-19**: Document Issuance & Template Generator
- **S-20**: Public Document Verification Portal (Zero-login QR scanner)

### 4. Key Milestones
- **Weeks 1–4**: Project Docker Compose initialization, Flyway base migrations, Spring Security JWT filter.
- **Weeks 5–8**: User CSV bulk import, Academic structure CRUD, Audit hash-chain engine.
- **Weeks 9–14**: Verifiable PDF issuance, public verification endpoint, end-to-end admin testing.

---

## 🧑‍💻 Collaborator 2: Faculty & Vision Attendance Lead

> **Primary User Role:** `FACULTY`  
> **Core Focus:** Classroom Attendance Pipeline, Computer Vision AI, Grade Entry & Override Auditing

### 1. Backend Modules & APIs Owned
- **`com.intellicampus.attendance` (Faculty Side)**:
  - Session lifecycle: Launch session with random 4-character challenge code (`POST /api/sessions/start`).
  - Classroom snapshot ingest pipeline (`POST /api/sessions/{id}/photo`):
    - 10-minute validity window check (`WINDOW_CLOSED`).
    - Haversine device geofence validation ($\le 100\text{ m}$ accuracy, $d \le \text{radius}$).
    - Image deduplication: SHA-256 byte check + 64-bit DCT perceptual hash (pHash Hamming distance $\le 8$).
  - Result consolidation: Segmenting class into Confirmed, Matched-Unconfirmed, Review, Absent (`GET /api/sessions/{id}/result`).
  - Manual override endpoint requiring $\ge 10$-character reason (`POST /api/attendance/{sessionId}/override`).
  - Session finalization and lock (`POST /api/sessions/{id}/finalise`).
- **`com.intellicampus.marks` (Faculty Side)**:
  - Internal marks recording grid and CSV batch upload (`POST /api/marks`).
  - Mark lock-on-publish mechanism (`MARKS_PUBLISHED`).
  - Mark correction request submission with mandatory justification (`POST /api/marks/{id}/correction`).

### 2. AI & Vision Integration Owned
- **FastAPI Microservice (`ai-service/app/api/analyze.py`)**:
  - Integration with **InsightFace Buffalo_l (ArcFace)** for multi-face detection and 512-dim embedding extraction.
  - Multi-scale CNN anti-spoofing detector (scores liveness; $< 0.60$ flagged as review).
  - Division-scoped cosine vector search using PostgreSQL `pgvector`.
  - Ephemeral memory handling: guarantees raw classroom images are discarded immediately after inference.

### 3. Database Tables Owned
- `students` (face embeddings), `attendance_sessions`, `session_photos`, `attendance_flags`, `mark_entries`

### 4. Frontend Screens Owned
- **S-06**: Faculty Dashboard & Daily Lecture Schedule
- **S-07**: Session Launch Screen (displays challenge code to write on classroom board)
- **S-08**: In-App Live Camera Capture (file upload strictly disabled to block photo spoofing)
- **S-09**: Attendance Review (4-tab interface, face crop thumbnails, override drawer)
- **S-12**: Marks Entry Grid & CSV Grade Importer

### 5. Key Milestones
- **Weeks 5–8**: Faculty dashboard, session creation, challenge code generator.
- **Weeks 8–13**: Python FastAPI InsightFace service, live camera component, geofence Haversine algorithm.
- **Weeks 14–17**: Marks entry, lock on publish, manual override audit logging.

---

## 🧑‍💻 Collaborator 3: Student Experience & AI Chatbot Lead

> **Primary User Role:** `STUDENT`  
> **Core Focus:** Mobile Attendance Confirmation, Dynamic QR Scanner, Targeted Notices & Grounded RAG Chatbot

### 1. Backend Modules & APIs Owned
- **`com.intellicampus.attendance` (Student Side)**:
  - 120-second active confirmation endpoint (`POST /api/attendance/{sessionId}/confirm`).
  - Student device fingerprinting binding (First device auto-approved, secondary devices require mentor approval).
  - Student geolocation validation inside room radius at the moment of confirmation.
  - Dynamic rotating QR attendance fallback (`POST /api/attendance/{sessionId}/qr`).
  - Subject-wise attendance aggregation and defaulter flag identification ($< 75\%$).
  - Attendance dispute logging (`POST /api/attendance/{recordId}/dispute`).
- **`com.intellicampus.chatbot` (RAG Engine)**:
  - Institutional PDF ingestion & chunking (500 tokens, 50 token overlap) with `pgvector` indexing.
  - Query embedding & cosine similarity retrieval (top 5 passages).
  - Spring AI / LLM orchestration ($T=0$, strict citations of document name + page, polite refusal on low confidence).
  - Multilingual support for English, Hindi, and Marathi (`POST /api/chat/ask`).
  - Chat answer rating feedback (`POST /api/chat/{id}/rating`).
- **`com.intellicampus.notice`**:
  - Targeted audience filter engine (by Department, Year, Division).
  - Student read receipts tracking and automated 24h deadline reminder task.

### 2. Real-time & Caching Integration Owned
- **WebSocket Gateway (`/ws`)**:
  - STOMP broadcasting to student private queues (`/user/queue/attendance`, `/user/queue/notify`).
- **Redis Cache (QR Token Engine)**:
  - HMAC-SHA256 15-second rotating classroom token generator and single-use validation cache.

### 3. Database Tables Owned
- `devices`, `attendance_records`, `notices`, `notice_reads`, `knowledge_documents`, `document_chunks`, `chat_messages`

### 4. Frontend Screens Owned
- **S-10**: Student Attendance Dashboard (subject-wise percentages, defaulter badges, dispute button)
- **S-11**: Confirm Attendance Screen (120s countdown, GPS confirmation, dynamic QR scanner)
- **S-14**: Campus Circulars & Notices Feed (with deadline badges & read acknowledgments)
- **S-15**: Grounded RAG Chatbot Interface (with collapsible source document/page citations)
- **S-16**: Knowledge Base Manager (PDF document upload & indexing status)

### 5. Key Milestones
- **Weeks 5–8**: Student portal dashboard, device fingerprinting, subject-wise attendance stats.
- **Weeks 8–13**: WebSocket STOMP listener, 120s countdown modal, html5-qrcode camera scanner.
- **Weeks 14–19**: Spring AI RAG pipeline, pgvector chunking, multilingual query handling.

---

## 🧑‍💻 Collaborator 4: Mentorship, Governance & Risk Analytics Lead

> **Primary User Roles:** `MENTOR` & `HEAD OF DEPARTMENT (HOD)`  
> **Core Focus:** Predictive Early Warning ML (EWS), Multi-Tier Leave Management, Department Governance & Defaulter Audits

### 1. Backend Modules & APIs Owned
- **`com.intellicampus.risk` (Early Warning System)**:
  - Weekly scheduled risk cron job computing feature vectors across all active students:
    1. Cumulative attendance percentage
    2. 4-week delta in attendance ($\Delta \text{Attn}$)
    3. Internal marks average percentage
    4. Term-over-term marks trend ($\Delta \text{Marks}$)
    5. Active backlog count
    6. Prior 30-day leave frequency
  - Integration with Python `/ai/risk` service returning risk scores ($0.0 - 1.0$), risk tiers (`LOW`, `MEDIUM`, `HIGH`), and top 3 contributing factors.
  - Mentor alert generation upon status transition to High Risk.
  - Mentor intervention note logging (`POST /api/risk/{scoreId}/action`).
- **`com.intellicampus.leave` (Multi-Tier Approval Chain)**:
  - Digital leave application submission (`POST /api/leave`).
  - Two-stage decision workflow (`PUT /api/leave/{id}/decision`):
    - Leaves $\le 3$ days: Handled completely by Mentor.
    - Leaves $> 3$ days: Evaluated by Mentor $\rightarrow$ forwarded to HOD for final approval.
  - Automatic attendance recalculation: Approved leave dates are excluded from attendance absence denominators.
- **`com.intellicampus.marks` (HOD Governance)**:
  - Review and decision on faculty mark correction requests (`PUT /api/marks/corrections/{id}/decision`).
  - Departmental reporting engine: Defaulter lists ($<75\%$), attendance distributions, and CSV/PDF report exports.
  - Flagged session inspection engine (`attendance_flags`).

### 2. Machine Learning Model Owned
- **FastAPI Microservice (`ai-service/app/api/risk.py`)**:
  - Scikit-Learn **Random Forest Classifier** trained on student academic progression data.
  - Baseline **Logistic Regression** model for explainability.
  - Feature importance / Tree SHAP module synthesizing human-readable risk factor explanations for mentors.

### 3. Database Tables Owned
- `risk_scores`, `risk_actions`, `leave_requests`, `leave_steps`, departmental analytics views

### 4. Frontend Screens Owned
- **S-17**: Mentee Risk Analytics Dashboard (Cohort view categorized by Low/Medium/High risk)
- **S-18**: Student Risk Detail View (Top 3 risk drivers, historical metrics, mentor intervention note modal)
- **S-21**: Apply for Leave (Date range picker, medical certificate upload, timeline tracker)
- **S-22**: Multi-Tier Leave Approvals Queue (Mentor & HOD evaluation views with comments)
- **S-23**: Flagged Session Inspection Dashboard (Detects duplicate photos, location jumps, low spoof scores)
- **S-13**: HOD / Admin Audit Trail Explorer & Mark Correction Review

### 5. Key Milestones
- **Weeks 8–13**: Multi-tier leave state machine, automatic attendance deduction logic.
- **Weeks 14–18**: Scikit-Learn EWS model, weekly scheduled batch evaluation, mentor alert dashboard.
- **Weeks 19–23**: HOD mark correction workflow, departmental PDF/CSV reporting, anomaly flag viewer.

---

## 📅 Cross-Collaborator Sprint & Integration Plan

```
Sprint 1 (Weeks 1-4)   : Foundations (Collaborator 1: Auth & DB | Collaborators 2,3,4: Specs & UI wireframes)
Sprint 2 (Weeks 5-8)   : Core Modules (C1: Admin Setup | C2: Faculty Sessions | C3: Student Portal | C4: Leave System)
Sprint 3 (Weeks 9-13)  : Attendance & Vision Integration (C2: InsightFace | C3: WebSocket & QR | C1: Audit Hash | C4: Attendance Normalization)
Sprint 4 (Weeks 14-18) : Intelligence Layer (C3: Spring AI RAG | C4: Scikit-Learn EWS | C2: Marks Lock | C1: Verifiable PDFs)
Sprint 5 (Weeks 19-22) : Department Governance & Auditing (C4: HOD Approvals & Defaulters | C1: Audit Verifier | C2/C3: Anomaly Flags)
Sprint 6 (Weeks 23-24) : Joint Testing, Load Benchmarking (JMeter), Final Report & Viva Presentation
```
