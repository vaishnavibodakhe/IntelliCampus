# =====================================================================
# IntelliCampus GitHub Issues & Milestone Automation Script
# Repository: vaishnavibodakhe/IntelliCampus
# =====================================================================

$Repo = "vaishnavibodakhe/IntelliCampus"
$MilestoneTitle = "IntelliCampus v1"

Write-Host ">>> Checking GitHub CLI authentication..." -ForegroundColor Cyan
gh auth status
if ($LASTEXITCODE -ne 0) {
    Write-Host "`n[ERROR] GitHub CLI is not authenticated!" -ForegroundColor Red
    Write-Host "Please authenticate by running:" -ForegroundColor Yellow
    Write-Host "    gh auth login`n" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n>>> Ensuring labels exist on $Repo..." -ForegroundColor Cyan
$labels = @(
    @{ Name = "frontend"; Color = "1d76db"; Description = "Frontend & UI components" },
    @{ Name = "backend"; Color = "5319e7"; Description = "Backend, API & Business Logic" },
    @{ Name = "database"; Color = "006b75"; Description = "Database models, schemas & persistence" },
    @{ Name = "validation"; Color = "fbca04"; Description = "Validation, security & error handling" },
    @{ Name = "testing"; Color = "0e8a16"; Description = "Automated unit and integration testing" },
    @{ Name = "documentation"; Color = "0075ca"; Description = "Documentation & specifications" },
    @{ Name = "member-1"; Color = "e99695"; Description = "Assigned to Bhumika Khairnar (Leader)" },
    @{ Name = "member-2"; Color = "f9d0c4"; Description = "Assigned to Siddhi Kolage" },
    @{ Name = "member-3"; Color = "c5def5"; Description = "Assigned to Gayatri Jadhav" },
    @{ Name = "member-4"; Color = "d4c5f9"; Description = "Assigned to Vaishnavi Bodakhe" }
)

foreach ($l in $labels) {
    gh label create $l.Name --color $l.Color --description $l.Description --repo $Repo 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  Created label: $($l.Name)" -ForegroundColor Green
    } else {
        Write-Host "  Label exists or updated: $($l.Name)" -ForegroundColor Gray
    }
}

Write-Host "`n>>> Ensuring milestone '$MilestoneTitle' exists..." -ForegroundColor Cyan
gh api "repos/$Repo/milestones" --jq ".[].title" | Select-String -Pattern "^$MilestoneTitle$" -Quiet
$milestoneExists = $LASTEXITCODE -eq 0
if (-not $milestoneExists) {
    gh milestone create --title $MilestoneTitle --description "IntelliCampus Version 1.0 Milestone" --repo $Repo 2>$null
    Write-Host "  Created milestone '$MilestoneTitle'" -ForegroundColor Green
} else {
    Write-Host "  Milestone '$MilestoneTitle' already exists" -ForegroundColor Gray
}

