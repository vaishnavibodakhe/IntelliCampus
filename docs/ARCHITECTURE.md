# 🏛️ IntelliCampus: Technical Architecture & System Design

This document details the engineering architecture, structural patterns, inter-service contracts, and key design decisions implemented in **IntelliCampus**, corresponding to the Software Requirements Specification (IC-SRS-1.0).

---

## 🏗️ Architectural Topology

IntelliCampus is structured as a **Modular Monolith** backend complemented by a specialized **Computer Vision / AI Microservice**, backed by **PostgreSQL 16 with pgvector** and **Redis 7**.

```
+-----------------------------------------------------------------------------------------+
|                                    PRESENTATION TIER                                    |
|                                                                                         |
|       +-------------------------------------------------------------------------+       |
|       |                           React 18 SPA Client                           |       |
|       |               Role Views: Admin / HOD / Faculty / Mentor / Student      |       |
|       |               Hardware APIs: MediaDevices (Camera), Geolocation (GPS)   |       |
|       +------------------------------------+------------------------------------+       |
+--------------------------------------------|--------------------------------------------+
                                             | HTTPS REST (JSON) / WSS STOMP
                                             v
+-----------------------------------------------------------------------------------------+
|                                    APPLICATION TIER                                     |
|                                                                                         |
|  +-----------------------------------------------------------------------------------+  |
|  |                             Spring Boot 3 Modular Monolith                        |  |
|  |                                                                                   |  |
|  |  +---------------+  +---------------+  +---------------+  +--------------------+  |  |
|  |  |  auth & rbac  |  |  admin-mgmt   |  |  attendance   |  |    marks-audit     |  |  |
|  |  +---------------+  +---------------+  +---------------+  +--------------------+  |  |
|  |  +---------------+  +---------------+  +---------------+  +--------------------+  |  |
|  |  |  leave-mgmt   |  |  docs-verify  |  | chatbot (RAG) |  |   risk-engine      |  |  |
|  |  +---------------+  +---------------+  +---------------+  +--------------------+  |  |
|  |  +---------------+  +---------------+  +---------------+  +--------------------+  |  |
|  |  |  audit-chain  |  | notify-stomp  |  | common-utils  |  | config-security    |  |  |
|  |  +---------------+  +---------------+  +---------------+  +--------------------+  |  |
|  +-----------------------------------------+-----------------------------------------+  |
+--------------------------------------------|--------------------------------------------+
                                             |
             +-------------------------------+-------------------------------+
             | (JDBC over TLS)               | (Redis Protocol)              | (Internal HTTP REST)
             v                               v                               v
+---------------------------+   +---------------------------+   +---------------------------+
|      DATA & VECTOR        |   |       MEMORY CACHE        |   |      VISION & ML AI       |
|                           |   |                           |   |                           |
|   PostgreSQL 16 Engine    |   |      Redis 7 Alpine       |   |  Python 3.11 FastAPI App  |
|   - Relational Core Data  |   |   - In-Memory Rate Limits |   |   - InsightFace ArcFace   |
|   - pgvector Face Embeds  |   |   - Dynamic 15s QR HMACs  |   |   - Anti-Spoofing CNN     |
|   - pgvector RAG Chunks   |   |   - Ephemeral Sessions    |   |   - Scikit-Learn EWS      |
|   - SHA-256 Audit Chain   |   |   - Blacklisted Tokens    |   |   - Zero Image Storage    |
+---------------------------+   +---------------------------+   +---------------------------+
```

---

## ⚖️ Key Architectural Decisions & Trade-Offs

