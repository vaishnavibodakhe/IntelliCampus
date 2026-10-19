ALTER TABLE users
  ADD COLUMN department_id BIGINT UNSIGNED NULL,
  ADD CONSTRAINT fk_users_dept FOREIGN KEY (department_id) REFERENCES departments(id);
