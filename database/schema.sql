-- ============================================================================
-- IntelliCampus Full Schema Reference Script
-- Database: intellicampus (MySQL 8+, utf8mb4)
-- Note: Flyway migrations (V1 to V4) in backend are the source of truth for runtime.
-- This file is a consolidated, readable copy of the final schema for reference.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS intellicampus
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE intellicampus;

-- 1. Departments
CREATE TABLE IF NOT EXISTS departments (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Users (Core Authentication & Role Management)
CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_code VARCHAR(40) NOT NULL UNIQUE,
  role VARCHAR(20) NOT NULL,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(120) NOT NULL UNIQUE,
  phone VARCHAR(20) NULL,
  specialization VARCHAR(100) NULL,
  password_hash VARCHAR(120) NOT NULL,
  must_change_password BOOLEAN DEFAULT FALSE,
  status VARCHAR(20) DEFAULT 'ACTIVE',
  failed_attempts INT DEFAULT 0,
  locked_until DATETIME(6) NULL,
  department_id BIGINT UNSIGNED NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_users_dept FOREIGN KEY (department_id) REFERENCES departments(id),
  INDEX idx_users_role (role),
  INDEX idx_users_email (email),
  INDEX idx_users_user_code (user_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Divisions
CREATE TABLE IF NOT EXISTS divisions (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  department_id BIGINT UNSIGNED NOT NULL,
  year INT NOT NULL,
  name VARCHAR(20) NOT NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_divisions_dept FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Subjects
CREATE TABLE IF NOT EXISTS subjects (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  credits INT NOT NULL DEFAULT 4,
  faculty_id BIGINT UNSIGNED NULL,
  department_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_subjects_dept FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE CASCADE,
  CONSTRAINT fk_subjects_faculty FOREIGN KEY (faculty_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Classrooms & Laboratories
CREATE TABLE IF NOT EXISTS rooms (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(40) NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  radius_m INT DEFAULT 50,
  capacity INT NULL,
  department_id BIGINT UNSIGNED NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_rooms_dept FOREIGN KEY (department_id) REFERENCES departments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Students
CREATE TABLE IF NOT EXISTS students (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL UNIQUE,
  roll_no VARCHAR(20) NOT NULL UNIQUE,
  division_id BIGINT UNSIGNED NOT NULL,
  mentor_id BIGINT UNSIGNED NOT NULL,
  face_photo_path VARCHAR(255) NULL,
  face_enrolled BOOLEAN NOT NULL DEFAULT FALSE,
  attendance_rate DOUBLE NOT NULL DEFAULT 0.0,
  backlogs INT NOT NULL DEFAULT 0,
  fee_status VARCHAR(20) NOT NULL DEFAULT 'PAID',
  due_amount DOUBLE NOT NULL DEFAULT 0.0,
  face_embedding JSON NULL,
  consent_at DATETIME(6) NULL,
  consent_version VARCHAR(20) NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_students_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_students_division FOREIGN KEY (division_id) REFERENCES divisions(id),
  CONSTRAINT fk_students_mentor FOREIGN KEY (mentor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Devices
CREATE TABLE IF NOT EXISTS devices (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT UNSIGNED NOT NULL,
  fingerprint VARCHAR(128) NOT NULL,
  label VARCHAR(60) NULL,
  approved BOOLEAN DEFAULT FALSE,
  approved_by BIGINT UNSIGNED NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_devices_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  CONSTRAINT fk_devices_approved_by FOREIGN KEY (approved_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Attendance Sessions
CREATE TABLE IF NOT EXISTS attendance_sessions (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  faculty_id BIGINT UNSIGNED NOT NULL,
  subject_id BIGINT UNSIGNED NOT NULL,
  division_id BIGINT UNSIGNED NOT NULL,
  room_id BIGINT UNSIGNED NOT NULL,
  started_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  photo_window_ends_at DATETIME(6) NULL,
  challenge_code VARCHAR(8) NOT NULL,
  status VARCHAR(12) DEFAULT 'OPEN',
  finalised_at DATETIME(6) NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_sessions_faculty FOREIGN KEY (faculty_id) REFERENCES users(id),
  CONSTRAINT fk_sessions_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
  CONSTRAINT fk_sessions_division FOREIGN KEY (division_id) REFERENCES divisions(id),
  CONSTRAINT fk_sessions_room FOREIGN KEY (room_id) REFERENCES rooms(id),
  INDEX idx_sessions_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Session Photos
CREATE TABLE IF NOT EXISTS session_photos (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT UNSIGNED NOT NULL,
  sha256 VARCHAR(64) NOT NULL,
  phash BIGINT UNSIGNED NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  accuracy_m DOUBLE NOT NULL,
  face_count INT NOT NULL DEFAULT 0,
  captured_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  retained_until DATETIME(6) NULL,
  CONSTRAINT fk_photos_session FOREIGN KEY (session_id) REFERENCES attendance_sessions(id) ON DELETE CASCADE,
  UNIQUE INDEX uq_session_sha256 (session_id, sha256),
  INDEX idx_photos_phash (phash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Attendance Records
CREATE TABLE IF NOT EXISTS attendance_records (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT UNSIGNED NOT NULL,
  student_id BIGINT UNSIGNED NOT NULL,
  face_score DOUBLE NULL,
  spoof_score DOUBLE NULL,
  confirmed BOOLEAN NOT NULL DEFAULT FALSE,
  confirmed_at DATETIME(6) NULL,
  confirm_lat DOUBLE NULL,
  confirm_lng DOUBLE NULL,
  device_id BIGINT UNSIGNED NULL,
  final_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  status_reason VARCHAR(255) NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_records_session FOREIGN KEY (session_id) REFERENCES attendance_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_records_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  CONSTRAINT fk_records_device FOREIGN KEY (device_id) REFERENCES devices(id),
  UNIQUE INDEX uq_session_student (session_id, student_id),
  INDEX idx_records_status (final_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. Audit Logs (SHA-256 Chained Immutable Ledger)
CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  actor_id BIGINT UNSIGNED NOT NULL,
  actor_name VARCHAR(100) NOT NULL,
  action VARCHAR(50) NOT NULL,
  entity_type VARCHAR(50) NOT NULL,
  entity_id VARCHAR(64) NOT NULL,
  old_value TEXT NULL,
  new_value TEXT NULL,
  reason VARCHAR(500) NOT NULL,
  prev_hash VARCHAR(64) NOT NULL,
  entry_hash VARCHAR(64) NOT NULL UNIQUE,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_audit_entity (entity_type, entity_id),
  INDEX idx_audit_entry_hash (entry_hash),
  INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. Internal Marks Entries
CREATE TABLE IF NOT EXISTS mark_entries (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT UNSIGNED NOT NULL,
  subject_id BIGINT UNSIGNED NOT NULL,
  exam VARCHAR(50) NOT NULL,
  marks DOUBLE NOT NULL,
  max_marks DOUBLE NOT NULL,
  status VARCHAR(12) DEFAULT 'DRAFT',
  corrected_by BIGINT UNSIGNED NULL,
  updated_by BIGINT UNSIGNED NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_marks_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  CONSTRAINT fk_marks_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
  CONSTRAINT fk_marks_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. Mark Correction Requests (HOD Audit Approval Workflow)
CREATE TABLE IF NOT EXISTS mark_correction_requests (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  mark_id BIGINT UNSIGNED NOT NULL,
  old_marks DOUBLE NOT NULL,
  new_marks DOUBLE NOT NULL,
  reason VARCHAR(500) NOT NULL,
  faculty_id BIGINT UNSIGNED NOT NULL,
  hod_id BIGINT UNSIGNED NULL,
  decision_comment VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING_HOD',
  requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  decided_at DATETIME(6) NULL,
  CONSTRAINT fk_corrections_mark FOREIGN KEY (mark_id) REFERENCES mark_entries(id) ON DELETE CASCADE,
  CONSTRAINT fk_corrections_faculty FOREIGN KEY (faculty_id) REFERENCES users(id),
  CONSTRAINT fk_corrections_hod FOREIGN KEY (hod_id) REFERENCES users(id),
  INDEX idx_corrections_status (status),
  INDEX idx_corrections_mark (mark_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. Verifiable Issued Documents (Cryptographic Public Verification)
CREATE TABLE IF NOT EXISTS issued_documents (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  type VARCHAR(50) NOT NULL,
  student_id BIGINT UNSIGNED NOT NULL,
  student_name VARCHAR(100) NOT NULL DEFAULT '',
  masked_student_name VARCHAR(100) NOT NULL DEFAULT '',
  roll_no VARCHAR(30) NULL,
  department VARCHAR(100) NULL,
  issue_date DATE NOT NULL DEFAULT (CURRENT_DATE),
  file_hash VARCHAR(64) NOT NULL,
  status VARCHAR(20) DEFAULT 'ISSUED',
  revoked_reason VARCHAR(255) NULL,
  gpa VARCHAR(30) NULL,
  amount_paid VARCHAR(50) NULL,
  authorized_signatory VARCHAR(120) NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_docs_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. Leave Requests (Multi-Tier Student -> Mentor -> HOD State Machine)
CREATE TABLE IF NOT EXISTS leave_requests (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT UNSIGNED NOT NULL,
  from_date DATE NOT NULL,
  to_date DATE NOT NULL,
  days_count INT NOT NULL DEFAULT 1,
  reason VARCHAR(500) NOT NULL,
  attachment VARCHAR(255) NULL,
  attachment_name VARCHAR(200) NULL,
  attachment_url VARCHAR(500) NULL,
  mentor_id BIGINT UNSIGNED NULL,
  mentor_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
  mentor_comment VARCHAR(500) NULL,
  hod_id BIGINT UNSIGNED NULL,
  hod_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
  hod_comment VARCHAR(500) NULL,
  status VARCHAR(20) DEFAULT 'SUBMITTED',
  final_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
  submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  decided_at DATETIME(6) NULL,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_leave_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  CONSTRAINT fk_leave_mentor FOREIGN KEY (mentor_id) REFERENCES users(id),
  CONSTRAINT fk_leave_hod FOREIGN KEY (hod_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. Notices (Targeted Hierarchy Broadcasts)
CREATE TABLE IF NOT EXISTS notices (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  body TEXT NOT NULL,
  target_department VARCHAR(100) DEFAULT 'All Departments',
  target_year VARCHAR(50) DEFAULT 'All Years',
  target_division VARCHAR(50) DEFAULT 'All Divisions',
  publisher_role VARCHAR(50) NULL,
  publisher_id BIGINT UNSIGNED NULL,
  deadline DATE NULL,
  published_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  has_attachment BOOLEAN DEFAULT FALSE,
  file_name VARCHAR(200) NULL,
  attachment_url VARCHAR(500) NULL,
  CONSTRAINT fk_notices_publisher FOREIGN KEY (publisher_id) REFERENCES users(id),
  INDEX idx_notices_published_at (published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 17. Notice Read Receipts (Auditable Read Tracking)
CREATE TABLE IF NOT EXISTS notice_read_receipts (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  notice_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  read_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_receipts_notice FOREIGN KEY (notice_id) REFERENCES notices(id) ON DELETE CASCADE,
  CONSTRAINT fk_receipts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  UNIQUE KEY uq_notice_user (notice_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 18. Risk Assessment Records (AI Early-Warning System Logs)
CREATE TABLE IF NOT EXISTS risk_assessments (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT UNSIGNED NOT NULL,
  risk_score DOUBLE NOT NULL,
  level VARCHAR(16) NOT NULL,
  trend VARCHAR(20) DEFAULT 'STABLE',
  top_factors TEXT NULL,
  evaluated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_risk_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  INDEX idx_risk_student (student_id),
  INDEX idx_risk_level (level),
  INDEX idx_risk_evaluated_at (evaluated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 19. Password Reset Tokens (Stores SHA-256 hashes of reset tokens)
CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_reset_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_reset_token_hash (token_hash),
  INDEX idx_reset_token_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 20. Revoked Tokens (Logout Token Blacklist Table)
CREATE TABLE IF NOT EXISTS revoked_tokens (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  revoked_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  expires_at DATETIME(6) NOT NULL,
  INDEX idx_revoked_token_hash (token_hash),
  INDEX idx_revoked_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
