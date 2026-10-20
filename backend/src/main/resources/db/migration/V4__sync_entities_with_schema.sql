-- IntelliCampus MySQL Database Migration V3
-- Schema Synchronization for JPA Hibernate Entities (ddl-auto: validate)
-- Compliant with IC-SRS-1.0 and Hibernate 6.4 Schema Validation Specifications

-- 1. Alter Table: attendance_records (AttendanceRecord entity)
SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance_records' AND COLUMN_NAME = 'confirmed');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE attendance_records ADD COLUMN confirmed BOOLEAN NOT NULL DEFAULT FALSE AFTER spoof_score', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance_records' AND COLUMN_NAME = 'status_reason');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE attendance_records ADD COLUMN status_reason VARCHAR(255) NULL AFTER final_status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE attendance_records
  MODIFY COLUMN face_score DOUBLE NULL,
  MODIFY COLUMN spoof_score DOUBLE NULL,
  MODIFY COLUMN final_status VARCHAR(16) NOT NULL DEFAULT 'PENDING';

-- 2. Alter Table: attendance_sessions (AttendanceSession entity)
SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance_sessions' AND COLUMN_NAME = 'photo_window_ends_at');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE attendance_sessions ADD COLUMN photo_window_ends_at DATETIME(6) NULL AFTER started_at', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance_sessions' AND COLUMN_NAME = 'created_at');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE attendance_sessions ADD COLUMN created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) AFTER finalised_at', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance_sessions' AND INDEX_NAME = 'idx_sessions_started_at');
SET @sql = IF(@i_exists = 0, 'ALTER TABLE attendance_sessions ADD INDEX idx_sessions_started_at (started_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. Alter Table: audit_logs (AuditLogEntry entity)
SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_logs' AND COLUMN_NAME = 'actor_name');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE audit_logs ADD COLUMN actor_name VARCHAR(100) NOT NULL DEFAULT ''System'' AFTER actor_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE audit_logs
  MODIFY COLUMN entity_id VARCHAR(64) NOT NULL;

SET @i_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_logs' AND INDEX_NAME = 'idx_audit_created_at');
SET @sql = IF(@i_exists = 0, 'ALTER TABLE audit_logs ADD INDEX idx_audit_created_at (created_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE audit_logs SET actor_name = 'System' WHERE id = 1;

-- 4. Alter Table: rooms (Classroom entity)
ALTER TABLE rooms
  MODIFY COLUMN name VARCHAR(60) NOT NULL;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rooms' AND COLUMN_NAME = 'capacity');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE rooms ADD COLUMN capacity INT NULL AFTER radius_m', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rooms' AND COLUMN_NAME = 'department_id');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE rooms ADD COLUMN department_id BIGINT UNSIGNED NULL AFTER capacity', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @k_exists = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rooms' AND CONSTRAINT_NAME = 'fk_rooms_dept');
SET @sql = IF(@k_exists = 0, 'ALTER TABLE rooms ADD CONSTRAINT fk_rooms_dept FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. Alter Table: subjects (Subject entity)
SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subjects' AND COLUMN_NAME = 'credits');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE subjects ADD COLUMN credits INT NOT NULL DEFAULT 4 AFTER name', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subjects' AND COLUMN_NAME = 'faculty_id');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE subjects ADD COLUMN faculty_id BIGINT UNSIGNED NULL AFTER department_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @k_exists = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subjects' AND CONSTRAINT_NAME = 'fk_subjects_faculty');
SET @sql = IF(@k_exists = 0, 'ALTER TABLE subjects ADD CONSTRAINT fk_subjects_faculty FOREIGN KEY (faculty_id) REFERENCES users(id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6. Alter Table: students (StudentProfile entity)
ALTER TABLE students
  MODIFY COLUMN roll_no VARCHAR(30) NOT NULL;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND COLUMN_NAME = 'face_enrolled');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE students ADD COLUMN face_enrolled BOOLEAN DEFAULT FALSE AFTER mentor_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND COLUMN_NAME = 'attendance_rate');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE students ADD COLUMN attendance_rate DOUBLE DEFAULT 0.0 AFTER face_enrolled', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND COLUMN_NAME = 'backlogs');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE students ADD COLUMN backlogs INT DEFAULT 0 AFTER attendance_rate', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND COLUMN_NAME = 'fee_status');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE students ADD COLUMN fee_status VARCHAR(20) DEFAULT ''PAID'' AFTER backlogs', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND COLUMN_NAME = 'due_amount');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE students ADD COLUMN due_amount DOUBLE DEFAULT 0.0 AFTER fee_status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7. Alter Table: session_photos (SessionPhoto entity)
SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'session_photos' AND COLUMN_NAME = 'face_count');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE session_photos ADD COLUMN face_count INT DEFAULT 0 AFTER accuracy_m', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE session_photos
  MODIFY COLUMN phash BIGINT NULL;

-- 8. Alter Table: mark_entries (MarkEntry entity)
ALTER TABLE mark_entries
  MODIFY COLUMN marks DOUBLE NOT NULL,
  MODIFY COLUMN max_marks DOUBLE NOT NULL;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mark_entries' AND COLUMN_NAME = 'updated_by');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE mark_entries ADD COLUMN updated_by BIGINT UNSIGNED NULL AFTER status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @k_exists = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mark_entries' AND CONSTRAINT_NAME = 'fk_marks_updated_by');
