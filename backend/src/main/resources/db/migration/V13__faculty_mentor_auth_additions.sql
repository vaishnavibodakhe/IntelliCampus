-- V13__faculty_mentor_auth_additions.sql
-- Flyway Migration V13: Faculty & Mentor Portal Auth Additions, Token Revocation, and OTP Reset
-- Preserves schema changes from feature/mysql-migration without colliding with origin/main V3-V12

-- 1. Ensure phone column on users table (idempotent)
SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'phone');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE users ADD COLUMN phone VARCHAR(20) NULL AFTER email', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Ensure specialization column on users table (idempotent)
SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'specialization');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE users ADD COLUMN specialization VARCHAR(100) NULL AFTER phone', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. Create revoked_tokens table for token blacklist/revocation
CREATE TABLE IF NOT EXISTS revoked_tokens (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  revoked_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  expires_at DATETIME(6) NOT NULL,
  INDEX idx_revoked_token_hash (token_hash),
  INDEX idx_revoked_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Ensure reset_token_hash on password_reset_tokens table (idempotent)
SET @c_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'password_reset_tokens' AND COLUMN_NAME = 'reset_token_hash');
SET @sql = IF(@c_exists = 0, 'ALTER TABLE password_reset_tokens ADD COLUMN reset_token_hash VARCHAR(64) NULL AFTER token_hash', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. Ensure index on reset_token_hash if not present (idempotent)
SET @i_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'password_reset_tokens' AND INDEX_NAME = 'idx_pwd_reset_token_hash_token');
SET @sql = IF(@i_exists = 0, 'CREATE INDEX idx_pwd_reset_token_hash_token ON password_reset_tokens (reset_token_hash)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
