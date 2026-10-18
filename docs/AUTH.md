# IntelliCampus Authentication Architecture Specification

## Overview
IntelliCampus employs a role-based, cryptographically hardened authentication architecture dividing actors into:
1. **Students**: Self-service registration and instant JWT login (`/api/auth/register`, `/api/auth/login`).
2. **Staff (HOD & Admin)**: Controlled registration under `/api/auth/staff/register` requiring SuperAdmin approval before activation, secure credential rotation, audit chain traceability, and token lifecycle management.

---

## 1. Environment Secrets & Security Parameters
> [!IMPORTANT]
> **No hardcoded secrets exist in source code.** The backend will strictly fail to boot or log security warnings if mandatory secrets are omitted.

* `JWT_SECRET`: **Mandatory for the whole backend, including student and staff login.** Must be at least 32 characters long.
* `SUPERADMIN_PASSWORD`: Optional initial seed secret. If provided in the environment, the system automatically provisions `admin@intellicampus.edu` (`ADM-SUPER-001`) with `ROLE_ADMIN` and `ACTIVE` status (BCrypt strength 12). If omitted or blank, superadmin seeding is cleanly skipped and a warning is logged to the console:
  `SUPERADMIN_PASSWORD environment variable is missing; skipping default superadmin user seeding.`
* `APP_FRONTEND_URL`: Base URL for links generated in transactional emails (e.g., `http://localhost:5173`). Defaults to `http://localhost:5173`.

---

## 2. Password & Token Cryptography
* **BCrypt Password Hashing**: Strength factor 12 applied to all user passwords (`BCryptPasswordEncoder(12)`).
* **Reset Tokens**:
  * Generated with `SecureRandom` (32 bytes / 256 bits of entropy, hex-encoded).
  * Only the **SHA-256 hash** (`VARCHAR(64)`) is stored in `password_reset_tokens`.
  * **Validity Window**: Exactly 15 minutes.
  * **Single Use**: Once consumed, the token is flagged `used = true` and invalidated.
  * **Generic Response**: Requests for password reset return the identical generic message: `"If the email is registered, a password reset link has been sent."` regardless of whether the email exists.
* **Refresh Tokens**:
  * 32-byte SecureRandom tokens hashed with SHA-256 and stored in `refresh_tokens`.
  * Standard 7-day expiration with token rotation and revocation on logout or password change.

---

## 3. Account Lockout & Status Governance
* **Brute-Force Protection**: 5 consecutive failed login attempts trigger an immediate 15-minute account lockout (`UserStatus.LOCKED`).
* **Registration Approval Gate**:
  * New staff register with status `PENDING_APPROVAL`.
  * Login attempts by `PENDING_APPROVAL`, `REJECTED`, or `DISABLED` accounts are rejected with descriptive 403 Forbidden exceptions.
* **Role Verification**:
  * Endpoints `/api/auth/staff/pending`, `/api/auth/staff/{id}/approve`, and `/api/auth/staff/{id}/reject` are restricted exclusively to `ROLE_ADMIN`.

---

## 4. Rate Limiting
* **Password Reset Request Throttling**:
  * Enforced at maximum **3 requests per hour per email address** AND maximum **3 requests per hour per client IP address**.
  * Violations immediately return `429 Too Many Requests`.

---

## 5. Cryptographic Audit Trail
All security lifecycle events are immutably appended to the SHA-256 cryptographic hash chain via `AuditChainService`:
* `STAFF_REGISTERED`: Awaiting admin approval.
* `STAFF_APPROVED`: Approved by an administrator.
* `STAFF_REJECTED`: Rejected with optional justification.
* `LOGIN_SUCCESS`: Authenticated credentials.
* `LOGIN_FAILURE`: Invalid credential attempt (increments failed counter).
* `ACCOUNT_LOCKED`: 5 consecutive failures recorded.
* `PASSWORD_RESET_REQUESTED`: Reset link issued.
* `PASSWORD_RESET_COMPLETED`: Password updated via valid token.
* `PASSWORD_CHANGED`: Authenticated user updated password.

> [!CAUTION]
> **Zero Credential Exposure**: Passwords, hashes, and raw reset tokens are strictly forbidden from audit payloads. Only sanitized metadata is recorded.

---

## 6. Staff Endpoints Specification (`/api/auth/staff/*`)
| Method | Endpoint | Authorization | Description |
|---|---|---|---|
| `POST` | `/api/auth/staff/register` | Public | Registers HOD or Admin in `PENDING_APPROVAL` status |
| `POST` | `/api/auth/staff/login` | Public | Staff login with lockout and status verification |
| `POST` | `/api/auth/staff/refresh` | Public | Rotates refresh token and issues new access token |
| `POST` | `/api/auth/staff/logout` | Bearer Token / Optional Body | Revokes refresh tokens and logs out user |
| `POST` | `/api/auth/staff/forgot-password` | Public | Dispatches rate-limited reset link (generic response) |
| `POST` | `/api/auth/staff/reset-password` | Public | Resets password using single-use SHA-256 token |
| `POST` | `/api/auth/staff/change-password` | Authenticated | Updates password for authenticated user |
| `GET` | `/api/auth/staff/me` | Authenticated | Fetches authenticated staff profile |
| `GET` | `/api/auth/staff/pending` | `ROLE_ADMIN` | Lists staff registrations awaiting approval |
| `POST` | `/api/auth/staff/{id}/approve` | `ROLE_ADMIN` | Approves pending registration to `ACTIVE` |
| `POST` | `/api/auth/staff/{id}/reject` | `ROLE_ADMIN` | Rejects pending registration with reason |
