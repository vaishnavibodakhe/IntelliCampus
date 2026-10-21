-- IntelliCampus MySQL Database Migration V15
-- Admin-Managed Staff Accounts: Disable any legacy pending staff registrations

UPDATE users
SET status = 'DISABLED'
WHERE role IN ('HOD', 'FACULTY', 'MENTOR')
  AND status IN ('PENDING', 'PENDING_APPROVAL', 'PENDING_VERIFICATION');
