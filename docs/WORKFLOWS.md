# 🔄 IntelliCampus: Role-Wise Workflows & State Machines

This document provides complete workflow flowcharts, cross-role interactions, and lifecycle state machines for all 6 user roles in **IntelliCampus**, based on the formal institutional project specifications.

---

## 👥 Role Matrix & Responsibilities

| Role | Responsibility Scope | Interacting Roles |
| :--- | :--- | :--- |
| **Admin** | Manages user lifecycles, academic structures, fee records, verifiable documents, circulars, and chatbot knowledge base. | All roles |
| **HOD** | Authorizes student leave ($>3$ days) and marks corrections; analyzes departmental attendance and academic audits. | Faculty, Mentors, Admin |
| **Faculty** | Initiates attendance sessions, captures classroom photos, enters exam marks, manages class notices, resolves disputes. | Students, HOD, Mentors |
| **Mentor** | Monitors assigned student cohort, acts on automated EWS high-risk alerts, handles first-level leave approvals. | Students, HOD, Faculty |
| **Student** | Confirms attendance within 2-min window, scans dynamic fallback QR, applies for leave, queries chatbot, views grades. | Faculty, Mentors |
| **Public Verifier** | Verifies authenticity of issued documents via public portal without login. | External parties, Employers |

---

## 🔀 Cross-Role Interconnected Workflows

```
                                      [ Leave Workflow ]
    Student applies ---> Mentor reviews & forwards ---> HOD approves/rejects ---> Student notified
                                                                                     |
                                                         (Excludes approved days from absences)

                                   [ Attendance Workflow ]
    Faculty captures photo ---> System matches faces ---> Student confirms (2 min) ---> Faculty finalizes
                                                                  |                            |
                                                      (or Fallback Dynamic QR)         (Immutable Audit)

                                  [ Mark Correction Workflow ]
    Faculty enters & publishes marks (Locked) ---> Faculty requests correction ---> HOD approves/rejects
                                                                                           |
                                                                                (Hash-Chained Audit Log)

                                     [ Document Workflow ]
    Admin issues PDF with QR & Code ---> Public Verifier checks on portal ---> Status (Valid/Revoked)

                                    [ Risk Alert Workflow ]
    System runs weekly ML model ---> Flags high-risk student ---> Mentor alerted with factors ---> Mentor logs action
```

---

## 📊 Detailed Role-Wise Workflows

### 1. Admin Workflow

The Admin sets up foundational institutional data that all other operational roles depend upon:

```mermaid
flowchart TD
    Login([Login as Admin])
    Login --> Users[Manage Users]
    Login --> Academic[Academic Setup]
    Login --> Notices[Notices & Circulars]
    Login --> Fees[Fee Records]
    Login --> Docs[Verifiable Documents]
    Login --> Chatbot[Chatbot Knowledge]

    Users --> U1[Add or CSV Import Users]
    U1 --> U2[Assign Role & Department]
    U2 --> U3[Assign Mentors to Students]

    Academic --> A1[Add Departments & Courses]
    A1 --> A2[Create Divisions & Classes]
    A2 --> A3[Link Faculty & HOD; Set Room GPS]

    Notices --> N1[Compose Notice]
    N1 --> N2[Define Target Audience: Dept / Year / Div]
    N2 --> N3[Publish & Broadcast Push Notifications]

    Fees --> F1[Add or Import Fee Dues]
    F1 --> F2[Record Payments & Waivers]
    F2 --> F3[Student Views Dues in Portal]

    Docs --> D1[Upload or Generate Document]
    D1 --> D2[Generate Unique Code & Signed QR]
    D2 --> D3[Public Verification Active]

    Chatbot --> C1[Upload PDF Circulars & Rules]
    C1 --> C2[Review & Trigger Vector Indexing]
    C2 --> C3[Chatbot Answers Student Queries]
```

---

### 2. Faculty Workflow

Faculty members conduct lectures, capture attendance, record internal grades, and handle classroom exceptions:

```mermaid
flowchart TD
    Login([Login as Faculty])
    Login --> Att[Class Attendance]
    Login --> Marks[Student Marks]
    Login --> Notices[Class Notices]

    Att --> Att1[Pick Subject, Division & Room]
    Att1 --> Att2[Start Session: Displays Random Challenge Code]
    Att2 --> Att3[Live Camera Snapshot: Challenge Code Visible]
    Att3 --> Att4[Backend Runs Validation Pipeline]
    Att4 --> Att5[Push Confirmation Sent to Matched Students]
    Att5 --> Att6[Review 4 Lists: Confirmed, Unconfirmed, Review, Absent]
    Att6 --> Att7[Finalize Session & Lock Records]

    Marks --> M1[Select Exam & Division]
    M1 --> M2[Input Marks via Grid or CSV]
    M2 --> M3[Save Draft -> Publish & Lock]
    M3 --> M4[Need change? Raise Correction with Reason]

    Notices --> N1[Select Assigned Class]
    N1 --> N2[Compose Notice & Attach Files]
    N2 --> N3[Publish & Track Student Read Receipts]
```

---

### 3. Student Workflow

Students interact with their individual academic records via desktop and mobile web browsers:

