-- V8__student_auth_and_session_tokens.sql
-- IntelliCampus Student Auth, Token Revocation Denylist & Anti-Proxy Session Tokens
-- Compliant with IC-SRS-1.0 and DPDP Act 2023

-- 1. Extend students table with contact number and multi-frame embedding storage (idempotent)
SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND COLUMN_NAME = 'contact_no');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE students ADD COLUMN contact_no VARCHAR(20) NULL AFTER roll_no', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND COLUMN_NAME = 'face_embeddings_multi');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE students ADD COLUMN face_embeddings_multi JSON NULL AFTER face_embedding', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Extend attendance_sessions table with single-use nonce, photo counter and session expiration (idempotent)
SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance_sessions' AND COLUMN_NAME = 'session_nonce');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE attendance_sessions ADD COLUMN session_nonce VARCHAR(64) NULL AFTER challenge_code', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance_sessions' AND COLUMN_NAME = 'photo_count');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE attendance_sessions ADD COLUMN photo_count INT NOT NULL DEFAULT 0 AFTER photo_window_ends_at', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance_sessions' AND COLUMN_NAME = 'expires_at');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE attendance_sessions ADD COLUMN expires_at DATETIME(6) NULL AFTER photo_count', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. Extend session_photos table with spoof score and faculty review flag (idempotent)
SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'session_photos' AND COLUMN_NAME = 'spoof_score');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE session_photos ADD COLUMN spoof_score DOUBLE NULL AFTER phash', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'session_photos' AND COLUMN_NAME = 'flagged_for_review');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE session_photos ADD COLUMN flagged_for_review BOOLEAN NOT NULL DEFAULT FALSE AFTER spoof_score', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. Create token_denylist table for immediate JWT invalidation on logout
CREATE TABLE IF NOT EXISTS token_denylist (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  token VARCHAR(512) NOT NULL UNIQUE,
  user_id BIGINT UNSIGNED NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_token_denylist_token (token),
  INDEX idx_token_denylist_expires (expires_at),
  CONSTRAINT fk_token_denylist_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
