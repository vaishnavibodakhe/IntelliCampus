-- IntelliCampus MySQL Database Migration V8
-- Make legacy column expiry_date nullable in password_reset_tokens table
-- Entity maps to 'expires_at'; making expiry_date NULL prevents MySQL 1364 default value error

ALTER TABLE password_reset_tokens MODIFY COLUMN expiry_date DATETIME(6) NULL;
