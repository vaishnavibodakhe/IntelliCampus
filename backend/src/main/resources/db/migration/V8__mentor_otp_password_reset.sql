-- V8__mentor_otp_password_reset.sql
-- Mentor & Staff Authentication: Secure OTP Password Reset Flow
-- Adds attempts and reset_token_hash columns to password_reset_tokens table safely

-- 1. Conditionally add attempts column if not already present
SET @col_attempts_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'password_reset_tokens'
      AND COLUMN_NAME = 'attempts'
);

SET @sql_attempts = IF(@col_attempts_exists = 0,
    'ALTER TABLE password_reset_tokens ADD COLUMN attempts INT NOT NULL DEFAULT 0 AFTER used',
    'SELECT "Column attempts already exists"'
);

PREPARE stmt_attempts FROM @sql_attempts;
EXECUTE stmt_attempts;
DEALLOCATE PREPARE stmt_attempts;

-- 2. Conditionally add reset_token_hash column if not already present
SET @col_reset_hash_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'password_reset_tokens'
      AND COLUMN_NAME = 'reset_token_hash'
);

SET @sql_reset_hash = IF(@col_reset_hash_exists = 0,
    'ALTER TABLE password_reset_tokens ADD COLUMN reset_token_hash VARCHAR(64) NULL AFTER token_hash',
    'SELECT "Column reset_token_hash already exists"'
);

PREPARE stmt_reset_hash FROM @sql_reset_hash;
EXECUTE stmt_reset_hash;
DEALLOCATE PREPARE stmt_reset_hash;

-- 3. Conditionally create index for reset_token_hash
SET @idx_reset_hash_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'password_reset_tokens'
      AND INDEX_NAME = 'idx_pwd_reset_token_hash_token'
);

SET @sql_idx = IF(@idx_reset_hash_exists = 0,
    'CREATE INDEX idx_pwd_reset_token_hash_token ON password_reset_tokens (reset_token_hash)',
    'SELECT "Index idx_pwd_reset_token_hash_token already exists"'
);

PREPARE stmt_idx FROM @sql_idx;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;
