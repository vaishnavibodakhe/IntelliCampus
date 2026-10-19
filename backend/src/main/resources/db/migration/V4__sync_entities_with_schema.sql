-- IntelliCampus MySQL Database Migration V3
-- Schema Synchronization for JPA Hibernate Entities (ddl-auto: validate)
-- Compliant with IC-SRS-1.0 and Hibernate 6.4 Schema Validation Specifications

-- 1. Alter Table: attendance_records (AttendanceRecord entity)
ALTER TABLE attendance_records
  ADD COLUMN confirmed BOOLEAN NOT NULL DEFAULT FALSE AFTER spoof_score,
  ADD COLUMN status_reason VARCHAR(255) NULL AFTER final_status,
  MODIFY COLUMN face_score DOUBLE NULL,
  MODIFY COLUMN spoof_score DOUBLE NULL,
  MODIFY COLUMN final_status VARCHAR(16) NOT NULL DEFAULT 'PENDING';

-- 2. Alter Table: attendance_sessions (AttendanceSession entity)
ALTER TABLE attendance_sessions
  ADD COLUMN photo_window_ends_at DATETIME(6) NULL AFTER started_at,
  ADD COLUMN created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) AFTER finalised_at,
  ADD INDEX idx_sessions_started_at (started_at);

-- 3. Alter Table: audit_logs (AuditLogEntry entity)
ALTER TABLE audit_logs
  ADD COLUMN actor_name VARCHAR(100) NOT NULL DEFAULT 'System' AFTER actor_id,
  MODIFY COLUMN entity_id VARCHAR(64) NOT NULL,
  ADD INDEX idx_audit_created_at (created_at);

UPDATE audit_logs SET actor_name = 'System' WHERE id = 1;

-- 4. Alter Table: rooms (Classroom entity)
ALTER TABLE rooms
  MODIFY COLUMN name VARCHAR(60) NOT NULL,
  ADD COLUMN capacity INT NULL AFTER radius_m,
  ADD COLUMN department_id BIGINT UNSIGNED NULL AFTER capacity,
  ADD CONSTRAINT fk_rooms_dept FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL;

-- 5. Alter Table: subjects (Subject entity)
ALTER TABLE subjects
  ADD COLUMN credits INT NOT NULL DEFAULT 4 AFTER name,
  ADD COLUMN faculty_id BIGINT UNSIGNED NULL AFTER department_id,
  ADD CONSTRAINT fk_subjects_faculty FOREIGN KEY (faculty_id) REFERENCES users(id) ON DELETE SET NULL;

-- 6. Alter Table: students (StudentProfile entity)
ALTER TABLE students
  MODIFY COLUMN roll_no VARCHAR(30) NOT NULL,
  ADD COLUMN face_enrolled BOOLEAN DEFAULT FALSE AFTER mentor_id,
  ADD COLUMN attendance_rate DOUBLE DEFAULT 0.0 AFTER face_enrolled,
  ADD COLUMN backlogs INT DEFAULT 0 AFTER attendance_rate,
  ADD COLUMN fee_status VARCHAR(20) DEFAULT 'PAID' AFTER backlogs,
  ADD COLUMN due_amount DOUBLE DEFAULT 0.0 AFTER fee_status;

-- 7. Alter Table: session_photos (SessionPhoto entity)
ALTER TABLE session_photos
  ADD COLUMN face_count INT DEFAULT 0 AFTER accuracy_m,
  MODIFY COLUMN phash BIGINT NULL;

-- 8. Alter Table: mark_entries (MarkEntry entity)
ALTER TABLE mark_entries
  MODIFY COLUMN marks DOUBLE NOT NULL,
  MODIFY COLUMN max_marks DOUBLE NOT NULL,
  ADD COLUMN updated_by BIGINT UNSIGNED NULL AFTER status,
  ADD CONSTRAINT fk_marks_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL,
  ADD INDEX idx_marks_status (status);

