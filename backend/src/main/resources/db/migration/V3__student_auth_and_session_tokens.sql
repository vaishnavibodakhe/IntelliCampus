-- V3__student_auth_and_session_tokens.sql
-- IntelliCampus Student Auth, Token Revocation Denylist & Anti-Proxy Session Tokens
-- Compliant with IC-SRS-1.0 and DPDP Act 2023

-- 1. Extend students table with contact number and multi-frame embedding storage
ALTER TABLE students
  ADD COLUMN contact_no VARCHAR(20) NULL AFTER roll_no,
  ADD COLUMN face_embeddings_multi JSON NULL AFTER face_embedding;

-- 2. Extend attendance_sessions table with single-use nonce, photo counter and session expiration
ALTER TABLE attendance_sessions
  ADD COLUMN session_nonce VARCHAR(64) NULL AFTER challenge_code,
  ADD COLUMN photo_count INT NOT NULL DEFAULT 0 AFTER photo_window_ends_at,
  ADD COLUMN expires_at DATETIME(6) NULL AFTER photo_count;

-- 3. Extend session_photos table with spoof score and faculty review flag
ALTER TABLE session_photos
  ADD COLUMN spoof_score DOUBLE NULL AFTER phash,
  ADD COLUMN flagged_for_review BOOLEAN NOT NULL DEFAULT FALSE AFTER spoof_score;

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
