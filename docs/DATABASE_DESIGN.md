# 🗄️ IntelliCampus: Database Design & Schema Specification (MySQL)

This document details the relational data model, indexing strategies, and immutable audit log architecture for **IntelliCampus**, completely adapted for **MySQL 8.0 / MySQL 8.4 LTS**.

---

## 📐 Entity Relationship (ER) Overview

```
                                  +-------------------+
                                  |    departments    |
                                  +---------+---------+
                                            | 1
                                            | has
                                            | N
                                  +---------v---------+
                      +---------->|     divisions     |
                      | 1         +---------+---------+
                      |                     | 1
                      | has                 | has
                      |                     | N
                      |           +---------v---------+
                      |           |      students     |
                      |           +----+----+----+----+
                      |                |    |    |
           +----------+          1:N   |    |    | 1:N
           |  users   | <--------------+    |    +-------------> [ mark_entries ]
           | (Mentor) |                     |
           +----------+                     | 1:N
                                            v
                                     [ leave_requests ]

                                  +---------------------+
                                  | attendance_sessions |
                                  +----------+----------+
                                             |
                         +-------------------+-------------------+
                         | 1:N                                   | 1:N
                         v                                       v
                 [ session_photos ]                    [ attendance_records ]
                         |                                       |
                         v                                       v
                 (SHA-256 / pHash)                        (PRESENT / ABSENT)

                                  +-------------------+
                                  |    audit_logs     |
                                  +-------------------+
                                  (Cryptographic Chain:
                                   prev_hash -> entry_hash)
```

---

## 📑 Complete MySQL Data Dictionary

All primary keys use `BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY`. Default engine is `InnoDB` with character set `utf8mb4` and collation `utf8mb4_unicode_ci`.

### 1. `users`
Core identity table supporting role-based access control.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT UNSIGNED` | `AUTO_INCREMENT PRIMARY KEY` | Unique identity integer. |
| `user_code` | `VARCHAR(20)` | `UNIQUE, NOT NULL` | Login identifier (e.g. `ADM001`, `FAC104`, `STU2026042`). |
| `role` | `VARCHAR(12)` | `NOT NULL` | Enum string: `ADMIN`, `HOD`, `FACULTY`, `STUDENT`. |
| `name` | `VARCHAR(100)` | `NOT NULL` | Full legal name. |
| `email` | `VARCHAR(120)` | `UNIQUE, NOT NULL` | Institutional email address. |
| `password_hash` | `VARCHAR(100)`| `NOT NULL` | Salted BCrypt password hash. |
| `must_change_password`| `BOOLEAN`| `DEFAULT TRUE` | Forces password change upon first authentication. |
| `status` | `VARCHAR(10)` | `DEFAULT 'ACTIVE'` | Status: `ACTIVE`, `DISABLED`, `LOCKED`. |
| `failed_attempts` | `INT` | `DEFAULT 0` | Consecutive failed login counter. |
| `locked_until` | `DATETIME(6)` | `NULL` | Lockout expiration timestamp. |
| `created_at` | `DATETIME(6)` | `DEFAULT CURRENT_TIMESTAMP(6)` | Record creation timestamp. |
| `updated_at` | `DATETIME(6)` | `DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Record update timestamp. |

---

### 2. `students`
Student profile metadata, division associations, and biometric enrollment.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT UNSIGNED` | `AUTO_INCREMENT PRIMARY KEY` | Unique student record ID. |
| `user_id` | `BIGINT UNSIGNED` | `UNIQUE, FK -> users(id)` | Foreign key to core user identity. |
| `roll_no` | `VARCHAR(20)` | `UNIQUE, NOT NULL` | Official university roll number. |
| `division_id` | `BIGINT UNSIGNED` | `FK -> divisions(id)` | Academic division grouping. |
| `mentor_id` | `BIGINT UNSIGNED` | `FK -> users(id)` | Assigned faculty mentor (exactly one mentor per student). |
| `face_embedding` | `JSON` or `BLOB` | `NULL` | Encrypted 512-dim ArcFace embedding array (AES-GCM encrypted). |
| `consent_at` | `DATETIME(6)` | `NULL` | Timestamp when biometric consent was granted. |
| `consent_version` | `VARCHAR(20)` | `NULL` | Legal consent policy revision string. |
| `created_at` | `DATETIME(6)` | `DEFAULT CURRENT_TIMESTAMP(6)` | Record creation timestamp. |
| `updated_at` | `DATETIME(6)` | `DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Record update timestamp. |

---

