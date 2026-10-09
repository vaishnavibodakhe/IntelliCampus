# 🎓 IntelliCampus: Intelligent College Platform

[![Spring Boot 3](https://img.shields.io/badge/Backend-Spring%20Boot%203.x-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg?logo=openjdk)](https://openjdk.org/)
[![Python 3.11](https://img.shields.io/badge/AI%20Engine-Python%203.11-blue.svg?logo=python)](https://python.org)
[![FastAPI](https://img.shields.io/badge/AI%20API-FastAPI-009688.svg?logo=fastapi)](https://fastapi.tiangolo.com/)
[![React 18](https://img.shields.io/badge/Frontend-React%2018-61DAFB.svg?logo=react)](https://react.dev/)
[![MySQL 8](https://img.shields.io/badge/Database-MySQL%208.0%2B-4479A1.svg?logo=mysql)](https://www.mysql.com/)
[![Redis 7](https://img.shields.io/badge/Cache-Redis%207-DC382D.svg?logo=redis)](https://redis.io/)
[![Docker Compose](https://img.shields.io/badge/Deployment-Docker%20Compose-2496ED.svg?logo=docker)](https://www.docker.com/)
[![DPDP Act 2023](https://img.shields.io/badge/Compliance-DPDP%20Act%202023-purple.svg)](#-privacy-ethics--dpdp-compliance)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 📌 Executive Summary

**IntelliCampus** is a secure, intelligent, and tamper-evident campus administration platform designed to modernize higher education institutional operations. While traditional college enterprise portals operate merely as passive relational record repositories, **IntelliCampus** closes critical operational gaps across **fraud-resistant attendance**, **tamper-evident academic grading**, **document-grounded AI support**, **predictive early-warning student interventions**, and **cryptographically verifiable credentials**.

The system is built as a robust **Spring Boot 3 (Java 21) modular monolith** combined with a high-performance **Python 3.11 (FastAPI) AI microservice**, backed by **MySQL 8.0 / 8.4**, **Redis 7**, and a responsive **React 18** client.

---

## 📑 Table of Contents

- [Key Motivations & Problem Statement](#-key-motivations--problem-statement)
- [System Architecture & Data Flow](#-system-architecture--data-flow)
- [Role-Based Access & Actor Matrix](#-role-based-access--actor-matrix)
- [Team Task Division (4 Collaborators)](docs/TEAM_TASK_DIVISION.md)
- [Core Functional Innovations](#-core-functional-innovations)
  - [1. Dual-Factor AI Attendance Validation Pipeline](#1-dual-factor-ai-attendance-validation-pipeline)
  - [2. Hash-Chained Tamper-Evident Marks & Audit Trail](#2-hash-chained-tamper-evident-marks--audit-trail)
  - [3. Multilingual Document-Grounded RAG Chatbot](#3-multilingual-document-grounded-rag-chatbot)
  - [4. Early Warning System (EWS) Machine Learning](#4-early-warning-system-ews-machine-learning)
  - [5. Publicly Verifiable Documents (Anti-Forgery)](#5-publicly-verifiable-documents-anti-forgery)
  - [6. Multi-Tier Leave Management System](#6-multi-tier-leave-management-system)
  - [7. Targeted Circulars & Push Notifications](#7-targeted-circulars--push-notifications)
- [Technology Stack Matrix](#-technology-stack-matrix)
- [Repository Directory Structure](#-repository-directory-structure)
- [Getting Started & Local Setup](#-getting-started--local-setup)
  - [Prerequisites](#prerequisites)
  - [Option A: One-Click Launch (Docker Compose)](#option-a-one-click-launch-docker-compose)
  - [Option B: Manual Development Environment](#option-b-manual-development-environment)
- [REST API Reference & Status Codes](#-rest-api-reference--status-codes)
- [Database Schema & Data Dictionary](#-database-schema--data-dictionary)
- [Privacy, Ethics & DPDP Compliance](#-privacy-ethics--dpdp-compliance)
- [Quality Benchmarks & Test Acceptance Plan](#-quality-benchmarks--test-acceptance-plan)
- [Literature References](#-literature-references)
- [License & Contributors](#-license--contributors)

---

## 💡 Key Motivations & Problem Statement

Conventional college administration workflows suffer from structural vulnerabilities:

| Challenge in Legacy Systems | Real-World Impact | IntelliCampus Solution |
| :--- | :--- | :--- |
| **Attendance Fraud & Proxies** | Roll-calls waste 10–15 mins/lecture; static QR codes and proxy buddies undermine academic integrity. | **Dual-Factor Attendance**: One-shot classroom photo analyzed by InsightFace + anti-spoofing; student confirms within 2 mins via device binding & classroom geofence. Dynamic rotating QR fallback (15s HMAC). |
| **Silent Mark Manipulations** | Internal marks altered in spreadsheets or relational databases with zero immutable history. | **Cryptographic Hash Chain**: Every grade change creates an insert-only SHA-256 block linking to the previous entry, with mandatory audit reason. |
| **Scattered Communications** | Important circulars posted randomly across physical boards, WhatsApp groups, and unstructured portals. | **Targeted Notices & Tracking**: Segmented notifications by department, year, and division with read-receipt audit and automated deadline alerts. |
| **Office Queues for Routine Queries** | Administrative staff repeatedly field identical routine queries about syllabus, exam fees, and deadlines. | **Document-Grounded RAG Chatbot**: Spring AI RAG system that answers strictly from verified PDFs with exact citations (English, Hindi, Marathi). |
| **Reactive Dropout Interventions** | Struggling students are noticed only when semester failure lists or detention notices appear. | **Weekly Early Warning System (EWS)**: Machine learning model (Random Forest / Logistic Regression) evaluates 6 indicators weekly, alerting mentors to top risk drivers. |
| **Certificate / Receipt Forgery** | Hard-copy fee receipts and paper bonafides are vulnerable to tampering and time-consuming manual validation. | **Public Verification**: PDF documents issued with cryptographically signed QR codes and hashes; instantly verifiable online without authentication. |
| **Unlinked Paper Leave Requests** | Physical leave slips lost in transit, resulting in unfair student attendance shortages. | **Integrated Multi-Step Leave Workflow**: Multi-tier approvals (Mentor &rarr; HOD) automatically recompute session denominators in attendance calculations. |

---

## 🏛️ System Architecture & Data Flow

IntelliCampus uses a **modular monolith** backend paired with a dedicated **Python AI service**, prioritizing high maintainability, low deployment complexity, and strict horizontal separation of concerns.

```
                           +------------------------------------------+
                           |           React 18 Web Client            |
                           |  (Admin / HOD / Faculty / Mentor / Stud) |
                           +--------------------+---------------------+
                                                |
                                    HTTPS REST / WSS STOMP
                                                |
                                                v
+----------------------------------------------------------------------------------------------------+
|                                    Spring Boot 3 Backend API                                       |
|                                                                                                    |
|  +--------------+  +--------------+  +---------------+  +--------------+  +---------------------+  |
|  |  auth & rbac |  | admin-mgmt   |  |  attendance   |  |  marks-audit |  | notices & notify    |  |
|  +--------------+  +--------------+  +---------------+  +--------------+  +---------------------+  |
|  +--------------+  +--------------+  +---------------+  +--------------+  +---------------------+  |
|  | leave-mgmt   |  | docs-verify  |  | chatbot (RAG) |  | risk-engine  |  | audit hash-chain    |  |
|  +--------------+  +--------------+  +---------------+  +--------------+  +---------------------+  |
+-------------------+-------------------------+---------------------------------+--------------------+
                    |                         |                                 |
           JDBC over TLS               Redis Protocol                   Internal REST API
                    |                         |                                 |
                    v                         v                                 v
        +-----------------------+   +-------------------+          +-------------------------+
        |     MySQL 8.0 DB      |   |      Redis 7      |          | Python 3.11 AI Service  |
        |  - Relational Schema  |   | - Dynamic QR HMAC |          | - InsightFace (512-dim) |
        |  - JSON Biometrics    |   | - Rate Limiters   |          | - Anti-Spoofing Scorer  |
        |  - JSON Document RAG  |   | - Session Caching |          | - Scikit-Learn EWS      |
        |  - Immutable Audit    |   +-------------------+          +-------------------------+
        +-----------------------+
```

---

## 👥 Role-Based Access & Actor Matrix

The system specifies **six distinct user classes** with strict role enforcement enforced at both the API filter level and method security annotations (`@PreAuthorize`):

```
                                  [ College Platform ]
                                           |
         +-----------------+---------------+----------------+----------------+
         |                 |               |                |                |
     [ Admin ]          [ HOD ]        [ Faculty ]      [ Mentor ]      [ Student ]      [ Public Verifier ]
         |                 |               |                |                |                   |
   User Management,   Department      Classroom        Mentee Groups,   Self Dashboard,    Zero Login,
   Academic Setup,    Reports,        Attendance,      Risk Alerts &    2-Min Attendance   QR & Code
   Circulars,         Leave Final,    Marks Entry &    Actions,         Confirm, Disputing, Verifier
   Document Issue     Mark Correction Corrections      Leave 1st Step   Chatbot, Leave
```

| Role | Administrative Scope | Primary Functional Permissions |
| :--- | :--- | :--- |
| **Admin** | Campus-Wide System & Setup | User account provisioning & bulk CSV import; Academic layout (Departments, Divisions, Subjects, Classrooms with GPS coords); Circular publishing; Document issuance & revocation; Knowledge base indexing. |
| **HOD** | Department Head | Review & approve mark correction requests; Final authorization on leave requests (>3 days); Inspect departmental attendance/defaulter/audit analytics. |
| **Faculty** | Subject Teacher | Open attendance sessions; Live camera class snapshot capture; Resolve student attendance disputes; Enter internal marks; Publish class-specific notices. |
| **Mentor** | Assigned Student Cohort | Monitor assigned mentee progress; Receive automated EWS High-Risk alerts with top feature contributions; Log intervention notes; First-level leave approval. |
| **Student** | Personal Cohort Member | 2-minute attendance confirmation; Rotating QR scanning fallback; View subject-wise attendance & raise disputes; View marks history; Submit leave; Ask RAG chatbot; Download verified receipts. |
| **Public Verifier** | Unauthenticated External | Zero login required. Verify validity, issuance date, and masked student identity of certificates and receipts via code or QR scanner. |

---

## 🔬 Core Functional Innovations

### 1. Dual-Factor AI Attendance Validation Pipeline

To completely eliminate proxy attendance while finishing class marking in **under 2 minutes**, IntelliCampus applies a strict multi-layer sequential validation pipeline:

```
[Faculty Starts Session] ---> [Challenge Code Displayed] ---> [Live In-App Camera Only]
                                                                      |
  +-------------------------------------------------------------------+
  |
  +---> [1. Time Window Check]       : Must be within 10 minutes of session launch.
  +---> [2. Geofence Radius Check]   : Haversine distance between phone & room GPS <= radius (default 50m).
  +---> [3. Anti-Replay SHA-256]     : File hash verified against past sessions (blocks re-upload).
  +---> [4. Perceptual Hash (pHash)] : 64-bit DCT pHash Hamming distance > 8 (blocks cropped/resized photos).
  +---> [5. InsightFace Extraction]  : Multi-face 512-dim normalized embedding extraction.
  +---> [6. Spoof Detection]        : Anti-spoofing CNN scores face liveness; < 0.60 sent to Review.
  +---> [7. Cosine Search in Vector] : Matches division embeddings via cosine similarity (similarity >= 0.50, margin >= 0.05).
  +---> [8. WebSocket Push to Mentees]: Matched students receive push notification on `/user/queue/attendance`.
  +---> [9. 2-Minute Confirmation]   : Student confirms from approved device fingerprint inside classroom geofence.
  +---> [10. Dynamic QR Fallback]    : Unmatched students scan 15-second rotating HMAC-SHA256 teacher QR code.
  +---> [11. Finalize & Flags]       : Teacher reviews 4 segmented lists and locks session into immutable audit log.
```

#### Attendance Status Lifecycle

```
             +------------+
             |  PENDING   |
             +-----+------+
                   |
         +---------+---------+
         |                   |
         v                   v
   +-----------+       +------------+
   |  MATCHED  |       | UNMATCHED  |
   +-----+-----+       +-----+------+
         |                   |
    +----+----+         +----+----+
    |         |         |         |
    v         v         v         v
+-------+ +---------+ +--------+ +--------+
|PRESENT| |NOT CONF | | REVIEW | | ABSENT |
+-------+ +---------+ +--------+ +--------+
```

- **PRESENT**: Face recognized above threshold **AND** confirmed on registered device inside classroom geofence within 120 seconds.
- **PRESENT_QR**: Validated via high-frequency rotating dynamic QR code inside classroom geofence.
- **REVIEW**: Low facial match confidence, spoof score `< 0.60`, multiple faces mapped to single ID, or dual confirmations from single device fingerprint.
- **ABSENT**: Unrecognized, unconfirmed, or manually marked absent.

---

### 2. Hash-Chained Tamper-Evident Marks & Audit Trail

All administrative modifications—especially internal exam marks, attendance overrides, and credential revocations—are written to an append-only, tamper-evident hash chain inspired by blockchain blocks:

$$\text{entry\_hash} = \text{SHA-256}(\text{prev\_hash} + \text{actor\_id} + \text{action} + \text{entity\_type} + \text{entity\_id} + \text{old\_val} + \text{new\_val} + \text{reason} + \text{created\_at})$$

- **Genesis Block**: Seeded with a deterministic constant hash.
- **Integrity Validation Engine**: Administrators can execute `/api/audit/verify` at any time. The engine recalculates sequential block digests across 10,000+ entries in under 30 seconds, immediately highlighting the exact broken row index if database records were modified directly via SQL.
- **Mandatory Justification**: Any manual status or mark modification requires a descriptive reason string of at least 10 characters.

---

### 3. Multilingual Document-Grounded RAG Chatbot

The IntelliCampus knowledge engine replaces noisy, uncurated chat channels with a hallucination-free, retrieval-augmented system:
- **PDF Ingestion & Chunking**: Institutional PDFs (circulars, syllabi, fee structures, ordinances) are parsed into 500-token passages with a 50-token sliding overlap.
- **Vector Search**: Passages are vectorized and indexed for cosine similarity retrieval.
- **Grounded Generation**: Questions are embedded, retrieving the top 5 cosine-similar chunks. LLM queries run with `temperature = 0.0` with explicit system prompt restrictions:
  > *"You are the IntelliCampus official assistant. Answer ONLY using the supplied context excerpts. Always cite Document Title and Page Number. If the context does not contain the answer, politely refuse and instruct the student to contact the academic registrar."*
- **Multilingual Support**: Supports queries and responses in **English, Hindi, and Marathi**.

---

### 4. Early Warning System (EWS) Machine Learning

A scheduled background job analyzes student academic telemetry weekly to flag early indicators of student attrition:
- **Feature Vector**:
  1. Cumulative attendance percentage
  2. 4-week delta in attendance rate ($\Delta \text{Attn}$)
  3. Internal assessment average percentage
  4. Test score trend relative to previous term ($\Delta \text{Marks}$)
  5. Active academic backlog count
  6. Cumulative approved/unapproved leave days in the prior 30 days
- **Algorithms**: **Random Forest Classifier** alongside an explainable **Logistic Regression Baseline** trained on student academic benchmarks.
- **Risk Tiers**:
  - **Low Risk** ($< 0.33$)
  - **Medium Risk** ($0.33 - 0.66$)
  - **High Risk** ($> 0.66$): Automatically dispatches an alert to the designated mentor displaying the top 3 contributing risk factors. Mentors must document intervention action notes.

---

### 5. Publicly Verifiable Documents (Anti-Forgery)

- **Tamper-Evident Issuance**: Generates official fee receipts, bonafide certificates, and grade transcripts as signed PDFs containing a unique document UUID, SHA-256 digest, and high-density QR code.
- **Zero-Login Verification Page**: Third parties (employers, banks, universities) scan the QR or submit the code at `/verify`.
- **Privacy Masking**: The public verification endpoint confirms document legitimacy (`VALID`, `REVOKED`, or `NOT_FOUND`) while displaying masked student information (e.g., `J*** D**`, Roll `CS***42`) to safeguard student privacy.

---

### 6. Multi-Tier Leave Management System

- **Digital Submissions**: Students submit leave requests specifying dates, category, justification, and optional documentary evidence (up to 5 MB).
- **Two-Stage Approval Chain**:
  - Leaves $\le 3$ days: Handled directly by student's designated Faculty Mentor.
  - Leaves $> 3$ days: Evaluated by Faculty Mentor, then forwarded to Head of Department (HOD) for final approval.
- **Automated Attendance Normalization**: Approved leave dates are automatically credited into attendance calculations, ensuring students are not unfairly designated as attendance defaulters.

---

### 7. Targeted Circulars & Push Notifications

- **Granular Audience Targeting**: Notices can be dispatched to specific scopes: College-wide, Department, Academic Year, or specific Division.
- **Read Receipt Auditing**: Faculty and administration can monitor real-time read statistics (percentage and student list).
- **Automated Deadline Watchdog**: Scheduled task dispatches push alerts 24 hours prior to notice submission deadlines to all students who haven't acknowledged the notice.

---

## 🛠️ Technology Stack Matrix

```
+----------------------------------------------------------------------------------------+
|                                    TECHNOLOGY STACK                                    |
+---------------------+-------------------------------+----------------------------------+
| Layer               | Technology                    | Purpose                          |
+---------------------+-------------------------------+----------------------------------+
| Backend Runtime     | Java 21 LTS (OpenJDK)         | Core business logic monolith     |
| Backend Framework   | Spring Boot 3.2.x             | REST APIs, Dependency Injection  |
| Security & Auth     | Spring Security 6, JWT, BCrypt| RBAC, Access/Refresh tokens      |
| Real-time Messaging | Spring WebSocket + STOMP      | Push attendance & alerts         |
| AI Microservice     | Python 3.11, FastAPI, Uvicorn | Face recognition & ML inference  |
| Biometrics / Vision | InsightFace (ArcFace), OpenCV | 512-dim embedding & anti-spoof   |
| Machine Learning    | Scikit-Learn, NumPy, Pandas   | Early Warning Risk Classifier    |
| Primary Database    | MySQL 8.0 / 8.4 LTS           | Relational data & JSON storage   |
| In-Memory Cache     | Redis 7 Alpine                | OTPs, dynamic QR, rate limits    |
| AI Orchestration    | Spring AI (RAG Engine)        | Vector search & LLM prompt chain |
| Frontend Library    | React 18, Vite / CRA          | Single-page web application      |
| Styling & UI        | Tailwind CSS / Lucide Icons   | Responsive mobile-first UI       |
| Hardware Scanner    | html5-qrcode, qrcode.react    | In-app QR generation & camera    |
| Containerization    | Docker, Docker Compose        | Multi-container orchestration    |
| API Documentation   | OpenAPI 3.0 / Springdoc       | Interactive Swagger UI reference |
+---------------------+-------------------------------+----------------------------------+
```

---

## 📂 Repository Directory Structure

```text
IntelliCampus/
├── .github/
│   └── workflows/              # CI/CD automated build and test pipelines
├── ai-service/                 # Python 3.11 FastAPI microservice
│   ├── app/
│   │   ├── api/                # FastAPI router endpoints (/ai/analyze, /ai/risk)
│   │   ├── core/               # App configuration and ML settings
│   │   ├── models/             # InsightFace loader, anti-spoofing net, EWS classifier
│   │   └── services/           # Face extraction, vector normalization, inference
│   ├── Dockerfile              # Container definition for AI engine
│   ├── requirements.txt        # Python dependency manifest
│   └── README.md               # AI service architecture and setup guide
├── backend/                    # Spring Boot 3 modular monolith
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/intellicampus/
│   │   │   │   ├── admin/      # Academic departments, courses, room geofencing
│   │   │   │   ├── attendance/ # Photo pipeline, geofence, 2-min confirm, QR HMAC
│   │   │   │   ├── audit/      # SHA-256 hash-chained immutable audit log
│   │   │   │   ├── auth/       # JWT token management, BCrypt, account lockout
│   │   │   │   ├── chatbot/    # Spring AI document chunking and RAG engine
│   │   │   │   ├── documents/  # Tamper-evident PDF generation & public verification
│   │   │   │   ├── leave/      # Multi-tier leave requests & attendance adjustment
│   │   │   │   ├── marks/      # Internal marks entry and HOD correction approvals
│   │   │   │   ├── notify/     # WebSocket STOMP broadcasting and email alerts
│   │   │   │   └── risk/       # Scheduled EWS pipeline & mentor actions
│   │   │   └── resources/
│   │   │       ├── db/migration/ # Flyway SQL migrations (MySQL schema, tables)
│   │   │       └── application.yml # Backend application configurations
│   ├── Dockerfile              # Multi-stage container build for Java 21
│   ├── pom.xml                 # Maven build dependencies
│   └── README.md               # Backend module documentation & API specs
├── frontend/                   # React 18 single page application
│   ├── public/                 # Static assets, manifests, icons
│   ├── src/
│   │   ├── components/         # Reusable UI controls, modals, tables, badges
│   │   ├── contexts/           # Authentication, Theme, WebSocket providers
│   │   ├── hooks/              # Geolocation, camera capture, WebSocket hooks
│   │   ├── pages/              # Role dashboards (Admin, HOD, Faculty, Mentor, Student)
│   │   └── services/           # Axios API client, STOMP client
│   ├── package.json            # Node.js dependencies
│   ├── Dockerfile              # Nginx production build container
│   └── README.md               # Frontend setup, routing, and screens catalog
├── docs/                       # Project engineering specifications
│   ├── ARCHITECTURE.md         # Full technical architecture and system design
│   ├── WORKFLOWS.md            # Role-wise flowcharts & state machines
│   ├── API_SPECIFICATION.md    # REST API endpoints, schemas, and curl examples
│   ├── DATABASE_DESIGN.md      # Data dictionary, ER diagram, and MySQL schema
│   └── TEAM_TASK_DIVISION.md   # 4-Collaborator role ownership & sprint milestones
├── docker-compose.yml          # Production multi-service orchestration
├── .gitignore                  # Git tracking exclusion rules
├── LICENSE                     # MIT License
└── README.md                   # Master repository documentation (this file)
```

---

## 🚀 Getting Started & Local Setup

### Prerequisites

Ensure the following tools are installed on your workstation:
- **Docker Engine** (v24.0+) & **Docker Compose** (v2.20+)
- **Java Development Kit (JDK 21 LTS)** *(for local backend dev)*
- **Node.js (v20 LTS)** & **npm** *(for local frontend dev)*
- **Python 3.11** *(for local AI service dev)*
- **MySQL 8.0+** *(if running outside Docker)*

---

### Option A: One-Click Launch (Docker Compose)

The fastest method to launch the entire IntelliCampus ecosystem:

1. **Clone the repository**:
   ```bash
   git clone https://github.com/vaishnavibodakhe/IntelliCampus.git
   cd IntelliCampus
   ```

2. **Configure environment variables**:
   Create a `.env` file in the project root:
   ```env
   # MySQL Database
   DB_HOST=db
   DB_PORT=3306
   DB_NAME=intellicampus_db
   DB_USER=campus_admin
   DB_PASSWORD=SecureCampusPassword2026!
   MYSQL_ROOT_PASSWORD=RootSecurePassword2026!

   # Redis Cache
   REDIS_HOST=redis
   REDIS_PORT=6379

   # JWT Security
   JWT_SECRET=4c8a91f2b6e3d7a8c1f4e5a9b2d8c3e7f1a5b9c2d8e4f1a7b3c9d5e2f8a4b6c1
   JWT_ACCESS_EXPIRATION_MS=900000        # 15 minutes
   JWT_REFRESH_EXPIRATION_MS=604800000    # 7 days

   # AI Service Endpoints
   AI_SERVICE_URL=http://ai-service:8000

   # LLM Provider (Hosted or Local Ollama)
   SPRING_AI_OPENAI_API_KEY=your_api_key_or_placeholder
   SPRING_AI_OPENAI_BASE_URL=https://api.openai.com
   ```

3. **Build and spin up all containers**:
   ```bash
   docker-compose up --build -d
   ```

4. **Verify running services**:
   ```bash
   docker-compose ps
   ```

| Container | Service | Host Port | Internal Description |
| :--- | :--- | :--- | :--- |
| `intellicampus-web` | Frontend Web UI | `http://localhost:3000` | Nginx reverse proxy serving React app |
| `intellicampus-api` | Backend API Monolith | `http://localhost:8080` | Spring Boot 3 Core Server |
| `intellicampus-ai` | AI Microservice | `http://localhost:8000` | FastAPI Face & Risk Engine |
| `intellicampus-db` | Database | `localhost:3306` | MySQL 8.0 Server |
| `intellicampus-redis` | Cache & Sessions | `localhost:6379` | Redis 7 Alpine |

---

### Option B: Manual Development Environment

#### 1. Database & Cache
```bash
# Launch MySQL 8.0 and Redis using Docker
docker run -d --name campus-db -p 3306:3306 -e MYSQL_DATABASE=intellicampus_db -e MYSQL_USER=campus_admin -e MYSQL_PASSWORD=SecureCampusPassword2026! -e MYSQL_ROOT_PASSWORD=RootSecurePassword2026! mysql:8.0
docker run -d --name campus-redis -p 6379:6379 redis:7-alpine
```

#### 2. Python AI Service
```bash
cd ai-service
python -m venv .venv
source .venv/bin/activate  # On Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

#### 3. Spring Boot Backend Monolith
```bash
cd backend
mvn clean spring-boot:run
```
Access OpenAPI Swagger documentation at: `http://localhost:8080/swagger-ui.html`

#### 4. React Frontend Web Application
```bash
cd frontend
npm install
npm start
```
The application will launch automatically at: `http://localhost:3000`

---

## 🌐 REST API Reference & Status Codes

All API endpoints follow RESTful design rooted at `/api`. Dates follow ISO-8601 UTC.

| HTTP Method | Route | Role | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/login` | Public | Authenticates user; returns JWT token & user role. |
| `POST` | `/api/auth/refresh` | Public | Exchanges HTTP-only cookie refresh token for new access token. |
| `POST` | `/api/admin/users/import` | Admin | Bulk user creation via CSV file with line-level validation. |
| `POST` | `/api/students/{id}/face` | Student/Admin | Enrolls face embeddings (3 live photos) with explicit consent. |
| `POST` | `/api/sessions/start` | Faculty | Initializes class attendance session and challenge code. |
| `POST` | `/api/sessions/{id}/photo` | Faculty | Ingests classroom photo, validates geofence, and triggers AI. |
| `POST` | `/api/attendance/{sessionId}/confirm` | Student | 2-minute geofenced student attendance confirmation. |
| `POST` | `/api/attendance/{sessionId}/qr` | Student | Fallback dynamic QR attendance scan inside room. |
| `POST` | `/api/attendance/{sessionId}/override` | Faculty | Manual override with mandatory justification ($\ge 10$ chars). |
| `POST` | `/api/sessions/{id}/finalise` | Faculty | Locks session, generates anomaly flags, commits audit log. |
| `POST` | `/api/marks` | Faculty | Enters internal marks in draft state. |
| `POST` | `/api/marks/{id}/correction` | Faculty | Submits correction request for published locked marks. |
| `PUT` | `/api/marks/corrections/{id}/decision` | HOD | Authorizes or rejects faculty mark correction request. |
| `GET` | `/api/audit/verify` | Admin | Recalculates cryptographic SHA-256 chain across audit blocks. |
| `POST` | `/api/notices` | Staff | Dispatches targeted notice with departmental segmentation. |
| `POST` | `/api/chat/ask` | Logged In | RAG chatbot query returning document-grounded citation. |
| `GET` | `/api/risk/mentees` | Mentor | Retrieves mentee risk ratings, trends, and alert indicators. |
| `POST` | `/api/documents/issue` | Admin | Issues verifiable PDF credential with embedded QR digest. |
| `GET` | `/api/verify/{code}` | Public | Unauthenticated verification of document authenticity status. |
| `POST` | `/api/leave` | Student | Submits digital leave request with date range and attachment. |
| `PUT` | `/api/leave/{id}/decision` | Mentor/HOD | Evaluates leave approval at appropriate tier. |

### Standardized System Error Codes

| Error Code | HTTP Status | Root Cause |
| :--- | :--- | :--- |
| `INVALID_CREDENTIALS` | 401 Unauthorized | Incorrect user code or password. |
| `ACCOUNT_LOCKED` | 423 Locked | Exceeded 5 failed login attempts (locked 15 mins). |
| `FORBIDDEN_ROLE` | 403 Forbidden | User lacks necessary RBAC permissions. |
| `WINDOW_CLOSED` | 410 Gone | Class photo or confirmation window expired. |
| `OUTSIDE_GEOFENCE` | 422 Unprocessable | Device GPS coordinates exceed room radius boundary. |
| `DUPLICATE_PHOTO` | 422 Unprocessable | Exact image SHA-256 match identified from previous sessions. |
| `NEAR_DUPLICATE_PHOTO` | 422 Unprocessable | Image DCT pHash Hamming distance $\le 8$ (resized/cropped). |
| `DEVICE_NOT_APPROVED` | 403 Forbidden | Student confirmation attempted from unverified device. |
| `AI_UNAVAILABLE` | 502 Bad Gateway | Python AI microservice failed to respond. |
| `REASON_REQUIRED` | 400 Bad Request | Justification text is shorter than 10 required characters. |
| `MARKS_PUBLISHED` | 409 Conflict | Direct edits rejected; requires formal HOD correction. |
| `NOT_FOUND` | 404 Not Found | Requested entity, session, or document code not found. |

---

## 🔒 Privacy, Ethics & DPDP Compliance

IntelliCampus is intentionally architected to strictly adhere to **India's Digital Personal Data Protection (DPDP) Act, 2023**:

1. **Informed Digital Consent**: Biometric enrollment cannot be triggered without an affirmative, cryptographically recorded student consent action (`consent_at`, `consent_version`).
2. **Zero Raw Image Retention**: Raw facial photographs captured during attendance are processed purely in ephemeral memory and discarded immediately upon 512-dimension vector extraction. No raw facial images are stored on disk or database.
3. **Encryption at Rest**: Face vectors stored in the database are encrypted at rest using **AES-GCM-256** with encryption keys maintained in environment secret vaults outside the database.
4. **Purpose Limitation & Right to Erasure**: Biometric embeddings are strictly utilized for attendance validation and never repurposed. Students possess the irrevocable right to withdraw consent; an administrative action immediately purges all biometric records.
5. **Privacy by Default for Public Verification**: Public document validation outputs only masked credentials, preventing identity theft while proving authenticity.
6. **No Continuous GPS Surveillance**: Geolocation coordinates are requested exclusively at the instantaneous moment of attendance submission to establish classroom presence and are never continuously tracked.

---

## 🧪 Quality Benchmarks & Test Acceptance Plan

The platform is evaluated against rigorous acceptance thresholds:

```
+-----------------------------------------------------------------------------------------+
|                                ACCEPTANCE CRITERIA MATRIX                               |
+--------+---------------------+-------------------------------+--------------------------+
| Code   | Quality Attribute   | Engineering Benchmark         | Verification Method      |
+--------+---------------------+-------------------------------+--------------------------+
| NFR-01 | Vision Speed        | $\le 30$ seconds for 60 faces | Timed stress benchmark   |
| NFR-02 | API Response Time   | 95% of queries $\le 2.0$s     | JMeter (200 concurrent)  |
| NFR-03 | Channel Encryption  | 100% HTTPS TLS 1.3 / WSS      | Automated security audit |
| NFR-04 | Access Control      | 100% server-side role check   | Integration test matrix  |
| NFR-07 | Log Immutability    | 100% detect altered db row    | Cryptographic validator  |
| NFR-08 | Match Safety        | Zero wrong-match accepted     | Volunteer test dataset   |
| NFR-09 | Teacher Workflow    | Completed in $\le 5$ clicks   | Real-world usability trial|
| NFR-11 | System Portability  | 1-step `docker-compose up`    | Clean VM verification    |
+--------+---------------------+-------------------------------+--------------------------+
```

---

## 📚 Literature References

1. **Early Warning Systems**: *Machine Learning in Higher Education: Predicting and Mitigating Student Attrition*, IEEE, 2024. *(Random Forest and Decision Trees for early dropout detection).*
2. **Document-Grounded Chatbots**: *Development and Evaluation of a University Chatbot: A RAG-Based Approach*, Ulster University, 2025. *(Retrieval-augmented grounding reduces hallucination).*
3. **Fraud-Resistant Biometrics**: *AttendEase: Fraud-Resistant Attendance Tracking*, IJACSA. *(Multi-factor verification combining dynamic QR, geofencing, and biometrics).*
4. **Verifiable Academic Records**: *Academic Certificate Validation Using Blockchain Technology*, IEEE, 2022. *(Hash-based tamper-evident credential verification).*

---

## 📄 License & Contributors

Distributed under the **MIT License**. See `LICENSE` for further details.

**IntelliCampus Development Team**  
*Final-Year Capstone Project — Java Full Stack & AI*  
- **Repository**: [https://github.com/vaishnavibodakhe/IntelliCampus](https://github.com/vaishnavibodakhe/IntelliCampus)  
- **Lead Developer**: Vaishnavi Bodakhe ([vaishnavibodakhe11@gmail.com](mailto:vaishnavibodakhe11@gmail.com))
