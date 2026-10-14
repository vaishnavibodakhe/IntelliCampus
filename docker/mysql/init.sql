-- IntelliCampus MySQL 8 Initialization
CREATE DATABASE IF NOT EXISTS intellicampus_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Ensure campus_admin user permissions
GRANT ALL PRIVILEGES ON intellicampus_db.* TO 'campus_admin'@'%';
FLUSH PRIVILEGES;