# 24 Issues Definition
$issues = @(
    # --- MEMBER 1: Bhumika Khairnar (khairnarbhumika34-ship-it) ---
    @{
        Title = "[Smart Attendance] UI: Real-Time Dynamic QR Display & GPS Confirmation Interface"
        Assignee = "khairnarbhumika34-ship-it"
        Labels = "frontend,member-1"
        Body = @"
### Overview
Develop and refine the faculty live session QR projection interface and student self-check-in GPS confirmation UI.

### Files to Touch
- ``src/components/FacultyView.jsx``
- ``src/components/StudentView.jsx``
- ``src/utils/cryptoAndGeo.js``

### Acceptance Criteria Checklist
- [ ] Faculty can launch an attendance session and project a 15-second rotating dynamic QR token with a visual countdown timer.
- [ ] Student UI requests device geolocation and calculates distance to room coordinates.
- [ ] Student check-in displays active status (Present / Matched / Geo-out-of-range).
- [ ] Manual override actions in FacultyView update attendance status cleanly.
"@
    },
    @{
        Title = "[Smart Attendance] Backend: Attendance Session Lifecycle & Dynamic QR Token API"
        Assignee = "khairnarbhumika34-ship-it"
        Labels = "backend,member-1"
        Body = @"
### Overview
Implement Spring Boot REST endpoints and business service managing the attendance session state machine (OPEN -> PROCESSING -> REVIEW -> FINALISED) and time-slotted rotating QR tokens.

### Files to Touch
- ``backend/src/main/java/com/intellicampus/attendance/controller/AttendanceController.java``
- ``backend/src/main/java/com/intellicampus/attendance/service/AttendanceService.java``
- ``backend/src/main/java/com/intellicampus/attendance/dto/``

### Acceptance Criteria Checklist
- [ ] ``POST /api/attendance/sessions`` opens a session with roomId, subjectId, and challengeCode.
- [ ] ``GET /api/attendance/sessions/{id}/token`` returns the 15-second rotating HMAC-SHA256 token.
- [ ] ``POST /api/attendance/sessions/{id}/confirm`` records student GPS and biometric check-in.
- [ ] Session transitions properly enforce faculty-only authorization.
"@
    },
    @{
        Title = "[Smart Attendance] Database: AttendanceSession & AttendanceRecord Entities"
        Assignee = "khairnarbhumika34-ship-it"
        Labels = "database,member-1"
        Body = @"
### Overview
Create JPA entities and Spring Data repositories for attendance sessions and student attendance records.

### Files to Touch
- ``backend/src/main/java/com/intellicampus/attendance/model/AttendanceSession.java``
- ``backend/src/main/java/com/intellicampus/attendance/model/AttendanceRecord.java``
- ``backend/src/main/java/com/intellicampus/attendance/repository/AttendanceSessionRepository.java``
- ``backend/src/main/java/com/intellicampus/attendance/repository/AttendanceRecordRepository.java``

### Acceptance Criteria Checklist
- [ ] ``AttendanceSession`` entity mapped with status enum (OPEN, PROCESSING, REVIEW, FINALISED).
- [ ] ``AttendanceRecord`` entity with faceScore, spoofScore, confirmed timestamp, and finalStatus enum (PRESENT, MATCHED, REVIEW, ABSENT).
- [ ] Proper JPA indexes on ``(sessionId, studentId)`` and foreign keys to User and Subject.
"@
    },
    @{
        Title = "[Smart Attendance] Validation: Geofence Haversine Boundary & Spoof Score Validator"
        Assignee = "khairnarbhumika34-ship-it"
        Labels = "validation,member-1"
        Body = @"
### Overview
Build validation filters ensuring student location is within classroom radius and AI anti-spoofing confidence passes threshold.

### Files to Touch
- ``src/utils/cryptoAndGeo.js``
- ``backend/src/main/java/com/intellicampus/attendance/validation/GeofenceValidator.java``
- ``backend/src/main/java/com/intellicampus/attendance/validation/SpoofScoreValidator.java``

### Acceptance Criteria Checklist
- [ ] Haversine formula validates student GPS lat/lon is within room radius (default 50m).
- [ ] Reject check-ins where GPS accuracy is greater than 100 meters.
- [ ] Flag records as REVIEW if anti-spoofing confidence is below threshold (0.60).
- [ ] Provide user-friendly localized error messages on out-of-boundary submissions.
"@
    },
    @{
        Title = "[Smart Attendance] Testing: Attendance Session & Geofence Verification Unit/Integration Tests"
        Assignee = "khairnarbhumika34-ship-it"
        Labels = "testing,member-1"
        Body = @"
### Overview
Write automated test suites covering dynamic QR time-slot transitions, geofencing distance boundaries, and session finalization.

### Files to Touch
- ``backend/src/test/java/com/intellicampus/attendance/AttendanceServiceTest.java``
- ``backend/src/test/java/com/intellicampus/attendance/GeofenceValidatorTest.java``
- ``src/components/__tests__/FacultyView.test.jsx``

### Acceptance Criteria Checklist
- [ ] Unit tests verify dynamic QR token rotation every 15 seconds.
- [ ] Haversine tests verify boundary edge cases (inside perimeter, exactly on perimeter, outside perimeter).
- [ ] Spring Boot MockMvc tests verify ``/api/attendance/sessions`` authorization and check-in flows.
"@
    },
    @{
        Title = "[Smart Attendance] Documentation: Attendance System Architecture & Dynamic QR Protocol Spec"
        Assignee = "khairnarbhumika34-ship-it"
        Labels = "documentation,member-1"
        Body = @"
### Overview
Write architecture documentation and dynamic QR protocol specification for the Smart Attendance module.

### Files to Touch
- ``docs/ATTENDANCE.md``
- ``README.md`` (Attendance feature section)

### Acceptance Criteria Checklist
- [ ] Document the mathematical basis of Haversine distance and 15s epoch rotating tokens.
- [ ] Include sequence diagram of Faculty Session Start -> Student Check-In -> AI Verification.
- [ ] Document all REST endpoints, request/response DTO schemas, and status codes.
"@
    },

    # --- MEMBER 2: Siddhi Kolage (siddhi1111111) ---
    @{
        Title = "[Academics & Leave] UI: Marks Entry & Multi-Tier Leave Request Workflow Dashboard"
        Assignee = "siddhi1111111"
        Labels = "frontend,member-2"
        Body = @"
### Overview
Build the UI dashboards for faculty marks entry/drafting, student multi-tier leave submission with attachments, and HOD evaluation panels.

### Files to Touch
- ``src/components/HodView.jsx``
- ``src/components/FacultyView.jsx``
- ``src/components/StudentView.jsx``

### Acceptance Criteria Checklist
- [ ] Faculty can enter, edit, and toggle marks status between DRAFT and PUBLISHED.
- [ ] Faculty can initiate a Mark Correction request with mandatory explanation.
- [ ] Student can submit leave applications with date range, reason, and document attachment.
- [ ] HOD view displays pending mark corrections and leave approvals with Approve/Reject actions.
"@
    },
    @{
        Title = "[Academics & Leave] Backend: Marks Lifecycle & Multi-Level Leave Approval REST API"
        Assignee = "siddhi1111111"
        Labels = "backend,member-2"
        Body = @"
### Overview
Implement REST controllers and business services handling marks grading, grade corrections, and student leave workflows.

### Files to Touch
- ``backend/src/main/java/com/intellicampus/academics/controller/MarksController.java``
- ``backend/src/main/java/com/intellicampus/academics/service/MarksService.java``
- ``backend/src/main/java/com/intellicampus/leave/controller/LeaveController.java``
- ``backend/src/main/java/com/intellicampus/leave/service/LeaveService.java``

### Acceptance Criteria Checklist
- [ ] ``POST /api/marks`` creates and updates student marks.
- [ ] ``POST /api/marks/corrections`` creates a correction request requiring HOD sign-off.
- [ ] ``POST /api/leaves`` submits leave application with file upload support.
- [ ] ``PUT /api/leaves/{id}/mentor`` and ``PUT /api/leaves/{id}/hod`` process approval stages.
"@
    },
    @{
        Title = "[Academics & Leave] Database: Marks, Grade Corrections & Leave Application Schema"
        Assignee = "siddhi1111111"
        Labels = "database,member-2"
        Body = @"
### Overview
Design JPA entities and repositories for student marks, mark correction requests, and leave applications.

### Files to Touch
- ``backend/src/main/java/com/intellicampus/academics/model/MarkEntry.java``
- ``backend/src/main/java/com/intellicampus/academics/model/MarkCorrectionRequest.java``
- ``backend/src/main/java/com/intellicampus/leave/model/LeaveRequest.java``
- ``backend/src/main/java/com/intellicampus/leave/repository/LeaveRequestRepository.java``

### Acceptance Criteria Checklist
- [ ] ``MarkEntry`` mapped with studentId, subjectId, examName, marks, maxMarks, and status enum.
- [ ] ``MarkCorrectionRequest`` mapped with oldMarks, newMarks, reason, facultyId, and status.
- [ ] ``LeaveRequest`` mapped with fromDate, toDate, daysCount, attachmentUrl, mentorStatus, hodStatus.
"@
    },
    @{
        Title = "[Academics & Leave] Validation: Mark Boundaries, Date Range & Leave Policy Validator"
        Assignee = "siddhi1111111"
        Labels = "validation,member-2"
        Body = @"
### Overview
Develop validation filters ensuring marks fall within valid ranges and leave date requests adhere to college guidelines.

### Files to Touch
- ``backend/src/main/java/com/intellicampus/academics/validation/MarkRangeValidator.java``
- ``backend/src/main/java/com/intellicampus/leave/validation/LeaveDateValidator.java``

### Acceptance Criteria Checklist
- [ ] Validate ``marks >= 0`` and ``marks <= maxMarks``.
- [ ] Ensure mark correction reason is mandatory and at least 15 characters long.
- [ ] Validate ``fromDate <= toDate`` and leave cannot be applied retroactively past allowed window.
- [ ] Enforce medical certificate attachment for sick leaves exceeding 2 consecutive days.
"@
    },
    @{
        Title = "[Academics & Leave] Testing: Marks Draft-Publish Flow & Leave Approval Tests"
        Assignee = "siddhi1111111"
        Labels = "testing,member-2"
        Body = @"
### Overview
Write automated test suites covering the marks publishing lifecycle and multi-stage leave approval transitions.

### Files to Touch
- ``backend/src/test/java/com/intellicampus/academics/MarksServiceTest.java``
- ``backend/src/test/java/com/intellicampus/leave/LeaveServiceTest.java``
- ``src/components/__tests__/HodView.test.jsx``

### Acceptance Criteria Checklist
- [ ] Tests verify student cannot view marks in DRAFT status until faculty publishes.
- [ ] Tests verify HOD approval successfully applies mark correction and creates audit entry.
- [ ] Tests verify leave transitions: SUBMITTED -> MENTOR_RECOMMENDED -> APPROVED/REJECTED.
"@
    },
    @{
        Title = "[Academics & Leave] Documentation: Academic Grading & Leave Approval Workflow Guide"
        Assignee = "siddhi1111111"
        Labels = "documentation,member-2"
        Body = @"
### Overview
Author comprehensive documentation detailing grading rubrics, mark correction procedures, and the multi-tier leave approval hierarchy.

### Files to Touch
- ``docs/ACADEMICS_AND_LEAVES.md``
- ``README.md`` (Academics & Leave sections)

### Acceptance Criteria Checklist
- [ ] Detail the complete lifecycle of internal marks from draft to publish.
- [ ] Include state diagram of leave requests from Student to Mentor to HOD.
- [ ] Provide example REST payloads for marks entry and leave approval.
"@
    },

    # --- MEMBER 3: Gayatri Jadhav (jgayatri664) ---
    @{
        Title = "[Admin & Notices] UI: User Directory, Room Geofencing & Targeted Notice Broadcast Portal"
        Assignee = "jgayatri664"
        Labels = "frontend,member-3"
        Body = @"
### Overview
Develop admin UI tabs for user management, academic room configuration with geofence perimeters, and targeted notice authoring.

### Files to Touch
- ``src/components/AdminView.jsx``
- ``src/components/Navbar.jsx``
- ``src/components/StudentView.jsx`` (Notices tab)

### Acceptance Criteria Checklist
- [ ] Admin can view, search, filter, and add users with role assignment.
- [ ] Bulk CSV import modal parses and displays preview of imported student rows.
- [ ] Room setup UI allows configuring GPS coordinates (latitude, longitude, radius).
- [ ] Targeted notice broadcast form supports filtering by Department, Year, and Division.
"@
    },
    @{
        Title = "[Admin & Notices] Backend: User Directory, Academic Geofence Setup & Targeted Notices API"
        Assignee = "jgayatri664"
        Labels = "backend,member-3"
        Body = @"
### Overview
Implement Spring Boot REST endpoints and services for user administration, classroom/subject configuration, and targeted notices broadcast.

### Files to Touch
- ``backend/src/main/java/com/intellicampus/user/controller/UserController.java``
- ``backend/src/main/java/com/intellicampus/academic/controller/AcademicSetupController.java``
- ``backend/src/main/java/com/intellicampus/notice/controller/NoticeController.java``
- ``backend/src/main/java/com/intellicampus/notice/service/NoticeService.java``

### Acceptance Criteria Checklist
- [ ] ``GET /api/users`` and ``POST /api/users`` for user management.
- [ ] ``POST /api/users/csv-import`` parses and persists batch student records.
- [ ] ``POST /api/rooms`` saves room geolocation and radius.
- [ ] ``POST /api/notices`` and ``GET /api/notices/my-feed`` filters notices based on recipient profile.
"@
    },
    @{
        Title = "[Admin & Notices] Database: User, Role, Classroom & Notice Broadcast Entities"
        Assignee = "jgayatri664"
        Labels = "database,member-3"
        Body = @"
### Overview
Design JPA entities and Spring Data repositories for users, classrooms, subjects, notices, and notice read receipts.

### Files to Touch
- ``backend/src/main/java/com/intellicampus/user/model/User.java``
- ``backend/src/main/java/com/intellicampus/academic/model/Classroom.java``
- ``backend/src/main/java/com/intellicampus/academic/model/Subject.java``
- ``backend/src/main/java/com/intellicampus/notice/model/Notice.java``
- ``backend/src/main/java/com/intellicampus/notice/model/NoticeReadReceipt.java``

### Acceptance Criteria Checklist
- [ ] ``User`` entity mapped with role enum (ADMIN, HOD, FACULTY, MENTOR, STUDENT) and status.
- [ ] ``Classroom`` entity mapped with latitude, longitude, and radiusM.
- [ ] ``Notice`` entity mapped with targetDepartment, targetYear, targetDivision, and attachments.
- [ ] ``NoticeReadReceipt`` entity tracking student read status.
"@
    },
    @{
        Title = "[Admin & Notices] Validation: CSV Batch Ingestion & Notice Audience Scope Validator"
        Assignee = "jgayatri664"
        Labels = "validation,member-3"
        Body = @"
### Overview
Implement input validation and data sanitization for CSV user uploads and notice audience targeting parameters.

### Files to Touch
- ``backend/src/main/java/com/intellicampus/user/validation/CsvUserValidator.java``
- ``backend/src/main/java/com/intellicampus/notice/validation/NoticeAudienceValidator.java``

### Acceptance Criteria Checklist
- [ ] CSV parser validates required headers: userCode, name, email, department, rollNo.
- [ ] Duplicate email or userCode entries are rejected with specific line numbers in error report.
- [ ] Notice audience validator verifies target department and year match active institutional taxonomy.
"@
    },
    @{
        Title = "[Admin & Notices] Testing: User CRUD, CSV Bulk Ingestion & Notice Delivery Tests"
        Assignee = "jgayatri664"
        Labels = "testing,member-3"
        Body = @"
### Overview
Write automated test suites verifying user creation, batch CSV ingestion, room coordinates persistence, and notice audience filtering.

### Files to Touch
- ``backend/src/test/java/com/intellicampus/user/UserServiceTest.java``
- ``backend/src/test/java/com/intellicampus/notice/NoticeServiceTest.java``
- ``src/components/__tests__/AdminView.test.jsx``

### Acceptance Criteria Checklist
- [ ] Unit tests verify valid CSV rows are imported while malformed rows are isolated.
- [ ] Integration tests verify student only receives notices matching their department/year.
- [ ] Test read receipt generation when a student accesses notice detail.
"@
    },
    @{
        Title = "[Admin & Notices] Documentation: Directory Administration & Notice Targeting Manual"
        Assignee = "jgayatri664"
        Labels = "documentation,member-3"
        Body = @"
### Overview
Create documentation for system administrators covering user directory maintenance, CSV import formats, room geofencing, and notice broadcasts.

### Files to Touch
- ``docs/ADMIN_SETUP.md``
- ``README.md`` (Admin & Setup sections)

### Acceptance Criteria Checklist
- [ ] Provide sample CSV template with expected columns and data types.
- [ ] Guide for setting up campus room GPS coordinates using Google Maps / OpenStreetMap.
- [ ] Detail audience filtering logic for targeted campus notices.
"@
    },

    # --- MEMBER 4: Vaishnavi Bodakhe (vaishnavibodakhe) ---
    @{
        Title = "[Audit & Trust] UI: Public Document Verifier, Hash Chain Inspector & EWS UI"
        Assignee = "vaishnavibodakhe"
        Labels = "frontend,member-4"
        Body = @"
### Overview
Develop frontend interfaces for public credential verification, cryptographic SHA-256 audit ledger inspection, and mentor Early Warning risk dashboard.

### Files to Touch
- ``src/components/PublicVerifierView.jsx``
- ``src/components/AuditChainModal.jsx``
- ``src/components/MentorView.jsx``
- ``src/components/ChatbotDrawer.jsx``

### Acceptance Criteria Checklist
- [ ] Public verifier allows verification by document code, pasted SHA-256 hash, or QR scan with masked identity display.
- [ ] Audit chain modal displays block explorer and interactive chain integrity verification.
- [ ] Mentor view visualizes student retention risk level, score, trend, and explainable AI factors.
- [ ] Chatbot drawer supports asking questions grounded in uploaded university policy documents.
"@
    },
    @{
        Title = "[Audit & Trust] Backend: Cryptographic Audit Chain & Document Registry REST API"
        Assignee = "vaishnavibodakhe"
        Labels = "backend,member-4"
        Body = @"
### Overview
Implement REST endpoints and services for cryptographic audit ledger management, verifiable document issuance/revocation, and AI risk evaluation.

### Files to Touch
- ``backend/src/main/java/com/intellicampus/audit/controller/AuditController.java``
- ``backend/src/main/java/com/intellicampus/audit/service/AuditChainService.java``
- ``backend/src/main/java/com/intellicampus/credential/controller/CredentialController.java``
- ``backend/src/main/java/com/intellicampus/ai/client/AiServiceClient.java``

### Acceptance Criteria Checklist
- [ ] ``GET /api/audit/chain`` and ``GET /api/audit/verify`` inspect and validate the cryptographic ledger.
- [ ] ``POST /api/credentials/issue`` issues documents with unique DOC code and SHA-256 file hash.
- [ ] ``GET /api/public/verify/{code}`` returns public verification status without authentication.
- [ ] Client communicates with FastAPI ``/ai/risk`` to retrieve explainable student risk factors.
"@
    },
    @{
        Title = "[Audit & Trust] Database: AuditLogEntry, VerifiableDocument & RiskRecord Entities"
        Assignee = "vaishnavibodakhe"
        Labels = "database,member-4"
        Body = @"
### Overview
Design JPA entities and repositories for cryptographic audit chain entries, verifiable certificates, and student risk scores.

### Files to Touch
- ``backend/src/main/java/com/intellicampus/audit/model/AuditLogEntry.java``
- ``backend/src/main/java/com/intellicampus/credential/model/VerifiableDocument.java``
- ``backend/src/main/java/com/intellicampus/ai/model/RiskAssessmentRecord.java``

### Acceptance Criteria Checklist
- [ ] ``AuditLogEntry`` mapped with id, prevHash, entryHash, actorId, action, entityType, oldValue, newValue, reason, createdAt.
- [ ] ``VerifiableDocument`` mapped with code, type, maskedStudentName, fileHash, status (VALID, REVOKED), revokedReason.
- [ ] Ensure database constraints prevent updates or deletions on ``AuditLogEntry`` (append-only ledger).
"@
    },
    @{
        Title = "[Audit & Trust] Validation: SHA-256 Hash Chain Integrity & Document Tamper Check"
        Assignee = "vaishnavibodakhe"
        Labels = "validation,member-4"
        Body = @"
### Overview
Implement cryptographic hash integrity validation routines ensuring the immutability of audit logs and digital documents.

### Files to Touch
- ``src/utils/cryptoAndGeo.js``
- ``backend/src/main/java/com/intellicampus/audit/validation/HashChainValidator.java``

### Acceptance Criteria Checklist
- [ ] Validate ``entryHash == SHA-256(prevHash | actorId | action | entityType | entityId | oldVal | newVal | reason | createdAt)``.
- [ ] Validate ``prevHash`` of block ``i`` matches ``entryHash`` of block ``i-1``.
- [ ] Detect and report exact tampered block index if content or hash link is corrupted.
- [ ] Validate document revocation reason has minimum length of 10 characters.
"@
    },
    @{
        Title = "[Audit & Trust] Testing: Tamper Detection & Document Verification Test Suite"
        Assignee = "vaishnavibodakhe"
        Labels = "testing,member-4"
        Body = @"
### Overview
Write automated test suites verifying cryptographic hash chaining, tamper detection, and public document verification.

### Files to Touch
- ``backend/src/test/java/com/intellicampus/audit/AuditChainServiceTest.java``
- ``backend/src/test/java/com/intellicampus/credential/CredentialServiceTest.java``
- ``src/utils/__tests__/cryptoAndGeo.test.js``

### Acceptance Criteria Checklist
- [ ] Test verifies that modifying a single character in any historical audit block invalidates chain integrity.
- [ ] Test verifies public document verification returns VALID for active documents and REVOKED for revoked ones.
- [ ] Test verifies FastAPI student risk service payload parsing and explainability factor extraction.
"@
    },
    @{
        Title = "[Audit & Trust] Documentation: Cryptographic Audit Specification & Credential Guide"
        Assignee = "vaishnavibodakhe"
        Labels = "documentation,member-4"
        Body = @"
### Overview
Create technical specification for the SHA-256 tamper-evident hash chain, verifiable digital credentials, and the Early Warning System.

### Files to Touch
- ``docs/SECURITY_AUDIT.md``
- ``README.md`` (Security, Audit & AI Analytics sections)

### Acceptance Criteria Checklist
- [ ] Document the mathematical hashing specification for audit ledger blocks.
- [ ] Detail public credential verification flow with QR codes.
- [ ] Document Early Warning ML heuristic weights and explainability thresholds.
"@
    }
)

