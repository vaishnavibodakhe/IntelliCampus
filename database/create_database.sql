-- ============================================================================
-- IntelliCampus Database Creation & User Provisioning
-- Target Database: intellicampus
-- Character Set: utf8mb4 | Collation: utf8mb4_unicode_ci
-- ============================================================================

-- 1. Create Database
CREATE DATABASE IF NOT EXISTS intellicampus
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- 2. Create Application User (Replace '<CHANGE_ME>' with your secure password)
CREATE USER IF NOT EXISTS 'campus_admin'@'localhost' IDENTIFIED BY '<CHANGE_ME>';
CREATE USER IF NOT EXISTS 'campus_admin'@'%' IDENTIFIED BY '<CHANGE_ME>';

-- 3. Grant Permissions to campus_admin on intellicampus schema
GRANT ALL PRIVILEGES ON intellicampus.* TO 'campus_admin'@'localhost';
GRANT ALL PRIVILEGES ON intellicampus.* TO 'campus_admin'@'%';

-- 4. Apply Changes
FLUSH PRIVILEGES;
