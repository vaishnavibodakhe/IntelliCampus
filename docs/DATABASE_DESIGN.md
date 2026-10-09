# 🗄️ IntelliCampus: Database Design & Schema Specification

This document details the relational data model, vector database extensions, and audit integrity architecture for **IntelliCampus**, corresponding to Section 6 of the Software Requirements Specification (IC-SRS-1.0).

---

## 📐 Entity Relationship (ER) Overview

```
                                  +-------------------+
                                  |    Department     |
                                  +---------+---------+
                                            | 1
                                            | has
                                            | N
                                  +---------v---------+
                      +---------->|     Division      |
                      | 1         +---------+---------+
                      |                     | 1
                      | has                 | has
                      |                     | N
                      |           +---------v---------+
                      |           |      Student      |
                      |           +----+----+----+----+
                      |                |    |    |
           +----------+          1:N   |    |    | 1:N
           | Mentor   | <--------------+    |    +-------------> [ Mark Entries ]
           | (Faculty)|                     |
           +----------+                     | 1:N
                                            v
                                     [ Leave Requests ]

                                  +-------------------+
                                  | AttendanceSession |
                                  +---------+---------+
                                            |
                         +------------------+------------------+
                         | 1:N                                 | 1:N
                         v                                     v
                 [ SessionPhotos ]                    [ AttendanceRecords ]
                         |                                     |
                         v                                     v
                 (SHA-256 / pHash)                      (PRESENT / ABSENT)

                                  +-------------------+
                                  |    AuditLogs      |
                                  +-------------------+
                                  (Cryptographic Chain:
                                   prev_hash -> entry_hash)
```

---

## 📑 Complete Data Dictionary

All primary keys use `bigint GENERATED ALWAYS AS IDENTITY`. Timestamps default to `TIMESTAMPTZ` in UTC.

### 1. `users`
Core identity table supporting role-based access control.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | `PRIMARY KEY` | Unique identity integer. |
| `user_code` | `varchar(20)` | `UNIQUE, NOT NULL` | Login identifier (e.g. `ADM001`, `FAC104`, `STU2026042`). |
| `role` | `varchar(12)` | `NOT NULL` | Enum: `ADMIN`, `HOD`, `FACULTY`, `STUDENT`. |
| `name` | `varchar(100)` | `NOT NULL` | Full legal name. |
| `email` | `varchar(120)` | `UNIQUE, NOT NULL` | Institutional email address. |
| `password_hash` | `varchar(100)`| `NOT NULL` | Salted BCrypt password hash. |
| `must_change_password`| `boolean`| `DEFAULT TRUE` | Forces password change upon first authentication. |
| `status` | `varchar(10)` | `DEFAULT 'ACTIVE'` | Status: `ACTIVE`, `DISABLED`, `LOCKED`. |
| `failed_attempts` | `int` | `DEFAULT 0` | Consecutive failed login counter. |
| `locked_until` | `timestamptz` | `NULL` | Lockout expiration timestamp. |
| `created_at` | `timestamptz` | `DEFAULT NOW()` | Record creation timestamp. |
| `updated_at` | `timestamptz` | `DEFAULT NOW()` | Record update timestamp. |

---

### 2. `students`
Student profile metadata, division associations, and biometric enrollment.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | `PRIMARY KEY` | Unique student record ID. |
| `user_id` | `bigint` | `UNIQUE, FK -> users(id)` | Foreign key to core user identity. |
| `roll_no` | `varchar(20)` | `UNIQUE, NOT NULL` | Official university roll number. |
| `division_id` | `bigint` | `FK -> divisions(id)` | Academic division grouping. |
| `mentor_id` | `bigint` | `FK -> users(id)` | Assigned faculty mentor (exactly one mentor per student). |
| `face_embedding` | `vector(512)` | `NULL` | Encrypted 512-dim ArcFace embedding vector. |
| `consent_at` | `timestamptz` | `NULL` | Timestamp when biometric consent was granted. |
| `consent_version` | `varchar(20)` | `NULL` | Legal consent policy revision string. |

---

### 3. `devices`
Registered browser and device fingerprints for student attendance confirmation.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | `PRIMARY KEY` | Device identifier. |
| `student_id` | `bigint` | `FK -> students(id)` | Owner student foreign key. |
| `fingerprint` | `varchar(128)`| `NOT NULL` | Cryptographic hash of client device attributes. |
| `label` | `varchar(60)` | `NULL` | User device label (e.g., "OnePlus 11 Chrome"). |
| `approved` | `boolean` | `DEFAULT FALSE` | First device auto-approved; secondary devices require mentor approval. |
| `approved_by` | `bigint` | `FK -> users(id)` | Mentor who approved device. |

---

### 4. `rooms`
Institutional classrooms and laboratory spaces with geofencing coordinates.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | `PRIMARY KEY` | Classroom identifier. |
| `name` | `varchar(40)` | `NOT NULL` | Room designation (e.g. "Lab 302", "LH-1"). |
| `latitude` | `double precision`| `NOT NULL` | Geometric latitude center. |
| `longitude`| `double precision`| `NOT NULL` | Geometric longitude center. |
| `radius_m` | `int` | `DEFAULT 50` | Geofence perimeter in meters (Range: 10m to 200m). |

---

