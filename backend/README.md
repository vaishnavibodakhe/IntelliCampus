# ☕ IntelliCampus - Backend Monolith (Spring Boot 3)

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg?logo=openjdk)](https://openjdk.org/)
[![Spring Boot 3](https://img.shields.io/badge/Framework-Spring%20Boot%203.2-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Security-Spring%20Security%206-green.svg)](https://spring.io/projects/spring-security)
[![Spring AI](https://img.shields.io/badge/AI-Spring%20AI-blue.svg)](https://spring.io/projects/spring-ai)
[![Flyway](https://img.shields.io/badge/Migration-Flyway-red.svg)](https://flywaydb.org/)
[![MySQL 8](https://img.shields.io/badge/Database-MySQL%208.0%2B-4479A1.svg?logo=mysql)](https://www.mysql.com/)

---

## 📖 Overview

The **IntelliCampus Backend** is a production-grade modular monolith built on **Spring Boot 3** and **Java 21 LTS**. It contains all business logic, role-based access rules, classroom attendance verification pipelines, tamper-evident audit hashing, document generation, and RAG document search orchestration.

By structuring the backend as a modular monolith, modules maintain clean architectural boundaries with strict package-private separation, making it simple to test and deploy while remaining easily split into independent microservices if needed.

---

## 🧱 Module & Package Architecture

The backend code is partitioned strictly by domain feature in `com.intellicampus`:

```
com.intellicampus/
├── admin/          # Academic setup: Departments, Divisions, Subjects, Classrooms, Mentors
├── attendance/     # Attendance session manager, validation pipeline, geofence, dynamic QR
├── audit/          # SHA-256 hash-chained immutable audit log & verification service
├── auth/           # Authentication, JWT token provider, password policies, account lockout
├── chatbot/        # Document ingestion, Spring AI chunking, vector similarity search, RAG pipeline
├── common/         # Global exception handlers, security utils, base entities, DTOs
├── config/         # SecurityFilterChain, CORS, Redis, WebSocket STOMP config
├── documents/      # Verifiable PDF generation, SHA-256 hashing, public verification
├── leave/          # Multi-step leave requests (Mentor -> HOD) & attendance recalculation
├── marks/          # Internal marks recording, lock-on-publish, HOD correction requests
├── notify/         # WebSocket STOMP messaging (/user/queue/*), notification history
└── risk/           # Scheduled Early Warning System (EWS) caller & mentor interventions
```

### Module Dependency Graph

```
           [ auth ] <--------> [ admin ]
              ^                   ^
              |                   |
    +---------+---------+---------+---------+---------+
    |                   |                   |         |
    v                   v                   v         v
[ attendance ]      [ marks ]           [ leave ] [ notices ]
    |   |               |                   |         |
    |   +---------------+-------------------+         |
    v                   v                             v
[ audit ]        [ ai-client ]                   [ notify ]
                        ^
                        |
            [ chatbot ] + [ risk ]
```

---

## 🔐 Security & Identity Architecture

1. **Authentication Protocol**:
   - `POST /api/auth/login`: Validates user code & BCrypt password hash.
   - Emits a short-lived **JWT Access Token** (valid 15 minutes) kept in client application memory.
   - Sets an **HTTP-only, Secure, SameSite=Strict Refresh Cookie** (valid 7 days).
   - Silent refresh via `POST /api/auth/refresh` rotates refresh tokens.
2. **Account Lockout & Protection**:
   - After **5 consecutive failed attempts** within 15 minutes, account is locked for 15 minutes.
   - Enforced initial password change on first login (`must_change_password=true`).
   - Passwords must have $\ge 8$ characters, including at least one letter and one number.
3. **Role-Based Access Control (RBAC)**:
   - All REST controllers enforce method security: `@PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")`.
   - Data access is strictly scoped: Students can query only their own data; Faculty can access only assigned divisions and subjects; Mentors access only assigned mentees.

---

## 🧮 Key Backend Algorithms

### 1. Haversine Geofencing Engine
Located in `attendance.service.GeofenceService`:
```java
public boolean isWithinRoom(double devLat, double devLng, double devAccuracy, 
                          double roomLat, double roomLng, int roomRadiusM) {
    if (devAccuracy > 100.0) return false; // reject inaccurate GPS fixes
    
    final int R = 6371000; // Earth radius in meters
    double dLat = Math.toRadians(roomLat - devLat);
    double dLng = Math.toRadians(roomLng - devLng);
    
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
               Math.cos(Math.toRadians(devLat)) * Math.cos(Math.toRadians(roomLat)) *
               Math.sin(dLng / 2) * Math.sin(dLng / 2);
               
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    double distance = R * c;
    
    return distance <= roomRadiusM;
}
```

### 2. Hash-Chained Audit Trail Engine
Located in `audit.service.AuditChainService`:
```java
public AuditLog createChainedEntry(Long actorId, String action, String entityType, 
                                  Long entityId, String oldVal, String newVal, String reason) {
    String prevHash = auditRepository.findLatestEntryHash().orElse("GENESIS_BLOCK_INTELLICAMPUS_2026");
    Instant now = Instant.now();
    
    String payload = prevHash + actorId + action + entityType + entityId + 
                     oldVal + newVal + reason + now.toString();
                     
    String entryHash = DigestUtils.sha256Hex(payload);
    
    AuditLog log = new AuditLog(actorId, action, entityType, entityId, oldVal, newVal, reason, prevHash, entryHash, now);
    return auditRepository.save(log);
}
```

### 3. Dynamic Rotating QR Fallback (HMAC-SHA256)
Located in `attendance.service.QrFallbackService`:
- Generates a rotating 16-character time-bound token every 15 seconds.
- Token window accepts both current slot (`t`) and preceding slot (`t-1`), guaranteeing 30-second resilience against network latency.
- Verified tokens are recorded in Redis to guarantee single-use per student per session.

---

## ⚙️ Configuration Properties

Configured in `src/main/resources/application.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:intellicampus_db}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: ${DB_USER:campus_admin}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

app:
  jwt:
    secret: ${JWT_SECRET:4c8a91f2b6e3d7a8c1f4e5a9b2d8c3e7f1a5b9c2d8e4f1a7b3c9d5e2f8a4b6c1}
    access-expiration-minutes: 15
    refresh-expiration-days: 7
    lockout-max-attempts: 5
    lockout-duration-minutes: 15
  attendance:
    photo-window-minutes: 10
    confirm-timeout-seconds: 120
    geofence-default-radius-m: 50
    geofence-max-accuracy-m: 100
    face-match-threshold: 0.50
    face-match-margin: 0.05
    spoof-min-score: 0.60
    phash-max-distance: 8
    qr-slot-seconds: 15
    defaulter-threshold-percent: 75
  ai:
    service-url: ${AI_SERVICE_URL:http://localhost:8000}
```

---

## 🚀 Build & Execution Guide

### Prerequisites
- JDK 21 LTS installed and on `PATH`
- Apache Maven 3.9+
- Active MySQL 8.0+ instance (or Docker container)

### Compile and Package
```bash
# Clean build and run automated unit tests
mvn clean package

# Run Spring Boot application locally
mvn spring-boot:run
```

### Run Tests
```bash
# Execute unit tests
mvn test

# Execute integration tests with Testcontainers
mvn verify -P integration-test
```
