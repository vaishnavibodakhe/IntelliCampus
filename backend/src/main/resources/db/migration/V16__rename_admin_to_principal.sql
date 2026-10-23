-- Migration V16: Rename ADMIN role to PRINCIPAL and create knowledge_documents table

-- 1. Update users table role enum to replace ADMIN with PRINCIPAL
ALTER TABLE users MODIFY COLUMN role ENUM('PRINCIPAL','HOD','FACULTY','MENTOR','STUDENT','PUBLIC','ADMIN') NOT NULL;
UPDATE users SET role = 'PRINCIPAL' WHERE role = 'ADMIN';
ALTER TABLE users MODIFY COLUMN role ENUM('PRINCIPAL','HOD','FACULTY','MENTOR','STUDENT','PUBLIC') NOT NULL;

-- 2. Update publisher_role in notices if ADMIN exists
UPDATE notices SET publisher_role = 'PRINCIPAL' WHERE publisher_role = 'ADMIN';

-- 3. Create knowledge_documents table for Principal Chatbot Knowledge Base
CREATE TABLE IF NOT EXISTS knowledge_documents (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    content LONGTEXT,
    category VARCHAR(100) DEFAULT 'General',
    pages INT DEFAULT 1,
    chunks_indexed INT DEFAULT 1,
    status VARCHAR(50) DEFAULT 'INDEXED',
    uploaded_by BIGINT UNSIGNED,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_knowledge_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
