-- V3__auth_and_demo_users.sql
-- Schema adjustments for auth, student self-registration, password reset tokens and demo seed users

-- 1. Ensure department_id column on users table
ALTER TABLE users 
  ADD COLUMN department_id BIGINT UNSIGNED NULL AFTER locked_until;

ALTER TABLE users 
  ADD CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL;

-- 2. Make students.mentor_id nullable for newly self-registered students
ALTER TABLE students 
  MODIFY COLUMN mentor_id BIGINT UNSIGNED NULL;

-- 3. Password Reset Tokens table
CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash VARCHAR(100) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  attempts INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_reset_user (user_id),
  INDEX idx_reset_token_hash (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Seed Academic & Admin Departments
INSERT INTO departments (id, code, name) VALUES 
  (1, 'CS', 'Computer Science'),
  (2, 'IT', 'Information Technology'),
  (3, 'MECH', 'Mechanical Engineering'),
  (4, 'CIVIL', 'Civil Engineering'),
  (5, 'ADMIN', 'Administration')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 5. Seed Divisions
INSERT INTO divisions (id, department_id, year, name) VALUES 
  (1, 1, 1, 'Year 1 Div A'),
  (2, 1, 2, 'Year 2 Div A'),
  (3, 1, 3, 'Year 3 Div A'),
  (4, 1, 4, 'Year 4 Div A'),
  (5, 1, 3, 'Div A')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 6. Seed Demo Users
-- Demo Password: CampusPass2026!
-- BCrypt Hash: $2a$10$/cWWItCHrAoFqjxyQMG2IONrVwD1vCRRujGtGgeKmPfEQbyPbxi3e

INSERT INTO users (id, user_code, role, name, email, password_hash, must_change_password, status, department_id) VALUES
  (1, 'admin.patel', 'ADMIN', 'Rajesh Patel', 'admin@intellicampus.edu', '$2a$10$/cWWItCHrAoFqjxyQMG2IONrVwD1vCRRujGtGgeKmPfEQbyPbxi3e', false, 'ACTIVE', 5),
  (2, 'hod.sharma', 'HOD', 'Dr. Ananya Sharma', 'hod.cs@intellicampus.edu', '$2a$10$/cWWItCHrAoFqjxyQMG2IONrVwD1vCRRujGtGgeKmPfEQbyPbxi3e', false, 'ACTIVE', 1),
  (3, 'prof.verma', 'FACULTY', 'Prof. Vikram Verma', 'v.verma@intellicampus.edu', '$2a$10$/cWWItCHrAoFqjxyQMG2IONrVwD1vCRRujGtGgeKmPfEQbyPbxi3e', false, 'ACTIVE', 1),
  (4, 'dr.mehta', 'MENTOR', 'Dr. Sunita Mehta', 's.mehta@intellicampus.edu', '$2a$10$/cWWItCHrAoFqjxyQMG2IONrVwD1vCRRujGtGgeKmPfEQbyPbxi3e', false, 'ACTIVE', 1),
  (5, 'student.aarav', 'STUDENT', 'Aarav Shah', 'aarav.cs24@intellicampus.edu', '$2a$10$/cWWItCHrAoFqjxyQMG2IONrVwD1vCRRujGtGgeKmPfEQbyPbxi3e', false, 'ACTIVE', 1),
  (6, 'student.priya', 'STUDENT', 'Priya Nair', 'priya.cs25@intellicampus.edu', '$2a$10$/cWWItCHrAoFqjxyQMG2IONrVwD1vCRRujGtGgeKmPfEQbyPbxi3e', false, 'ACTIVE', 1),
  (7, 'student.rohan', 'STUDENT', 'Rohan Deshmukh', 'rohan.cs26@intellicampus.edu', '$2a$10$/cWWItCHrAoFqjxyQMG2IONrVwD1vCRRujGtGgeKmPfEQbyPbxi3e', false, 'ACTIVE', 1),
  (8, 'student.kavya', 'STUDENT', 'Kavya Iyer', 'kavya.cs27@intellicampus.edu', '$2a$10$/cWWItCHrAoFqjxyQMG2IONrVwD1vCRRujGtGgeKmPfEQbyPbxi3e', false, 'ACTIVE', 1)
ON DUPLICATE KEY UPDATE 
  password_hash=VALUES(password_hash),
  role=VALUES(role),
  name=VALUES(name),
  department_id=VALUES(department_id);

-- 7. Seed Student Profiles
INSERT INTO students (id, user_id, roll_no, division_id, mentor_id, face_enrolled, attendance_rate, backlogs, fee_status, due_amount) VALUES
  (1, 5, 'CS-104', 5, 4, true, 84.5, 0, 'PAID', 0.0),
  (2, 6, 'CS-105', 5, 4, true, 64.0, 2, 'DUE', 18500.0),
  (3, 7, 'CS-106', 5, 4, true, 91.2, 0, 'PAID', 0.0),
  (4, 8, 'CS-107', 5, 4, false, 58.2, 1, 'PAID', 0.0)
ON DUPLICATE KEY UPDATE
  roll_no=VALUES(roll_no),
  division_id=VALUES(division_id),
  mentor_id=VALUES(mentor_id),
  face_enrolled=VALUES(face_enrolled),
  attendance_rate=VALUES(attendance_rate);