### 3. `devices`
Registered browser and device fingerprints for student attendance confirmation.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT UNSIGNED` | `AUTO_INCREMENT PRIMARY KEY` | Device identifier. |
| `student_id` | `BIGINT UNSIGNED` | `FK -> students(id)` | Owner student foreign key. |
| `fingerprint` | `VARCHAR(128)`| `NOT NULL` | Cryptographic hash of client device attributes. |
| `label` | `VARCHAR(60)` | `NULL` | User device label (e.g., "OnePlus 11 Chrome"). |
| `approved` | `BOOLEAN` | `DEFAULT FALSE` | First device auto-approved; secondary devices require mentor approval. |
| `approved_by` | `BIGINT UNSIGNED` | `FK -> users(id)` | Mentor who approved device. |
| `created_at` | `DATETIME(6)` | `DEFAULT CURRENT_TIMESTAMP(6)` | Record creation timestamp. |

---

### 4. `rooms`
Institutional classrooms and laboratory spaces with geofencing coordinates.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT UNSIGNED` | `AUTO_INCREMENT PRIMARY KEY` | Classroom identifier. |
| `name` | `VARCHAR(40)` | `NOT NULL` | Room designation (e.g. "Lab 302", "LH-1"). |
| `latitude` | `DOUBLE` | `NOT NULL` | Geometric latitude center. |
| `longitude`| `DOUBLE` | `NOT NULL` | Geometric longitude center. |
| `radius_m` | `INT` | `DEFAULT 50` | Geofence perimeter in meters (Range: 10m to 200m). |
| `created_at` | `DATETIME(6)` | `DEFAULT CURRENT_TIMESTAMP(6)` | Record creation timestamp. |

---

### 5. `attendance_sessions`
Classroom attendance tracking sessions created by faculty.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT UNSIGNED` | `AUTO_INCREMENT PRIMARY KEY` | Session ID. |
| `faculty_id` | `BIGINT UNSIGNED` | `FK -> users(id)` | Faculty conducting session. |
| `subject_id` | `BIGINT UNSIGNED` | `FK -> subjects(id)` | Subject/course identifier. |
| `division_id` | `BIGINT UNSIGNED` | `FK -> divisions(id)`| Academic class division. |
| `room_id` | `BIGINT UNSIGNED` | `FK -> rooms(id)` | Designated classroom. |
| `started_at` | `DATETIME(6)` | `DEFAULT CURRENT_TIMESTAMP(6)` | Server session start time. |
| `challenge_code`| `VARCHAR(8)` | `NOT NULL` | Ephemeral classroom display code (e.g. "K7Q2"). |
| `status` | `VARCHAR(12)` | `DEFAULT 'OPEN'` | Status: `OPEN`, `PROCESSING`, `REVIEW`, `FINALISED`. |
| `finalised_at` | `DATETIME(6)` | `NULL` | Lock timestamp. |

---

### 6. `session_photos`
Metadata and cryptographic deduplication digests of uploaded classroom photos.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT UNSIGNED` | `AUTO_INCREMENT PRIMARY KEY` | Photo entry identifier. |
| `session_id` | `BIGINT UNSIGNED` | `FK -> attendance_sessions(id)` | Parent attendance session. |
| `sha256` | `CHAR(64)` | `NOT NULL` | Exact duplicate SHA-256 image digest. |
| `phash` | `BIGINT UNSIGNED` | `NOT NULL` | 64-bit DCT perceptual hash for near-duplicate detection. |
| `latitude` | `DOUBLE` | `NOT NULL` | Device GPS latitude at snapshot. |
| `longitude`| `DOUBLE` | `NOT NULL` | Device GPS longitude at snapshot. |
| `accuracy_m`| `DOUBLE` | `NOT NULL` | Reported GPS device accuracy. |
| `captured_at`| `DATETIME(6)`| `DEFAULT CURRENT_TIMESTAMP(6)` | Server receipt time. |
| `retained_until`| `DATETIME(6)`| `NULL` | Image purged immediately unless flagged (kept 30 days for review). |

---