### 5. `attendance_sessions`
Classroom attendance tracking sessions created by faculty.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | `PRIMARY KEY` | Session ID. |
| `faculty_id` | `bigint` | `FK -> users(id)` | Faculty conducting session. |
| `subject_id` | `bigint` | `FK -> subjects(id)` | Subject/course identifier. |
| `division_id` | `bigint` | `FK -> divisions(id)`| Academic class division. |
| `room_id` | `bigint` | `FK -> rooms(id)` | Designated classroom. |
| `started_at` | `timestamptz` | `DEFAULT NOW()` | Server session start time. |
| `challenge_code`| `varchar(8)`| `NOT NULL` | Ephemeral classroom display code (e.g. "K7Q2"). |
| `status` | `varchar(12)` | `DEFAULT 'OPEN'` | Status: `OPEN`, `PROCESSING`, `REVIEW`, `FINALISED`. |
| `finalised_at` | `timestamptz` | `NULL` | Lock timestamp. |

---

### 6. `session_photos`
Metadata and cryptographic deduplication digests of uploaded classroom photos.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | `PRIMARY KEY` | Photo entry identifier. |
| `session_id` | `bigint` | `FK -> attendance_sessions(id)` | Parent attendance session. |
| `sha256` | `char(64)` | `NOT NULL` | Exact duplicate SHA-256 image digest. |
| `phash` | `bigint` | `NOT NULL` | 64-bit DCT perceptual hash for near-duplicate detection. |
| `latitude` | `double precision`| `NOT NULL` | Device GPS latitude at snapshot. |
| `longitude`| `double precision`| `NOT NULL` | Device GPS longitude at snapshot. |
| `accuracy_m`| `double precision`| `NOT NULL` | Reported GPS device accuracy. |
| `captured_at`| `timestamptz`| `DEFAULT NOW()` | Server receipt time. |
| `retained_until`| `timestamptz`| `NULL` | Image purged immediately unless flagged (kept 30 days for review). |

---

### 7. `attendance_records`
Per-student attendance verification results for a session.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | `PRIMARY KEY` | Attendance record ID. |
| `session_id` | `bigint` | `FK -> attendance_sessions(id)` | Parent session. |
| `student_id` | `bigint` | `FK -> students(id)` | Evaluated student. |
| `face_score` | `numeric(4,3)`| `NULL` | Cosine similarity between classroom face and enrolled embedding. |
| `spoof_score`| `numeric(4,3)`| `NULL` | Liveness probability score ($0.0 - 1.0$). |
| `confirmed_at`| `timestamptz`| `NULL` | Student confirmation timestamp. |
| `confirm_lat`| `double precision`| `NULL` | Student GPS latitude at confirmation. |
| `confirm_lng`| `double precision`| `NULL` | Student GPS longitude at confirmation. |
| `device_id` | `bigint` | `FK -> devices(id)` | Device used for confirmation. |
| `final_status`| `varchar(14)`| `DEFAULT 'PENDING'` | Status: `PENDING`, `MATCHED`, `REVIEW`, `PRESENT`, `PRESENT_QR`, `NOT_CONFIRMED`, `ABSENT`. |

---

### 8. `audit_logs`
Cryptographically hash-chained, insert-only administrative modification log.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | `PRIMARY KEY` | Sequential log index. |
| `actor_id` | `bigint` | `FK -> users(id)` | User who executed action. |
| `action` | `varchar(50)` | `NOT NULL` | Action code (e.g. `GRADE_MODIFIED`, `ATTENDANCE_OVERRIDE`). |
| `entity_type` | `varchar(50)`| `NOT NULL` | Target entity table name. |
| `entity_id` | `bigint` | `NOT NULL` | Target entity identifier. |
| `old_value` | `jsonb` | `NULL` | Previous state snapshot. |
| `new_value` | `jsonb` | `NULL` | New state snapshot. |
| `reason` | `varchar(500)`| `NOT NULL` | Mandatory justification ($\ge 10$ characters). |
| `prev_hash` | `char(64)` | `NOT NULL` | SHA-256 digest of preceding audit log block. |
| `entry_hash` | `char(64)` | `UNIQUE, NOT NULL` | SHA-256 digest of current entry payload. |
| `created_at` | `timestamptz` | `DEFAULT NOW()` | Immutable timestamp of log creation. |

---

## ⚡ Indexing & Vector Search Configuration

To ensure sub-second retrieval across large student populations, the database uses specific vector and B-Tree indexes:

```sql
-- 1. Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. HNSW Cosine Index for 512-dim ArcFace Biometrics
CREATE INDEX idx_students_face_embedding 
ON students 
USING hnsw (face_embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 3. HNSW Index for Document RAG Chunks
CREATE INDEX idx_document_chunks_embedding 
ON document_chunks 
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 4. Unique & Lookup B-Tree Indexes
CREATE UNIQUE INDEX idx_users_user_code ON users(user_code);
CREATE UNIQUE INDEX idx_students_roll_no ON students(roll_no);
CREATE UNIQUE INDEX idx_session_photos_sha256 ON session_photos(session_id, sha256);
CREATE UNIQUE INDEX idx_issued_documents_code ON issued_documents(code);
CREATE UNIQUE INDEX idx_audit_logs_entry_hash ON audit_logs(entry_hash);
CREATE INDEX idx_attendance_records_session_student ON attendance_records(session_id, student_id);
```