-- 9. Alter Table: leave_requests (LeaveRequest entity)
ALTER TABLE leave_requests
  ADD COLUMN days_count INT NOT NULL DEFAULT 1 AFTER to_date,
  ADD COLUMN attachment_name VARCHAR(200) NULL AFTER reason,
  ADD COLUMN attachment_url VARCHAR(500) NULL AFTER attachment_name,
  ADD COLUMN mentor_id BIGINT UNSIGNED NULL AFTER attachment_url,
  ADD COLUMN mentor_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' AFTER mentor_id,
  ADD COLUMN mentor_comment VARCHAR(500) NULL AFTER mentor_status,
  ADD COLUMN hod_id BIGINT UNSIGNED NULL AFTER mentor_comment,
  ADD COLUMN hod_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' AFTER hod_id,
  ADD COLUMN hod_comment VARCHAR(500) NULL AFTER hod_status,
  ADD COLUMN final_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' AFTER hod_comment,
  ADD COLUMN submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER final_status,
  ADD COLUMN decided_at DATETIME(6) NULL AFTER submitted_at,
  ADD CONSTRAINT fk_leave_mentor FOREIGN KEY (mentor_id) REFERENCES users(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_leave_hod FOREIGN KEY (hod_id) REFERENCES users(id) ON DELETE SET NULL,
  ADD INDEX idx_leaves_final_status (final_status),
  ADD INDEX idx_leaves_from_date (from_date);

-- 10. Alter Table: issued_documents (VerifiableDocument entity)
ALTER TABLE issued_documents
  MODIFY COLUMN type VARCHAR(60) NOT NULL,
  MODIFY COLUMN student_id BIGINT UNSIGNED NULL,
  ADD COLUMN student_name VARCHAR(100) NOT NULL DEFAULT '' AFTER student_id,
  ADD COLUMN masked_student_name VARCHAR(100) NOT NULL DEFAULT '' AFTER student_name,
  ADD COLUMN roll_no VARCHAR(30) NULL AFTER masked_student_name,
  ADD COLUMN department VARCHAR(100) NULL AFTER roll_no,
  ADD COLUMN issue_date DATE NOT NULL DEFAULT (CURRENT_DATE) AFTER department,
  ADD COLUMN gpa VARCHAR(30) NULL AFTER revoked_reason,
  ADD COLUMN amount_paid VARCHAR(50) NULL AFTER gpa,
  ADD COLUMN authorized_signatory VARCHAR(120) NULL AFTER amount_paid;

-- 11. Alter Table: users (align varchar bounds with User entity)
ALTER TABLE users
  MODIFY COLUMN user_code VARCHAR(40) NOT NULL,
  MODIFY COLUMN role VARCHAR(20) NOT NULL,
  MODIFY COLUMN password_hash VARCHAR(120) NOT NULL,
  MODIFY COLUMN status VARCHAR(20) DEFAULT 'ACTIVE';

-- 12. Create Table: notices (Notice entity)
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
  CONSTRAINT fk_notices_publisher FOREIGN KEY (publisher_id) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_notices_published_at (published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. Create Table: notice_read_receipts (NoticeReadReceipt entity)
CREATE TABLE IF NOT EXISTS notice_read_receipts (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  notice_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  read_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_receipts_notice FOREIGN KEY (notice_id) REFERENCES notices(id) ON DELETE CASCADE,
  CONSTRAINT fk_receipts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  UNIQUE INDEX uq_notice_user (notice_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. Create Table: risk_assessments (RiskAssessmentRecord entity)
CREATE TABLE IF NOT EXISTS risk_assessments (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT UNSIGNED NOT NULL,
  risk_score DOUBLE NOT NULL,
  level VARCHAR(16) NOT NULL,
  trend VARCHAR(20) DEFAULT 'STABLE',
  top_factors TEXT NULL,
  evaluated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_risk_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  INDEX idx_risk_student (student_id),
  INDEX idx_risk_level (level),
  INDEX idx_risk_evaluated_at (evaluated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. Create Table: mark_correction_requests (MarkCorrectionRequest entity)
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
  requested_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  decided_at DATETIME(6) NULL,
  CONSTRAINT fk_corrections_mark FOREIGN KEY (mark_id) REFERENCES mark_entries(id) ON DELETE CASCADE,
  CONSTRAINT fk_corrections_faculty FOREIGN KEY (faculty_id) REFERENCES users(id),
  CONSTRAINT fk_corrections_hod FOREIGN KEY (hod_id) REFERENCES users(id),
  INDEX idx_corrections_status (status),
  INDEX idx_corrections_mark (mark_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