### 7. `attendance_records`
Per-student attendance verification results for a session.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT UNSIGNED` | `AUTO_INCREMENT PRIMARY KEY` | Attendance record ID. |
| `session_id` | `BIGINT UNSIGNED` | `FK -> attendance_sessions(id)` | Parent session. |
| `student_id` | `BIGINT UNSIGNED` | `FK -> students(id)` | Evaluated student. |
| `face_score` | `DECIMAL(4,3)`| `NULL` | Cosine similarity between classroom face and enrolled embedding. |
| `spoof_score`| `DECIMAL(4,3)`| `NULL` | Liveness probability score ($0.0 - 1.0$). |
| `confirmed_at`| `DATETIME(6)`| `NULL` | Student confirmation timestamp. |
| `confirm_lat`| `DOUBLE` | `NULL` | Student GPS latitude at confirmation. |
| `confirm_lng`| `DOUBLE` | `NULL` | Student GPS longitude at confirmation. |
| `device_id` | `BIGINT UNSIGNED` | `FK -> devices(id)` | Device used for confirmation. |
| `final_status`| `VARCHAR(14)`| `DEFAULT 'PENDING'` | Status: `PENDING`, `MATCHED`, `REVIEW`, `PRESENT`, `PRESENT_QR`, `NOT_CONFIRMED`, `ABSENT`. |

---

### 8. `audit_logs`
Cryptographically hash-chained, insert-only administrative modification log.

| Column | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT UNSIGNED` | `AUTO_INCREMENT PRIMARY KEY` | Sequential log index. |
| `actor_id` | `BIGINT UNSIGNED` | `FK -> users(id)` | User who executed action. |
| `action` | `VARCHAR(50)` | `NOT NULL` | Action code (e.g. `GRADE_MODIFIED`, `ATTENDANCE_OVERRIDE`). |
| `entity_type` | `VARCHAR(50)`| `NOT NULL` | Target entity table name. |
| `entity_id` | `BIGINT UNSIGNED` | `NOT NULL` | Target entity identifier. |
| `old_value` | `JSON` | `NULL` | Previous state snapshot. |
| `new_value` | `JSON` | `NULL` | New state snapshot. |
| `reason` | `VARCHAR(500)`| `NOT NULL` | Mandatory justification ($\ge 10$ characters). |
| `prev_hash` | `CHAR(64)` | `NOT NULL` | SHA-256 digest of preceding audit log block. |
| `entry_hash` | `CHAR(64)` | `UNIQUE, NOT NULL` | SHA-256 digest of current entry payload. |
| `created_at` | `DATETIME(6)` | `DEFAULT CURRENT_TIMESTAMP(6)` | Immutable timestamp of log creation. |

---

### 9. Remaining Operational Tables

| Table | Primary Columns | Purpose |
| :--- | :--- | :--- |
| `mark_entries` | `student_id`, `subject_id`, `exam`, `marks`, `max_marks`, `status` (`DRAFT`/`PUBLISHED`), `corrected_by` | Internal assessment marks. |
| `attendance_flags` | `session_id`, `type`, `severity`, `details` (`JSON`), `reviewed_by`, `decision` | Anomaly flags (replays, low spoof, jumps). |
| `notices` | `title`, `body`, `file_path`, `audience` (`JSON`), `deadline` | Targeted institutional announcements. |
| `notice_reads` | `notice_id`, `student_id`, `read_at` | Read receipts tracking per student. |
| `knowledge_documents` | `title`, `file_path`, `status` (`INDEXING`/`INDEXED`) | Uploaded PDF ordinances & circulars. |
| `document_chunks` | `document_id`, `page`, `chunk_text`, `embedding` (`JSON` or vector) | RAG document passage chunks. |
| `chat_messages` | `user_id`, `question`, `answer`, `sources` (`JSON`), `rating` | Chat history and response ratings. |
| `risk_scores` | `student_id`, `score`, `level` (`LOW`/`MED`/`HIGH`), `factors` (`JSON`), `run_date` | Weekly Early Warning System model outputs. |
| `risk_actions` | `score_id`, `mentor_id`, `note`, `action_date` | Documented mentor intervention actions. |
| `issued_documents` | `code` (`UNIQUE`), `type`, `student_id`, `file_hash`, `status`, `revoked_reason` | Verifiable fee receipts & certificates. |
| `leave_requests` | `student_id`, `from_date`, `to_date`, `reason`, `attachment`, `status` | Student leave applications. |
| `leave_steps` | `request_id`, `approver_id`, `decision`, `comment` | Multi-tier approval chain records. |
| `notifications` | `user_id`, `type`, `payload` (`JSON`), `read_at` | Push notification history. |

---

## ⚡ MySQL DDL Schema Definition

