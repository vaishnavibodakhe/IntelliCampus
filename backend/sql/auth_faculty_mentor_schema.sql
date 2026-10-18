-- ============================================================================
-- IntelliCampus: Faculty & Mentor Authentication Schema Additions
-- Standalone SQL script for MySQL 8+
-- ============================================================================

USE intellicampus;

-- 1. Ensure phone column exists on users table
SET @col_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'users' 
      AND COLUMN_NAME = 'phone'
);

SET @alter_sql = IF(@col_exists = 0, 'ALTER TABLE users ADD COLUMN phone VARCHAR(20) NULL AFTER email;', 'SELECT "phone column already exists";');
PREPARE stmt FROM @alter_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Password Reset Tokens Table (stores only SHA-256 hash of tokens)
CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_reset_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_reset_token_hash (token_hash),
  INDEX idx_reset_token_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Revoked / Blacklisted JWT Tokens Table for Logout
CREATE TABLE IF NOT EXISTS revoked_tokens (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  token_hash CHAR(64) NOT NULL UNIQUE,
  revoked_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  expires_at DATETIME(6) NOT NULL,
  INDEX idx_revoked_token_hash (token_hash),
  INDEX idx_revoked_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