SET @sql = IF(@k_exists = 0, 'ALTER TABLE mark_entries ADD CONSTRAINT fk_marks_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mark_entries' AND INDEX_NAME = 'idx_marks_status');
SET @sql = IF(@i_exists = 0, 'ALTER TABLE mark_entries ADD INDEX idx_marks_status (status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 9. Alter Table: leave_requests (LeaveRequest entity)
SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND COLUMN_NAME = 'days_count');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE leave_requests ADD COLUMN days_count INT NOT NULL DEFAULT 1 AFTER to_date', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND COLUMN_NAME = 'attachment_name');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE leave_requests ADD COLUMN attachment_name VARCHAR(200) NULL AFTER reason', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND COLUMN_NAME = 'attachment_url');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE leave_requests ADD COLUMN attachment_url VARCHAR(500) NULL AFTER attachment_name', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND COLUMN_NAME = 'mentor_id');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE leave_requests ADD COLUMN mentor_id BIGINT UNSIGNED NULL AFTER attachment_url', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND COLUMN_NAME = 'mentor_status');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE leave_requests ADD COLUMN mentor_status VARCHAR(20) NOT NULL DEFAULT ''SUBMITTED'' AFTER mentor_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND COLUMN_NAME = 'mentor_comment');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE leave_requests ADD COLUMN mentor_comment VARCHAR(500) NULL AFTER mentor_status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND COLUMN_NAME = 'hod_id');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE leave_requests ADD COLUMN hod_id BIGINT UNSIGNED NULL AFTER mentor_comment', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND COLUMN_NAME = 'hod_status');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE leave_requests ADD COLUMN hod_status VARCHAR(20) NOT NULL DEFAULT ''SUBMITTED'' AFTER hod_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND COLUMN_NAME = 'hod_comment');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE leave_requests ADD COLUMN hod_comment VARCHAR(500) NULL AFTER hod_status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND COLUMN_NAME = 'final_status');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE leave_requests ADD COLUMN final_status VARCHAR(20) NOT NULL DEFAULT ''SUBMITTED'' AFTER hod_comment', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND COLUMN_NAME = 'submitted_at');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE leave_requests ADD COLUMN submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER final_status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND COLUMN_NAME = 'decided_at');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE leave_requests ADD COLUMN decided_at DATETIME(6) NULL AFTER submitted_at', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @k_exists = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND CONSTRAINT_NAME = 'fk_leave_mentor');
SET @sql = IF(@k_exists = 0, 'ALTER TABLE leave_requests ADD CONSTRAINT fk_leave_mentor FOREIGN KEY (mentor_id) REFERENCES users(id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @k_exists = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND CONSTRAINT_NAME = 'fk_leave_hod');
SET @sql = IF(@k_exists = 0, 'ALTER TABLE leave_requests ADD CONSTRAINT fk_leave_hod FOREIGN KEY (hod_id) REFERENCES users(id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND INDEX_NAME = 'idx_leaves_final_status');
SET @sql = IF(@i_exists = 0, 'ALTER TABLE leave_requests ADD INDEX idx_leaves_final_status (final_status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'leave_requests' AND INDEX_NAME = 'idx_leaves_from_date');
SET @sql = IF(@i_exists = 0, 'ALTER TABLE leave_requests ADD INDEX idx_leaves_from_date (from_date)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 10. Alter Table: issued_documents (VerifiableDocument entity)
ALTER TABLE issued_documents
  MODIFY COLUMN type VARCHAR(60) NOT NULL,
  MODIFY COLUMN student_id BIGINT UNSIGNED NULL;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'issued_documents' AND COLUMN_NAME = 'student_name');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE issued_documents ADD COLUMN student_name VARCHAR(100) NOT NULL DEFAULT '''' AFTER student_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'issued_documents' AND COLUMN_NAME = 'masked_student_name');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE issued_documents ADD COLUMN masked_student_name VARCHAR(100) NOT NULL DEFAULT '''' AFTER student_name', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'issued_documents' AND COLUMN_NAME = 'roll_no');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE issued_documents ADD COLUMN roll_no VARCHAR(30) NULL AFTER masked_student_name', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'issued_documents' AND COLUMN_NAME = 'department');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE issued_documents ADD COLUMN department VARCHAR(100) NULL AFTER roll_no', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'issued_documents' AND COLUMN_NAME = 'issue_date');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE issued_documents ADD COLUMN issue_date DATE NOT NULL DEFAULT (CURRENT_DATE) AFTER department', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'issued_documents' AND COLUMN_NAME = 'gpa');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE issued_documents ADD COLUMN gpa VARCHAR(30) NULL AFTER revoked_reason', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'issued_documents' AND COLUMN_NAME = 'amount_paid');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE issued_documents ADD COLUMN amount_paid VARCHAR(50) NULL AFTER gpa', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'issued_documents' AND COLUMN_NAME = 'authorized_signatory');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE issued_documents ADD COLUMN authorized_signatory VARCHAR(120) NULL AFTER amount_paid', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
