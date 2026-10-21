# IntelliCampus Security Architecture & Hardening Manual

This document provides a comprehensive operational and technical specification of the production-grade access control, authentication hardening, and authorization architecture implemented across IntelliCampus.

---

## 1. Security Model Overview

IntelliCampus enforces a zero-trust, default-deny security architecture governed by strict role boundaries, ownership-based checks, and cryptographic auditability.

### Primary Roles
- **ADMIN**: Centralized institutional administrator. Exactly **one** admin account exists across the entire system.
- **HOD (Head of Department)**: Manages academic courses, subjects, marks approvals, and departmental leave within their designated department.
- **FACULTY**: Manages assigned subject sessions, records drafts of marks, initiates mark corrections, and issues attendance tokens.
- **MENTOR**: Reviews leave requests for assigned mentees, monitors student academic progress, and evaluates early-warning risk scores.
- **STUDENT**: Self-registers, completes DPDP Act 2023 biometric consent, enrolls multi-pose facial features, applies for leaves, views published academic results, and verifies attendance.
- **PUBLIC**: Unauthenticated public verification for issued verifiable credentials (transcripts, degree certificates).

---

## 2. Single Admin Singleton Guarantee

1. **Database Singleton Constraint (`V14__single_admin_and_credential_cleanup.sql`)**:
   A virtual generated column and unique index guarantee at the database storage engine level that only one row can ever hold `role = 'ADMIN'`:
   ```sql
   ALTER TABLE users ADD COLUMN admin_singleton TINYINT GENERATED ALWAYS AS (IF(role = 'ADMIN', 1, NULL)) VIRTUAL;
   ALTER TABLE users ADD CONSTRAINT uk_users_single_admin UNIQUE (admin_singleton);
   ```
2. **Startup Seeder (`SuperAdminSeeder`)**:
   - Runs on application initialization.
   - Idempotently checks if an account with `role = 'ADMIN'` exists.
   - Provisions the admin if missing using `ADMIN_EMAIL` and `SUPERADMIN_PASSWORD` (minimum 12 characters required in non-dev profiles).
   - Sets `must_change_password = true` and `status = ACTIVE`.
3. **Application & Service Guards**:
   - Every API endpoint and service method (`UserService`, `AdminStaffService`, `StaffAuthService`, CSV import) explicitly blocks:
     - Creating any user with `role = 'ADMIN'`.
     - Updating any user's role to `ADMIN`.
     - Modifying, demoting, deactivating, or deleting the system admin.

---

## 3. Comprehensive Role-Based Access Control (RBAC) Matrix

All endpoints default to **DENY**. Access is granted explicitly via Spring Security filters and `@PreAuthorize` method annotations:

