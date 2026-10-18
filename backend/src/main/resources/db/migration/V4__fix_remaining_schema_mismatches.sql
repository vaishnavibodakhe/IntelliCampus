-- IntelliCampus MySQL Database Migration V4
-- Fix Remaining Schema Mismatches for Hibernate 6.4 (ddl-auto: validate)
-- 1. Maps @Enumerated(EnumType.STRING) columns to native MySQL ENUM types expected by Hibernate 6.4 MySQLDialect
-- 2. Aligns CHAR(64) cryptographic hash columns with Java String (VARCHAR(64)) expectations
-- 3. Aligns JSON columns with columnDefinition = "TEXT"

-- 1. attendance_records: final_status enum
ALTER TABLE attendance_records
  MODIFY COLUMN final_status ENUM('PRESENT','MATCHED','REVIEW','ABSENT','PENDING') NOT NULL DEFAULT 'PENDING';

-- 2. attendance_sessions: status enum
ALTER TABLE attendance_sessions
  MODIFY COLUMN status ENUM('OPEN','PHOTO_WINDOW','FINALISED','CANCELLED') NOT NULL DEFAULT 'OPEN';

-- 3. leave_requests: mentor_status, hod_status, final_status enums, and relax legacy status column
ALTER TABLE leave_requests
  MODIFY COLUMN status VARCHAR(20) NULL DEFAULT 'SUBMITTED',
  MODIFY COLUMN mentor_status ENUM('SUBMITTED','APPROVED_BY_MENTOR','REJECTED_BY_MENTOR','APPROVED_BY_HOD','REJECTED_BY_HOD','CANCELLED') NOT NULL DEFAULT 'SUBMITTED',
  MODIFY COLUMN hod_status ENUM('SUBMITTED','APPROVED_BY_MENTOR','REJECTED_BY_MENTOR','APPROVED_BY_HOD','REJECTED_BY_HOD','CANCELLED') NOT NULL DEFAULT 'SUBMITTED',
  MODIFY COLUMN final_status ENUM('SUBMITTED','APPROVED_BY_MENTOR','REJECTED_BY_MENTOR','APPROVED_BY_HOD','REJECTED_BY_HOD','CANCELLED') NOT NULL DEFAULT 'SUBMITTED';

-- 4. mark_entries: status enum
ALTER TABLE mark_entries
  MODIFY COLUMN status ENUM('DRAFT','SUBMITTED','VERIFIED','LOCKED') NOT NULL DEFAULT 'DRAFT';

-- 5. mark_correction_requests: status enum
ALTER TABLE mark_correction_requests
  MODIFY COLUMN status ENUM('PENDING_HOD','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING_HOD';

-- 6. risk_assessments: level & trend enums
ALTER TABLE risk_assessments
  MODIFY COLUMN level ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
  MODIFY COLUMN trend ENUM('IMPROVING','STABLE','DETERIORATING') DEFAULT 'STABLE';

-- 7. users: role & status enums
ALTER TABLE users
  MODIFY COLUMN role ENUM('ADMIN','HOD','FACULTY','MENTOR','STUDENT','PUBLIC') NOT NULL,
  MODIFY COLUMN status ENUM('ACTIVE','LOCKED','SUSPENDED','PENDING_VERIFICATION') DEFAULT 'ACTIVE';

-- 8. issued_documents: status enum & file_hash VARCHAR(64)
ALTER TABLE issued_documents
  MODIFY COLUMN status ENUM('VALID','REVOKED','EXPIRED') NOT NULL DEFAULT 'VALID',
  MODIFY COLUMN file_hash VARCHAR(64) NOT NULL;

-- 9. audit_logs: prev_hash, entry_hash (CHAR -> VARCHAR), and old_value/new_value (JSON -> TEXT)
ALTER TABLE audit_logs
  MODIFY COLUMN prev_hash VARCHAR(64) NOT NULL,
  MODIFY COLUMN entry_hash VARCHAR(64) NOT NULL,
  MODIFY COLUMN old_value TEXT NULL,
  MODIFY COLUMN new_value TEXT NULL;

-- 10. session_photos: sha256 (CHAR -> VARCHAR)
ALTER TABLE session_photos
  MODIFY COLUMN sha256 VARCHAR(64) NOT NULL;
