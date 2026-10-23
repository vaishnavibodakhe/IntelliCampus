-- Migration V18: Drop knowledge_documents table and purge demo records so a fresh database starts completely empty

-- 1. Drop knowledge_documents table for document-based knowledge feature
DROP TABLE IF EXISTS knowledge_documents;

-- 2. Clean up demo credentials and issued documents
DELETE FROM issued_documents;

-- 3. Clean up demo notices and receipts
DELETE FROM notice_read_receipts;
DELETE FROM notices;

-- 4. Clean up academic setup demo data (rooms, subjects, divisions, departments)
DELETE FROM attendance_records;
DELETE FROM attendance_sessions;
DELETE FROM mark_correction_requests;
DELETE FROM mark_entries;
DELETE FROM leave_requests;
DELETE FROM risk_assessments;
DELETE FROM session_photos;

-- Dissociate student and user department references before deletion
DELETE FROM students;
DELETE FROM divisions;
DELETE FROM subjects;
DELETE FROM rooms;

UPDATE users SET department_id = NULL;
DELETE FROM departments;

-- 5. Remove seed/demo users while preserving any registered PRINCIPAL
DELETE FROM refresh_tokens WHERE user_id NOT IN (SELECT id FROM users WHERE role = 'PRINCIPAL');
DELETE FROM password_reset_tokens WHERE user_id NOT IN (SELECT id FROM users WHERE role = 'PRINCIPAL');
DELETE FROM users WHERE role != 'PRINCIPAL';
