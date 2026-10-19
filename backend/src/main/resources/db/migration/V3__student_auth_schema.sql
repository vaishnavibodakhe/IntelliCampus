-- IntelliCampus MySQL Database Migration V2
-- Student Self-Registration, Auth & Profile Alignment

-- 1. Ensure department_id column exists on users table for JPA mapping
ALTER TABLE users 
  ADD COLUMN department_id BIGINT UNSIGNED NULL AFTER locked_until;

ALTER TABLE users 
  ADD CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL;

-- 2. Modify students table: allow mentor_id to be NULL for newly self-registered students
ALTER TABLE students 
  MODIFY COLUMN mentor_id BIGINT UNSIGNED NULL;

-- 3. Default Academic Departments Seed Data
INSERT INTO departments (id, code, name) VALUES 
  (1, 'CS', 'Computer Science'),
  (2, 'IT', 'Information Technology'),
  (3, 'MECH', 'Mechanical Engineering'),
  (4, 'CIVIL', 'Civil Engineering'),
  (5, 'ENTC', 'Electronics & Telecommunication')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 4. Default Divisions Seed Data
INSERT INTO divisions (id, department_id, year, name) VALUES 
  (1, 1, 1, 'Year 1 Div A'),
  (2, 1, 2, 'Year 2 Div A'),
  (3, 1, 3, 'Year 3 Div A'),
  (4, 1, 4, 'Year 4 Div A'),
  (5, 2, 3, 'Year 3 Div A')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 5. Default Test Student Account (Email: aarav.cs24@intellicampus.edu / Password: Password@123)
-- BCrypt Hash: $2a$10$wK1q7e7C60.F36/LpM4wPeI6s35b91tCjN8sXq5j2iR9z6kC8rEeq
INSERT INTO users (id, user_code, role, name, email, password_hash, must_change_password, status, department_id)
VALUES (
  5, 
  'STU-CS-104', 
  'STUDENT', 
  'Aarav Shah', 
  'aarav.cs24@intellicampus.edu', 
  '$2a$10$wK1q7e7C60.F36/LpM4wPeI6s35b91tCjN8sXq5j2iR9z6kC8rEeq', 
  false, 
  'ACTIVE', 
  1
) ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash);

INSERT INTO students (id, user_id, roll_no, division_id, mentor_id)
VALUES (
  1, 
  5, 
  'CS-104', 
  3, 
  NULL
) ON DUPLICATE KEY UPDATE roll_no=VALUES(roll_no);
