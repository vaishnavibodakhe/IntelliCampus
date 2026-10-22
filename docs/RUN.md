# Local Setup and Run Guide

This guide helps you set up and run IntelliCampus on a fresh machine.

## Prerequisites

Make sure the following tools are installed:
- **Node.js**: v18+ or v20+ (LTS) and npm (frontend uses React 19 and Vite 8)
- **Java**: JDK 17 (project uses Java 17 in `pom.xml`)
- **Docker & Docker Compose**: v2.0+

---

## Service Ports

| Service | Host Port | Source / Config | Notes |
|---|---|---|---|
| **Frontend Dev Server** | `5173` | `vite.config.js` | React application with `/api` proxy |
| **Backend API** | `8080` | `application.yml` | Spring Boot REST service |
| **MySQL Database** | `3307` | `docker-compose.yml` (`DB_PORT_EXTERNAL`) | Container port 3306 published to host port 3307 |
| **Redis Cache** | `6379` | `docker-compose.yml` | Container running; starter in `pom.xml`, currently unused by backend application code |
| **AI Biometric Service** | `8000` | `docker-compose.yml` | Face recognition and anti-spoofing microservice |

---

## Quick Start Steps

### Step 1: Create and Configure Root `.env` (REQUIRED)

A root `.env` file **IS REQUIRED** before launching services:

1. Copy `.env.example` to create your local `.env`:
   ```bash
   # Windows (PowerShell):
   copy .env.example .env

   # Linux / macOS:
   cp .env.example .env
   ```
2. Open `.env` and configure your credentials:
   - Set `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` (use any passwords of your choice for a fresh local setup).
   - Verify `DB_PORT=3307` and `DB_HOST=localhost` (required when connecting to MySQL via Docker Compose).
   - **CRITICAL**: Create and configure `.env` **BEFORE** running `docker compose up -d db redis ai`, as MySQL initializes the database user and volume credentials on first boot.

> [!WARNING]
> **Stale Docker Volume Warning**: Changing `DB_PASSWORD` in `.env` later will **NOT** change the password of an existing Docker volume. If credentials get out of sync, either continue using the original password in `.env`, or reset the volume with `docker compose down -v` (which **DELETES ALL LOCAL DATA**).

> [!NOTE]
> **Gmail SMTP / Password Reset OTP**: To configure the Admin / HOD forgot-password flow, see [docs/SETUP.md](file:///c:/Users/HP/Videos/IntelliCampus/docs/SETUP.md) for instructions on creating a 16-character Gmail App Password. Note that `SETUP.md` references a standalone MySQL server installed on host port `3306`, whereas this guide uses the containerized MySQL running on port `3307`.

### Step 2: Start Infrastructure in Docker
Start MySQL, Redis, and the AI microservice in background mode:

```bash
docker compose up -d db redis ai
```

Verify that all three containers are healthy and running:
```bash
docker compose ps
```

### Step 3: Start the Backend (Host)
Run the Spring Boot application on your host machine. Flyway applies all database migrations automatically on startup:

**Linux / macOS:**
```bash
cd backend
./mvnw spring-boot:run
```

**Windows (PowerShell / Command Prompt):**
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

### Step 4: Start the Frontend (Host)
In a new terminal window at the project root, install dependencies and start the Vite dev server:

```bash
npm install
npm run dev
```

Open your browser at `http://localhost:5173`. Vite will forward `/api` requests to `http://localhost:8080`.

---

## Dev-Only Faculty Quick Login (`.env.local`)

In development mode, the faculty live-capture interface includes a quick-login button for testing.

To enable it, create a `.env.local` file in the project root:

```ini
# .env.local (in project root)
VITE_DEMO_FACULTY_EMAIL=your_faculty_email@intellicampus.edu
VITE_DEMO_FACULTY_PASSWORD=your_faculty_password_here
```

- Ask your **team lead** for the seeded faculty credentials.
- Do not commit any real credentials to Git. The `.env.local` file is already listed in `.gitignore`.

---

## Troubleshooting

### 1. "Access denied for user 'campus_admin'@'...'"
- **Wrong DB Port**: Check that your backend connects to port `3307` on the host, not `3306`. (Port `3306` on your host might be an existing local MySQL instance with different credentials).
- **Stale Docker Volume**: If you previously started MySQL with an older container or different password, reset the database using the reset instructions below.

### 2. "Port 8080 already in use"
Another process is already using port 8080 (for example, a previously running backend instance).
- Stop the old process or find and terminate the process holding port 8080.
- On Windows: `netstat -ano | findstr :8080` followed by `taskkill /PID <PID> /F`.
- On Linux/macOS: `lsof -i :8080` followed by `kill -9 <PID>`.

### 3. How to Run Tests

**Frontend Tests:**
```bash
npm test
```

**Backend Tests:**
```bash
# Linux / macOS
cd backend
./mvnw test

# Windows
cd backend
.\mvnw.cmd test
```

---

## Reset database (DELETES ALL LOCAL DATA)

Use this command when pulling new migrations or if you want a clean database state:

1. Stop the backend process (`Ctrl + C`).
2. Remove the MySQL Docker volume:
   ```bash
   docker compose down -v
   ```
3. Restart the Docker services:
   ```bash
   docker compose up -d db redis ai
   ```
4. Start the backend again (`./mvnw spring-boot:run` or `.\mvnw.cmd spring-boot:run`). Flyway will recreate the schema and apply all migrations automatically.