| Module / Endpoint Pattern | ADMIN | HOD | FACULTY | MENTOR | STUDENT | PUBLIC | Ownership / Scope Rules |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **Authentication & Public** | | | | | | | |
| `POST /api/auth/admin/login` | :white_check_mark: | :x: | :x: | :x: | :x: | :x: | ADMIN role only; other roles get generic 401 |
| `POST /api/auth/admin/verify-otp` | :white_check_mark: | :x: | :x: | :x: | :x: | :x: | Enabled when `ADMIN_OTP_ENABLED=true` |
| `POST /api/auth/staff/login` | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: | :x: | Staff login for HOD, Faculty, Mentor |
| `POST /api/auth/student/login` | :x: | :x: | :x: | :x: | :white_check_mark: | :x: | Requires `ACTIVE` status and face enrollment |
| `POST /api/auth/student/register` | :x: | :x: | :x: | :x: | :white_check_mark: | :x: | Public student registration (status: `PENDING`) |
| `GET /api/credentials/public/**` | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | Tamper-proof verification of credential hashes |
| **Staff Administration** | | | | | | | |
| `POST /api/admin/staff` | :white_check_mark: | :x: | :x: | :x: | :x: | :x: | Create HOD, Faculty, Mentor only (1 HOD/dept) |
| `GET /api/admin/staff` | :white_check_mark: | :x: | :x: | :x: | :x: | :x: | Filterable by role, department, status, query |
| `PUT /api/admin/staff/{id}` | :white_check_mark: | :x: | :x: | :x: | :x: | :x: | Update staff details |
| `PATCH /api/admin/staff/{id}/status` | :white_check_mark: | :x: | :x: | :x: | :x: | :x: | Toggle `ACTIVE` / `DISABLED`; revokes active tokens |
| `POST /api/admin/staff/{id}/reset-password`| :white_check_mark: | :x: | :x: | :x: | :x: | :x: | Generates 16-char temp pass, sets must-change |
| `DELETE /api/admin/staff/{id}` | :white_check_mark: | :x: | :x: | :x: | :x: | :x: | Soft delete (status: `DISABLED`, revokes tokens) |
| **User & Academic Management** | | | | | | | |
| `GET /api/users/**` | :white_check_mark: | :x: | :x: | :x: | :x: | :x: | User management & CSV batch import |
| `POST /api/academic/rooms` | :white_check_mark: | :x: | :x: | :x: | :x: | :x: | Setup geofenced classrooms |
| **Notices & Communication** | | | | | | | |
| `POST /api/notices` | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: | :x: | :x: | Admin (all), HOD (dept), Faculty (division) |
| `GET /api/notices/**` | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: | Authenticated feed filtered by target audience |
| **Attendance Workflows** | | | | | | | |
| `POST /api/attendance/sessions` | :white_check_mark: | :x: | :white_check_mark: | :x: | :x: | :x: | Faculty starts session for assigned subject |
| `POST /api/attendance/sessions/checkin`| :x: | :x: | :x: | :x: | :white_check_mark: | :x: | Enforces geofencing, challenge code, time window |
| `GET /api/attendance/sessions/records/**`| :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: | Students view own records; Faculty view session |
| **Academic Marks** | | | | | | | |
| `POST /api/marks` | :white_check_mark: | :x: | :white_check_mark: | :x: | :x: | Faculty draft marks for assigned subject |
| `POST /api/marks/corrections` | :white_check_mark: | :x: | :white_check_mark: | :x: | :x: | Faculty initiates mark correction request |
| `POST /api/marks/corrections/{id}/decide`| :white_check_mark: | :white_check_mark: | :x: | :x: | :x: | :x: | HOD decides corrections within own department |
| `GET /api/marks/student/{id}` | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: | Students see only `PUBLISHED` marks for self |
| **Leave Governance** | | | | | | | |
| `POST /api/leaves` | :x: | :x: | :x: | :x: | :white_check_mark: | :x: | Student submits leave for own profile |
| `PUT /api/leaves/{id}/mentor` | :white_check_mark: | :x: | :x: | :white_check_mark: | :x: | :x: | Mentor reviews only assigned mentees |
| `PUT /api/leaves/{id}/hod` | :white_check_mark: | :white_check_mark: | :x: | :x: | :x: | :x: | HOD grants final approval for departmental leaves |
| `GET /api/leaves/student/{id}` | :white_check_mark: | :white_check_mark: | :x: | :white_check_mark: | :white_check_mark: | :x: | Students view own leave history |
| **Audit & Credentials** | | | | | | | |
| `GET /api/audit/chain/**` | :white_check_mark: | :x: | :x: | :x: | :x: | :x: | View and verify SHA-256 cryptographic hash chain |
| `POST /api/credentials/revoke` | :white_check_mark: | :x: | :x: | :x: | :x: | :x: | Revoke verifiable credential with justification |

---

## 4. Account Lifecycle & Provisioning

### Staff Account Lifecycle
```
[Admin Creates Staff] 
       │ 
       ▼
 [Status: ACTIVE] ──► Generates 16-Char Temp Password ──► must_change_password = true
       │ 
       ▼
[First Login Gate] ──► Enforces POST /api/auth/staff/change-password
       │ 
       ▼ (Password changed)
[Normal Access] ──► must_change_password = false
       │
       ├──► Deactivated by Admin ──► [Status: DISABLED] (Tokens revoked immediately)
       └──► 5 Consecutive Failed Logins ──► [Status: LOCKED] (15-min lockout)
```

