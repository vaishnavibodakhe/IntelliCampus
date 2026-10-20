-- Align users.status column with Java UserStatus enum (supports PENDING for student registration)
ALTER TABLE users MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
