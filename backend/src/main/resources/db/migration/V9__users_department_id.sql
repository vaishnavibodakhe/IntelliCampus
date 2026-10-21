-- Ensure department_id column exists on users table (idempotent if already added in V3)
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'department_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE users ADD COLUMN department_id BIGINT UNSIGNED NULL, ADD CONSTRAINT fk_users_dept FOREIGN KEY (department_id) REFERENCES departments(id)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
