-- IntelliCampus MySQL Database Migration V5
-- Staff Authentication (HOD & Admin), Approval Workflow, Refresh Tokens & Secure Password Reset
-- Preserves existing users.status ENUM values and matches BIGINT UNSIGNED for users.id foreign keys

-- 1. Extend users table status ENUM to support Admin Approval workflow
ALTER TABLE users 
  MODIFY COLUMN status ENUM('ACTIVE','LOCKED','SUSPENDED','PENDING_VERIFICATION','PENDING_APPROVAL','REJECTED','DISABLED') NOT NULL DEFAULT 'ACTIVE';

-- 2. Password Reset Tokens Table (Stores SHA-256 hash of 32-byte SecureRandom token)
CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expiry_date DATETIME(6) NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_pwd_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_pwd_reset_token_hash (token_hash),
  INDEX idx_pwd_reset_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Refresh Tokens Table (Secure token rotation & revocation)
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expiry_date DATETIME(6) NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_refresh_token_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Password Reset Rate Limiting Table (Max 3 requests per hour per email & per IP)
CREATE TABLE IF NOT EXISTS password_reset_rate_limits (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(120) NOT NULL,
  ip_address VARCHAR(45) NOT NULL,
  requested_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_pwd_rate_email (email, requested_at),
  INDEX idx_pwd_rate_ip (ip_address, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