```mermaid
flowchart TD
    Login([Login as Student])
    Login --> View[View Academic Dashboard]
    Login --> Confirm[Classroom Attendance]
    Login --> Leave[Apply for Leave]
    Login --> Bot[Query Campus Chatbot]

    View --> V1[Check Subject-wise Attendance %]
    View --> V2[View Exam Marks & Historical Changes]
    View --> V3[Download Verified Receipts & Documents]

    Confirm --> C1[Receive WebSocket Push Notification]
    C1 --> C2[120-Second Active Countdown]
    C2 --> C3{Face Matched?}
    C3 -- Yes --> C4[Confirm from Registered Device Inside Room]
    C3 -- No --> C5[Scan Dynamic Rotating QR from Teacher Screen]
    C4 --> C6[Marked PRESENT]
    C5 --> C7[Marked PRESENT_QR]

    Leave --> L1[Select Dates & Leave Category]
    L1 --> L2[Attach Justification or Medical Certificate]
    L2 --> L3[Track Workflow Status: Mentor -> HOD]

    Bot --> B1[Type Natural Language Question]
    B1 --> B2[View Document-Grounded Answer with Page Citations]
    B2 --> B3[Rate Response as Helpful or Unhelpful]
```

---

### 4. Mentor Workflow

Mentors supervise student wellbeing, evaluate leave applications, and act on predictive early-warning alerts:

```mermaid
flowchart TD
    Login([Login as Mentor])
    Login --> Mentees[Assigned Cohort]
    Login --> Risk[Early Warning System]
    Login --> Leave[Leave Requests]

    Mentees --> M1[View Assigned Student List]
    M1 --> M2[Inspect Attendance Trends & Grade Trajectories]
    M2 --> M3[Record Mentorship Meeting Notes]

    Risk --> R1[Receive Weekly High-Risk Notifications]
    R1 --> R2[Inspect Top 3 Statistical Risk Drivers]
    R2 --> R3[Schedule In-Person Meeting with Student]
    R3 --> R4[Log Mandatory Action Taken in System]

    Leave --> L1[Inspect Student Leave Application & Proof]
    L1 --> L2{Duration?}
    L2 -- "<= 3 Days" --> L3[Approve or Reject with Comment]
    L2 -- "> 3 Days" --> L4[Recommend & Forward to HOD]
```

---

### 5. Head of Department (HOD) Workflow

HODs govern departmental operations, authorize sensitive grade modifications, and approve extended leaves:

```mermaid
flowchart TD
    Login([Login as HOD])
    Login --> Leave[Extended Leave Requests]
    Login --> Corrections[Mark Correction Requests]
    Login --> Reports[Department Analytics]

    Leave --> L1[Review Requests Forwarded by Mentors]
    L1 --> L2[Inspect Dates, Academic Standing & Reasons]
    L2 --> L3[Final Approve or Reject with Justification]
    L3 --> L4[Attendance Denominator Updated Automatically]

    Corrections --> C1[Receive Faculty Correction Request]
    C1 --> C2[Compare Old Grade vs New Requested Grade]
    C2 --> C3[Inspect Mandatory Faculty Reason]
    C3 --> C4[Approve or Reject]
    C4 --> C5[Approval Automatically Logged in Hash Chain]

    Reports --> R1[Defaulter Lists (< 75% Attendance)]
    Reports --> R2[Department Examination Grade Distributions]
    Reports --> R3[Export Standardized Reports (PDF / CSV)]
```

---

### 6. Public Verifier Workflow

Public third parties verify academic credentials without authentication credentials:

```mermaid
flowchart TD
    Start([Open Public Verification Portal])
    Start --> Input[Enter Document UUID or Scan Embedded QR]
    Input --> Backend[System Calculates SHA-256 Digest]
    Backend --> Check{Found & Valid?}
    Check -- "Found & Valid" --> Valid[Display VALID Status with Masked Details]
    Check -- "Revoked by Admin" --> Revoked[Display REVOKED Status with Reason]
    Check -- "Not in Database" --> NotFound[Display NOT FOUND - Potential Forgery]
```

---

## 🔄 State Machine Specifications

### 1. Attendance Record Lifecycle

```text
                  +-----------------------------------+
                  |              PENDING              |
                  +-----------------+-----------------+
                                    |
                    +---------------+---------------+
                    |                               |
                    v                               v
           +-----------------+             +-----------------+
           |     MATCHED     |             |    UNMATCHED    |
           +--------+--------+             +--------+--------+
                    |                               |
       +------------+------------+           +------+------+
       |                         |           |             |
       v                         v           v             v
+-------------+          +---------------+ +--------+  +--------+
|   PRESENT   |          | NOT_CONFIRMED | | REVIEW |  | ABSENT |
+-------------+          +-------+-------+ +--------+  +--------+
                                 |             ^
                                 v             |
                           +------------+      |
                           | PRESENT_QR +------+ (Flagged if dual device)
                           +------------+
```

### 2. Leave Request State Machine

```text
       [ Student Submits ]
                |
                v
          +-----------+
          | SUBMITTED | -----(Student cancels)-----> [ CANCELLED ]
          +-----+-----+
                |
       (Mentor evaluates)
         /             \
        v               v
  [ REJECTED ]   +-----------------+
                 | MENTOR_APPROVED |
                 +--------+--------+
                          |
                  (HOD evaluates)
                    /          \
                   v            v
             [ REJECTED ]  [ APPROVED ]
                                |
                   (Attendance Adjusted)
```

### 3. Academic Document State Machine

```text
       [ Admin Issues Document ]
                   |
                   v
              +----------+
              |  ISSUED  | <---- (Verifiable by Public QR)
              +----+-----+
                   |
             (Admin revokes)
                   |
                   v
              +----------+
              | REVOKED  | <---- (Displays Revocation Reason)
              +----------+
```
