-- IntelliCampus MySQL 8 Initialization
CREATE DATABASE IF NOT EXISTS intellicampus CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Ensure campus_admin user permissions
GRANT ALL PRIVILEGES ON intellicampus.* TO 'campus_admin'@'%';
FLUSH PRIVILEGES;
