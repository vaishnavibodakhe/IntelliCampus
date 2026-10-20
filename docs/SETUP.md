# 🚀 IntelliCampus New-Laptop Setup Guide

This guide walks you through setting up and running **IntelliCampus** on a fresh development machine, including the full authentication lifecycle (Admin / HOD registration, login, and email OTP forgot-password recovery).

---

## 1. Prerequisites

Ensure your system has the following installed:

- **Java JDK 17+** (Eclipse Temurin, OpenJDK, or Oracle JDK 17). Verify with:
  ```bash
  java -version
  ```
- **Node.js 18+ or 20+ (LTS)** and **npm**. Verify with:
  ```bash
  node -v
  npm -v
  ```
- **MySQL Server 8.0+** running locally on port `3306`. Verify with:
  ```bash
  mysql --version
  ```
- **Git**

---

## 2. MySQL Database Setup

1. Open your MySQL client (Command Line, MySQL Workbench, or DBeaver) connected as `root`:
   ```bash
   mysql -u root -p
   ```
2. Execute the following SQL commands to create the database and application user:
   ```sql
   -- Create database with utf8mb4 encoding
   CREATE DATABASE IF NOT EXISTS intellicampus_db 
       CHARACTER SET utf8mb4 
       COLLATE utf8mb4_unicode_ci;

   -- Create application user 'campus_admin'
   CREATE USER IF NOT EXISTS 'campus_admin'@'localhost' 
       IDENTIFIED BY 'SecureCampusPassword2026!';

   -- Grant permissions to intellicampus_db
   GRANT ALL PRIVILEGES ON intellicampus_db.* TO 'campus_admin'@'localhost';
   FLUSH PRIVILEGES;
   ```
   *(Note: If you use a custom MySQL port or different user credentials, adjust them in `.env` accordingly).*

---

## 3. Environment Configuration (`.env`)

1. Copy `.env.example` in the project root to create your local `.env`:
   ```bash
   # Windows (PowerShell or Command Prompt):
   copy .env.example .env

   # macOS / Linux:
   cp .env.example .env
   ```
2. Open `.env` and fill in your Gmail SMTP credentials:
   - `MAIL_USERNAME`: Your Gmail address (e.g. `yourname@gmail.com`).
   - `MAIL_PASSWORD`: A **16-letter Gmail App Password** (no spaces).

### How to generate a Gmail App Password:
1. Log into your Google Account: [myaccount.google.com](https://myaccount.google.com).
2. Go to **Security** in the left sidebar.
3. Under *How you sign in to Google*, ensure **2-Step Verification** is turned **ON**.
4. Search for or select **App passwords** (or go to [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)).
5. Name the app password (e.g., `IntelliCampus`) and click **Create**.
6. Google will generate a 16-character code (formatted like `xxxx xxxx xxxx xxxx`).
7. Copy the code into `.env` **without spaces**:
   ```ini
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=yourname@gmail.com
   MAIL_PASSWORD=xxxxxxxxxxxxxxxx
   ```
> [!IMPORTANT]
> Never commit `.env` to Git. `.env` is listed in `.gitignore` to prevent leaking credentials.

---

## 4. Start Backend Server

1. Open a terminal and navigate to the `backend/` folder:
   ```bash
   cd backend
   ```
2. Run the Spring Boot application using the bundled Maven wrapper:
   ```powershell
   # Windows:
   .\mvnw.cmd spring-boot:run

   # macOS / Linux:
   ./mvnw spring-boot:run
   ```
3. Look for the following lines in the startup log to confirm proper initialization:
   ```
   [DOTENV] Successfully loaded 5 properties from .env
   ...
   SMTP auth check: OK
   ...
   Tomcat started on port 8080 (http) with context path ''
   ```

---

## 5. Start Frontend Application

1. Open a separate terminal window in the project root:
   ```bash
   # Install dependencies
   npm install

   # Start Vite development server
   npm run dev
   ```
2. The frontend runs at:
   ```
   http://localhost:5173
   ```
   *(Vite is configured to automatically proxy `/api` calls to `http://localhost:8080`).*

---

## 6. How to Test Admin/HOD Register, Login & Forgot-Password OTP

1. Open your browser to `http://localhost:5173`.
2. Click on **HOD** or **Admin** portal.
3. Switch to the **Register** tab and sign up with your real email address (e.g., your Gmail).
4. Verify you can successfully log in with your new account credentials.
5. Log out, then on the login form click **"Forgot Password?"** (or navigate to `/forgot-password`).
6. Enter your registered email address and submit the OTP request.
7. Check your email inbox (including the **Spam** folder) for the 6-digit OTP code.
8. Enter the 6-digit OTP along with your new password to complete the reset.
9. Log in with your new password to verify recovery succeeded.

---

## 7. Troubleshooting

| Issue / Error | Cause | Resolution |
| :--- | :--- | :--- |
| **502 Bad Gateway** (`Failed to request OTP (502)`) | Backend is not running on port 8080. | Open a terminal, `cd backend`, and run `.\mvnw.cmd spring-boot:run`. Wait for `Tomcat started on port 8080`. |
| **535 Authentication Failed** (`SMTP auth check: FAILED (535)`) | Invalid Gmail App Password or 2-Step Verification disabled. | Re-generate a fresh 16-character App Password at `myaccount.google.com/apppasswords`. Remove all spaces and verify 2-Step Verification is active. |
| **"Success message shown, but no email arrived"** | Account not registered under that specific role (`ADMIN` vs `HOD`), or email routed to Spam. | 1. Check your email's **Spam / Junk** folder.<br>2. Confirm the account was registered with the exact role you are recovering.<br>3. Check backend log for `user not found` or `role mismatch`. |
| **MySQL 1045 / Connection Refused** | MySQL service stopped or credentials mismatch. | Ensure MySQL service is running and `DB_USER` / `DB_PASSWORD` in `.env` match your local MySQL configuration. |
