-- IntelliCampus MySQL Database Migration V6
-- Staff Authentication: OTP Password Reset Flow
-- Ensures password_reset_tokens table aligns with 6-digit OTP hash and attempt counter tracking

CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  attempts INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_pwd_reset_token_hash (token_hash),
  INDEX idx_pwd_reset_user_id (user_id),
  INDEX idx_pwd_reset_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
