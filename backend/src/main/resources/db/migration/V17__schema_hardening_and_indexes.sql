-- IntelliCampus MySQL Database Migration V17
-- Schema Hardening, Explicit Indexes, utf8mb4 Collation & Principal Singleton Guarantee

-- 1. Ensure students has department_id for department-scoped roll_no uniqueness
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND COLUMN_NAME = 'department_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE students ADD COLUMN department_id BIGINT UNSIGNED NULL AFTER roll_no', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Backfill department_id from division relation if null
UPDATE students s
JOIN divisions d ON s.division_id = d.id
SET s.department_id = d.department_id
WHERE s.department_id IS NULL;

-- Add foreign key constraint for students.department_id
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND CONSTRAINT_NAME = 'fk_students_department');
SET @sql = IF(@fk_exists = 0, 'ALTER TABLE students ADD CONSTRAINT fk_students_department FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE CASCADE', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Add Department-Scoped Roll Number Index
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND INDEX_NAME = 'idx_students_dept_roll');
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE students ADD INDEX idx_students_dept_roll (department_id, roll_no)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. Ensure Explicit Index on users.email
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_users_email');
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE users ADD INDEX idx_users_email (email)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. Ensure Explicit Index on users.department_id
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_users_department_id');
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE users ADD INDEX idx_users_department_id (department_id)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. Ensure Explicit Index on users.role
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_users_role');
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE users ADD INDEX idx_users_role (role)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6. Replace legacy admin_singleton with principal_singleton
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'admin_singleton');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE users DROP COLUMN admin_singleton', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'principal_singleton');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE users ADD COLUMN principal_singleton TINYINT GENERATED ALWAYS AS (IF(role = ''PRINCIPAL'', 1, NULL)) VIRTUAL, ADD CONSTRAINT uq_users_principal_singleton UNIQUE (principal_singleton)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 7. Ensure utf8mb4_unicode_ci Collation on Core User and Academic Tables
ALTER TABLE users CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE departments CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE divisions CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE subjects CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE rooms CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE students CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE notices CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE issued_documents CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE knowledge_documents CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE audit_logs CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
