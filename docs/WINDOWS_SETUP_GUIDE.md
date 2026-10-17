# IntelliCampus: Windows Setup & Testing Guide

This comprehensive guide covers installing prerequisites, initializing the MySQL database, configuring environment variables in PowerShell and `.env`, setting up Gmail App Password for SMTP password resets, running the Spring Boot backend and React/Vite frontend, and verifying all authentication flows.

---

## 1. Prerequisites Installation (Windows)

### A. Java Development Kit (JDK 17+)
1. Download JDK 17 or JDK 21 from [Eclipse Adoptium (Temurin)](https://adoptium.net/) or Oracle.
2. Run the installer and ensure **"Set JAVA_HOME variable"** is checked.
3. Verify in PowerShell:
   ```powershell
   java -version
   javac -version
   ```

### B. Node.js & npm (v18+)
1. Download the LTS installer from [nodejs.org](https://nodejs.org/).
2. Run installer and verify in PowerShell:
   ```powershell
   node -v
   npm -v
   ```

### C. MySQL 8.0+ Community Server
1. Download and install [MySQL Community Server](https://dev.mysql.com/downloads/mysql/).
2. Keep default port `3306`.
3. Set root password or create the campus administrator user.

---

## 2. MySQL Database Setup

Open **MySQL Workbench** or MySQL Command Line Client, and run:

```sql
CREATE DATABASE IF NOT EXISTS intellicampus_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Optional: Create dedicated service user if not using root
CREATE USER IF NOT EXISTS 'campus_admin'@'localhost' IDENTIFIED BY 'SecureCampusPassword2026!';
GRANT ALL PRIVILEGES ON intellicampus_db.* TO 'campus_admin'@'localhost';
FLUSH PRIVILEGES;
```

> **Note on Migrations**: Flyway will automatically execute migrations (`V1`, `V2`, and `V3__auth_faculty_mentor.sql`) when the Spring Boot backend boots.  
> Alternatively, you can run the standalone schema script manually:
> ```powershell
> mysql -u campus_admin -p intellicampus_db < backend/sql/auth_faculty_mentor_schema.sql
> ```

---

## 3. Email (SMTP) Configuration with Gmail App Password

For the Forgot/Reset Password feature to dispatch real emails:

1. Log into your Google Account and go to [Google Account Security](https://myaccount.google.com/security).
2. Enable **2-Step Verification** (if not already enabled).
3. Search for **"App Passwords"** in the top search bar (or go to [https://myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)).
4. Enter an App name, e.g., `IntelliCampus`.
5. Click **Create**. Google will display a 16-character code (e.g., `abcd efgh ijkl mnop`).
6. Copy this password (without spaces) and use it for `SMTP_PASSWORD`.

---

## 4. Environment Variables Setup

### Option A: Via `.env` file (Root directory)
Copy `.env.example` to `.env`:
```powershell
Copy-Item .env.example .env
```
Populate `.env` with your credentials:
```ini
DB_HOST=localhost
DB_PORT=3306
DB_NAME=intellicampus_db
DB_USER=campus_admin
DB_PASSWORD=SecureCampusPassword2026!
JWT_SECRET=4c8a91f2b6e3d7a8c1f4e5a9b2d8c3e7f1a5b9c2d8e4f1a7b3c9d5e2f8a4b6c1
FRONTEND_URL=http://localhost:5173
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your_email@gmail.com
SMTP_PASSWORD=your_16_digit_app_password
MAIL_FROM=noreply@intellicampus.edu
```

### Option B: PowerShell Environment Variables (Active Session)
```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="intellicampus_db"
$env:DB_USER="campus_admin"
$env:DB_PASSWORD="SecureCampusPassword2026!"
$env:JWT_SECRET="4c8a91f2b6e3d7a8c1f4e5a9b2d8c3e7f1a5b9c2d8e4f1a7b3c9d5e2f8a4b6c1"
$env:SMTP_HOST="smtp.gmail.com"
$env:SMTP_PORT="587"
$env:SMTP_USERNAME="your_email@gmail.com"
$env:SMTP_PASSWORD="your_16_digit_app_password"
$env:MAIL_FROM="noreply@intellicampus.edu"
$env:FRONTEND_URL="http://localhost:5173"
```

---

## 5. Running the Application

### A. Run Spring Boot Backend
Open a terminal in `backend/`:
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
*(Or if `mvn` is installed globally: `mvn spring-boot:run`)*  
Backend runs at: **`http://localhost:8080`**  
OpenAPI / Swagger documentation: **`http://localhost:8080/swagger-ui.html`**

### B. Run React + Vite Frontend
Open another terminal in the project root:
```powershell
npm install
npm run dev
```
Frontend runs at: **`http://localhost:5173`**

---

## 6. Endpoints & URLs to Test

| Feature | URL Path | Method | Description |
| :--- | :--- | :---: | :--- |
| **Faculty Login** | `http://localhost:5173/faculty/login` | GET (UI) | Form with Email + Password + Remember Me + Forgot Password link |
| **Faculty Registration** | `http://localhost:5173/faculty/register` | GET (UI) | Registration with name, facultyId, email, department, phone, password |
| **Mentor Login** | `http://localhost:5173/mentor/login` | GET (UI) | Form with Mentor Email + Password |
| **Mentor Registration** | `http://localhost:5173/mentor/register` | GET (UI) | Registration with name, mentorId, email, department, phone, password |
| **Forgot Password** | `http://localhost:5173/forgot-password` | GET (UI) | Request reset link by email; always returns neutral success |
| **Reset Password** | `http://localhost:5173/reset-password?token=...` | GET (UI) | Validates token, sets new password, checks expiry |
| **Faculty Dashboard** | `http://localhost:5173/faculty/dashboard` | GET (UI) | Protected view; requires ROLE_FACULTY |
| **Mentor Dashboard** | `http://localhost:5173/mentor/dashboard` | GET (UI) | Protected view; requires ROLE_MENTOR |

### REST API Endpoints:
- `POST /api/auth/faculty/register`
- `POST /api/auth/mentor/register`
- `POST /api/auth/faculty/login`
- `POST /api/auth/mentor/login`
- `POST /api/auth/logout` (Authorization: `Bearer <token>`)
- `POST /api/auth/forgot-password`
- `GET /api/auth/validate-reset-token?token=...`
- `POST /api/auth/reset-password`

---

## 7. Verification Test Checklist

1. **Faculty Registration**:
   - Register with duplicate email -> returns HTTP 409 Conflict.
   - Register with duplicate Faculty ID -> returns HTTP 409 Conflict.
   - Register with weak password or mismatched passwords -> returns HTTP 400 Bad Request.
   - Register with valid credentials -> returns HTTP 201 Created and JWT token.
2. **Mentor Registration**:
   - Validates mentor ID uniqueness and saves mentor account.
3. **Login & Cross-Role Blocking**:
   - Faculty credentials entered on Mentor login -> rejected with "Invalid credentials".
   - Mentor credentials entered on Faculty login -> rejected with "Invalid credentials".
4. **Logout**:
   - Call logout -> token is revoked and blacklisted; subsequent requests return 401.
5. **Forgot & Reset Password**:
   - Registered and unregistered emails receive identical response ("If this email is registered...").
   - Reset link used once -> succeeds; re-attempting with the same link fails ("Already used").
   - Expired link (>15 minutes) -> rejected with HTTP 400.
