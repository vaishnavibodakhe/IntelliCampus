-- IntelliCampus MySQL Database Migration V14
-- Database-level Single Admin Singleton Guarantee & Legacy Seed Credential Neutralization
--
-- 1. Neutralize demo seed users from V1 and V3 with known default passwords ($2a$10$wK1q7e7C60... == Password@123)
--    Preserves table rows and foreign key integrity across audit_logs, devices, students, etc.
-- 2. Demote legacy demo admin row (id=1) to PUBLIC with DISABLED status so it will not conflict with
--    the environment-driven SuperAdminSeeder or the admin singleton constraint.
-- 3. Add generated column `admin_singleton` with UNIQUE index to mathematically guarantee at most ONE admin row.

-- Step 1: Neutralize any user accounts created with known default demo hashes
UPDATE users
SET status = 'DISABLED',
    password_hash = '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'
WHERE password_hash = '$2a$10$wK1q7e7C60.F36/LpM4wPeI6s35b91tCjN8sXq5j2iR9z6kC8rEeq';

-- Step 2: Demote legacy admin.patel seed user (id=1) to ensure zero active ADMIN rows prior to seeder execution
UPDATE users
SET role = 'PUBLIC',
    status = 'DISABLED',
    email = CONCAT('legacy.admin.', id, '@intellicampus.local'),
    user_code = CONCAT('LEGACY-ADM-', id)
WHERE id = 1 AND role = 'ADMIN';

-- Step 3: Add database-level guarantee of a single admin
-- In MySQL, NULL values are distinct in a UNIQUE index.
-- When role = 'ADMIN', admin_singleton evaluates to 1.
-- Attempting to insert or update a second row with role='ADMIN' evaluates to 1, violating UNIQUE(admin_singleton).
ALTER TABLE users
  ADD COLUMN admin_singleton TINYINT GENERATED ALWAYS AS (IF(role = 'ADMIN', 1, NULL)) VIRTUAL,
  ADD CONSTRAINT uq_users_admin_singleton UNIQUE (admin_singleton);