| Decision | Engineering Rationale | Strategic Trade-off |
| :--- | :--- | :--- |
| **Modular Monolith over Microservices** | Keeps database transactions, deployments, and testing manageable for an institutional team while strictly isolating domain packages. | All backend modules scale within the same process; however, clean package interfaces make extracting future microservices straightforward. |
| **Separate Python AI Microservice** | State-of-the-art vision models (InsightFace, ONNX Runtime) and ML libraries (scikit-learn) have first-class support and highest inference performance in Python. | Introduces an internal network RPC hop between the Spring Boot server and the Python worker. |
| **Vector Matching inside PostgreSQL (pgvector)** | Consolidates identity records and 512-dimensional vector indexes into one ACID-compliant database, enabling SQL queries to filter vectors by classroom division before cosine similarity search. | Requires vector index tuning (HNSW / IVFFlat) as institutional student populations scale into tens of thousands. |
| **Student 2-Factor Presence Confirmation** | Removes single point of failure from vision models or instructor oversight by requiring active student device confirmation. | Students require smartphones in class; handled gracefully by the teacher's dynamic QR code fallback and manual override options. |
| **Cryptographic Hash Chain for Audit Logging** | Implements blockchain-level tamper evidence without the compute overhead, latency, or monetary expense of external distributed ledgers. | Detects unauthorized tampering if database rows are directly edited via raw SQL, but cannot prevent edits if a rogue root admin modifies both data and hash recalculations. |
| **Biometric Vectors Only (No Stored Photos)** | Minimizes privacy risk and guarantees compliance with the DPDP Act 2023. Images are processed in RAM and discarded immediately. | Cannot re-extract embeddings from past sessions if the underlying face recognition model architecture is upgraded. |
| **Strict RAG Grounding ($T=0$)** | Eliminates hallucination by enforcing generation strictly from retrieved context passages with exact document and page citations. | Chatbot refuses to answer out-of-scope questions, prioritizing accuracy over speculative conversation. |

---

## 🔒 Security Architecture & Defensive Layers

```
                                 [ Client Request ]
                                         |
                                         v
                      +-------------------------------------+
                      |   1. Transport Layer Security       | ---> Enforces TLS 1.3 / HTTPS
                      +------------------+------------------+
                                         |
                                         v
                      +-------------------------------------+
                      |   2. Rate Limiter (Redis)           | ---> Blocks brute-force attacks
                      +------------------+------------------+
                                         |
                                         v
                      +-------------------------------------+
                      |   3. JWT Stateless Filter           | ---> Validates signature & expiry
                      +------------------+------------------+
                                         |
                                         v
                      +-------------------------------------+
                      |   4. Spring Method Security (RBAC)  | ---> Checks role permissions
                      +------------------+------------------+
                                         |
                                         v
                      +-------------------------------------+
                      |   5. Domain Data Scoping            | ---> Restricts to own cohort/division
                      +------------------+------------------+
                                         |
                                         v
                      +-------------------------------------+
                      |   6. SHA-256 Audit Chain Logging    | ---> Records changes immutably
                      +-------------------------------------+
```

### Threat Modeling & Countermeasures

- **Password Guessing**: Enforces BCrypt hashing, progressive exponential delays, and account lockout for 15 minutes after 5 consecutive failures.
- **Biometric Replay / Photo Spoofing**:
  1. Attendance capture is locked to live camera streams (no file uploads).
  2. Classroom challenge code must appear visibly in the frame.
  3. Image SHA-256 and 64-bit DCT pHash block identical or resized images.
  4. Anti-spoofing CNN scores face liveness.
- **Location Spoofing**: Device GPS is checked against classroom coordinates using the Haversine formula, requiring accuracy $\le 100\text{ m}$. Combined with student confirmation and registered device fingerprinting.
- **Tampering with Marks**: Published grades are locked. Modifications require formal HOD approval and are committed to the tamper-evident hash chain.

---

## 🐳 Container Architecture & Docker Compose Setup

```yaml
version: '3.8'

services:
  web:
    build: ./frontend
    ports:
      - "3000:80"
    depends_on:
      - api

  api:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - DB_HOST=db
      - REDIS_HOST=redis
      - AI_SERVICE_URL=http://ai:8000
    depends_on:
      - db
      - redis
      - ai

  ai:
    build: ./ai-service
    ports:
      - "8000:8000"

  db:
    image: pgvector/pgvector:pg16
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: intellicampus_db
      POSTGRES_USER: campus_admin
      POSTGRES_PASSWORD: SecureCampusPassword2026!
    volumes:
      - pgdata:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  pgdata:
```