Write-Host "`n>>> Creating 24 GitHub Issues on $Repo..." -ForegroundColor Cyan
$count = 0
$failedAssignees = @{}

foreach ($item in $issues) {
    $count++
    Write-Host "Creating Issue $count/24: $($item.Title)..." -ForegroundColor White
    
    # Try creating with assignee
    $res = gh issue create --repo $Repo --title $item.Title --body $item.Body --label $item.Labels --milestone $MilestoneTitle --assignee $item.Assignee 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] Created & Assigned to $($item.Assignee): $res" -ForegroundColor Green
    } else {
        # Check if error is due to assignee permission
        if ($res -match "not found" -or $res -match "assignee" -or $res -match "collaborator") {
            Write-Host "  [WARN] Assignee '$($item.Assignee)' not a collaborator or assignment failed. Creating issue without assignee..." -ForegroundColor Yellow
            $failedAssignees[$item.Assignee] = $true
            $res2 = gh issue create --repo $Repo --title $item.Title --body $item.Body --label $item.Labels --milestone $MilestoneTitle 2>&1
            if ($LASTEXITCODE -eq 0) {
                Write-Host "  [OK] Created unassigned: $res2" -ForegroundColor Green
            } else {
                Write-Host "  [ERROR] Failed to create issue: $res2" -ForegroundColor Red
            }
        } else {
            Write-Host "  [ERROR] Failed to create issue: $res" -ForegroundColor Red
        }
    }
}

Write-Host "`n=======================================================" -ForegroundColor Cyan
Write-Host " Finished creating issues!" -ForegroundColor Green
if ($failedAssignees.Count -gt 0) {
    Write-Host " Note: The following assignees could not be automatically assigned (invite as repo collaborators on GitHub):" -ForegroundColor Yellow
    foreach ($k in $failedAssignees.Keys) {
        Write-Host "   - @$k" -ForegroundColor Yellow
    }
}
Write-Host "=======================================================`n" -ForegroundColor Cyan