1. **Staff Provisioning**:
   - Public registration endpoints (`/api/auth/staff/register`, `/api/faculty/register`, `/api/mentor/register`) are completely disabled.
   - Staff accounts are provisioned exclusively by the Administrator via `POST /api/admin/staff`.
   - Generates a cryptographically strong 16-character temporary password containing uppercase, lowercase, numbers, and special symbols.
   - If SMTP is configured, sends credentials via email. If unconfigured, returns the temporary password once in the response payload. It is **never** logged to server logs.
2. **Must-Change-Password Gate**:
   - When `must_change_password = true`, `JwtAuthenticationFilter` intercepts all requests.
   - Access to any application endpoint other than `/change-password`, `/logout`, and `/refresh` results in HTTP 403 `PASSWORD_CHANGE_REQUIRED`.
   - The frontend automatically redirects users to the change-password modal.
3. **Session Revocation**:
   - Upon deactivation (`status = DISABLED`), password reset, or logout, tokens are persisted to `RevokedTokenRepository` and rejected immediately on subsequent requests.

---

## 5. Authentication Hardening Controls

1. **Lockout Policy**:
   - 5 consecutive failed login attempts automatically lock the account for 15 minutes (`locked_until`).
   - Recorded as `ACCOUNT_LOCKED` in the audit chain.
2. **Timing-Attack Mitigation**:
   - When an unrecognized email attempts authentication, the service executes a constant-time dummy BCrypt hash verification (`DUMMY_BCRYPT_HASH`).
   - Responses return generic `"Invalid email or password"` to prevent username enumeration.
3. **Password Complexity Policy**:
   - Minimum 10 characters for staff and administrators.
   - Requires uppercase, lowercase, digit, and special character (`[!@#$%&*+\-_=]`).
   - Disallows passwords that match or contain the user's name or email prefix.
4. **Security Headers**:
   - `Strict-Transport-Security: max-age=31536000; includeSubDomains`
   - `X-Content-Type-Options: nosniff`
   - `X-Frame-Options: DENY`
   - `Content-Security-Policy: default-src 'self'; ...`
5. **Information Leakage Prevention**:
   - Server exception stack traces suppressed via `server.error.include-stacktrace: never` and `include-exception: false`.

---

## 6. Admin Password Rotation Procedure

To rotate the system administrator's credentials:

### Method A: Via Web UI / API (Recommended)
1. Sign in as Admin at `/admin/login`.
2. Open account settings and select **Change Password** (or call `POST /api/auth/staff/change-password`).
3. Enter current password and new password adhering to the strong password policy (at least 10 characters with upper, lower, number, and special character).
4. All existing tokens are automatically revoked upon change.

### Method B: Emergency Credential Reset via Environment Variables
If the current administrator password is lost:
1. Stop the backend server instance.
2. Update `SUPERADMIN_PASSWORD` in `.env` to a new random secret of at least 12 characters.
3. Connect to MySQL and reset the admin row password hash or allow the seeder:
   ```sql
   UPDATE users 
   SET password_hash = '$2a$12$...new_bcrypt_hash...', 
       must_change_password = 1, 
       failed_attempts = 0, 
       locked_until = NULL, 
       status = 'ACTIVE' 
   WHERE role = 'ADMIN';
   ```
4. Restart the backend service.

---

## 7. Verification & Smoke Testing

To run the automated security smoke test suite:

```bash
# Ensure backend is running on localhost:8080
chmod +x scripts/security-smoke-test.sh
./scripts/security-smoke-test.sh
```

To run all backend unit, integration, and security tests:
```bash
cd backend
./mvnw test
```

To run frontend security and route tests:
```bash
npm test -- --run
npm run build
```