```sql
-- Create Database
CREATE DATABASE IF NOT EXISTS intellicampus_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE intellicampus_db;

-- 1. Users
CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_code VARCHAR(20) NOT NULL UNIQUE,
  role VARCHAR(12) NOT NULL,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(120) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  must_change_password BOOLEAN DEFAULT TRUE,
  status VARCHAR(10) DEFAULT 'ACTIVE',
  failed_attempts INT DEFAULT 0,
  locked_until DATETIME(6) NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  INDEX idx_users_role (role)
) ENGINE=InnoDB;

-- 2. Rooms
CREATE TABLE IF NOT EXISTS rooms (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(40) NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  radius_m INT DEFAULT 50,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

-- 3. Students
CREATE TABLE IF NOT EXISTS students (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL UNIQUE,
  roll_no VARCHAR(20) NOT NULL UNIQUE,
  division_id BIGINT UNSIGNED NOT NULL,
  mentor_id BIGINT UNSIGNED NOT NULL,
  face_embedding JSON NULL,
  consent_at DATETIME(6) NULL,
  consent_version VARCHAR(20) NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_students_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_students_mentor FOREIGN KEY (mentor_id) REFERENCES users(id),
  INDEX idx_students_division (division_id)
) ENGINE=InnoDB;

-- 4. Attendance Sessions
CREATE TABLE IF NOT EXISTS attendance_sessions (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  faculty_id BIGINT UNSIGNED NOT NULL,
  subject_id BIGINT UNSIGNED NOT NULL,
  division_id BIGINT UNSIGNED NOT NULL,
  room_id BIGINT UNSIGNED NOT NULL,
  started_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  challenge_code VARCHAR(8) NOT NULL,
  status VARCHAR(12) DEFAULT 'OPEN',
  finalised_at DATETIME(6) NULL,
  CONSTRAINT fk_sessions_faculty FOREIGN KEY (faculty_id) REFERENCES users(id),
  CONSTRAINT fk_sessions_room FOREIGN KEY (room_id) REFERENCES rooms(id),
  INDEX idx_sessions_status (status)
) ENGINE=InnoDB;

-- 5. Session Photos (with Deduplication)
CREATE TABLE IF NOT EXISTS session_photos (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT UNSIGNED NOT NULL,
  sha256 CHAR(64) NOT NULL,
  phash BIGINT UNSIGNED NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  accuracy_m DOUBLE NOT NULL,
  captured_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  retained_until DATETIME(6) NULL,
  CONSTRAINT fk_photos_session FOREIGN KEY (session_id) REFERENCES attendance_sessions(id) ON DELETE CASCADE,
  UNIQUE INDEX uq_session_sha256 (session_id, sha256),
  INDEX idx_photos_phash (phash)
) ENGINE=InnoDB;

-- 6. Attendance Records
CREATE TABLE IF NOT EXISTS attendance_records (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT UNSIGNED NOT NULL,
  student_id BIGINT UNSIGNED NOT NULL,
  face_score DECIMAL(4,3) NULL,
  spoof_score DECIMAL(4,3) NULL,
  confirmed_at DATETIME(6) NULL,
  confirm_lat DOUBLE NULL,
  confirm_lng DOUBLE NULL,
  device_id BIGINT UNSIGNED NULL,
  final_status VARCHAR(14) DEFAULT 'PENDING',
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE INDEX uq_session_student (session_id, student_id),
  INDEX idx_records_status (final_status)
) ENGINE=InnoDB;

-- 7. Audit Logs (Tamper-Evident Hash Chain)
CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  actor_id BIGINT UNSIGNED NOT NULL,
  action VARCHAR(50) NOT NULL,
  entity_type VARCHAR(50) NOT NULL,
  entity_id BIGINT UNSIGNED NOT NULL,
  old_value JSON NULL,
  new_value JSON NULL,
  reason VARCHAR(500) NOT NULL,
  prev_hash CHAR(64) NOT NULL,
  entry_hash CHAR(64) NOT NULL UNIQUE,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_audit_entity (entity_type, entity_id),
  INDEX idx_audit_actor (actor_id)
) ENGINE=InnoDB;
```

---

## 🔒 Vector Embedding Strategy in MySQL

In MySQL 8.0, 512-dimensional facial embeddings and RAG document vectors are supported cleanly:
1. **Division-Scoped In-Memory / Vector Comparison**:
   - Because attendance face-matching is scoped strictly to students of the specific classroom division (typically 40–80 students), the backend or Python AI microservice retrieves the division's enrolled embeddings and calculates cosine similarity in milliseconds:
     $$\text{Cosine Similarity} = \frac{\mathbf{u} \cdot \mathbf{v}}{\|\mathbf{u}\|_2 \|\mathbf{v}\|_2}$$
2. **Storage**:
   - `face_embedding` is stored as an AES-GCM encrypted JSON array of 512 floats or as binary `BLOB` (2,048 bytes).
3. **Audit Log Table Security**:
   - The application database user is granted strictly `INSERT` and `SELECT` rights on `audit_logs` to prevent any runtime modification or deletion:
     ```sql
     GRANT SELECT, INSERT ON intellicampus_db.audit_logs TO 'campus_admin'@'%';
     REVOKE UPDATE, DELETE ON intellicampus_db.audit_logs FROM 'campus_admin'@'%';
     ```
