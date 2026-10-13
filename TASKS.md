# IntelliCampus Work Allocation & Task Tracker

**Milestone**: `IntelliCampus v1`  
**Repository**: [https://github.com/vaishnavibodakhe/IntelliCampus](https://github.com/vaishnavibodakhe/IntelliCampus)  
**Total Real Issues**: 24 active tasks (#2 to #25) — exactly 6 per member across UI, Backend, Database, Validation, Testing, Documentation.  
*(Note: Issue #1 was a test issue and has been closed).*

---

## 📊 Summary of Member Work Allocation

| Member | Role | GitHub Username | Label | Module Assigned | Real Issue Range | Issue Count | Status |
| :--- | :--- | :--- | :--- | :--- | :---: | :---: | :---: |
| **Bhumika Khairnar** | Team Leader | `@khairnarbhumika34-ship-it` | `member-1` | Smart Attendance & Multimodal Verification | #2 – #7 | 6 | Open |
| **Siddhi Kolage** | Developer | `@siddhi1111111` | `member-2` | Academic Grading & Multi-Tier Leave Workflow | #8 – #13 | 6 | Open |
| **Gayatri Jadhav** | Developer | `@jgayatri664` | `member-3` | User Administration, Academic Setup & Campus Notices | #14 – #19 | 6 | Open |
| **Vaishnavi Bodakhe** | Repo Owner | `@vaishnavibodakhe` | `member-4` | Verifiable Credentials, Cryptographic Audit & AI Analytics | #20 – #25 | 6 | Open |

---

## 📋 Comprehensive Tasks & Issues Directory

### Member 1: Bhumika Khairnar (`@khairnarbhumika34-ship-it`)
**Module**: Smart Attendance & Multimodal Verification  
**Extra Role**: PR Review & Merge Coordinator

| Issue # | Task Type | Issue Title & Link | Assigned To | Labels | Files Involved | Status |
| :---: | :--- | :--- | :--- | :--- | :--- | :---: |
| **#2** | `frontend` | [[Smart Attendance] UI: Real-Time Dynamic QR Display & GPS Confirmation Interface](https://github.com/vaishnavibodakhe/IntelliCampus/issues/2) | `@khairnarbhumika34-ship-it` | `frontend`, `member-1` | `src/components/FacultyView.jsx`, `src/components/StudentView.jsx` | ✅ Closed ([PR #34](https://github.com/vaishnavibodakhe/IntelliCampus/pull/34)) |
| **#3** | `backend` | [[Smart Attendance] Backend: Attendance Session Lifecycle & Dynamic QR Token API](https://github.com/vaishnavibodakhe/IntelliCampus/issues/3) | `@khairnarbhumika34-ship-it` | `backend`, `member-1` | `backend/src/main/java/com/intellicampus/attendance/controller/`, `.../service/` | ✅ Closed ([PR #31](https://github.com/vaishnavibodakhe/IntelliCampus/pull/31)) |
| **#4** | `database` | [[Smart Attendance] Database: AttendanceSession & AttendanceRecord Entities](https://github.com/vaishnavibodakhe/IntelliCampus/issues/4) | `@khairnarbhumika34-ship-it` | `database`, `member-1` | `backend/src/main/java/com/intellicampus/attendance/model/`, `.../repository/` | ✅ Closed ([PR #27](https://github.com/vaishnavibodakhe/IntelliCampus/pull/27)) |
| **#5** | `validation` | [[Smart Attendance] Validation: Geofence Haversine Boundary & Spoof Score Validator](https://github.com/vaishnavibodakhe/IntelliCampus/issues/5) | `@khairnarbhumika34-ship-it` | `validation`, `member-1` | `src/utils/cryptoAndGeo.js`, `backend/src/main/java/com/intellicampus/attendance/validation/` | Open |
| **#6** | `testing` | [[Smart Attendance] Testing: Attendance Session & Geofence Verification Unit/Integration Tests](https://github.com/vaishnavibodakhe/IntelliCampus/issues/6) | `@khairnarbhumika34-ship-it` | `testing`, `member-1` | `backend/src/test/java/com/intellicampus/attendance/`, `src/components/__tests__/FacultyView.test.jsx` | Open |
| **#7** | `documentation` | [[Smart Attendance] Documentation: Attendance System Architecture & Dynamic QR Protocol Spec](https://github.com/vaishnavibodakhe/IntelliCampus/issues/7) | `@khairnarbhumika34-ship-it` | `documentation`, `member-1` | `docs/ATTENDANCE.md`, `README.md` | Open |

---

### Member 2: Siddhi Kolage (`@siddhi1111111`)
**Module**: Academic Grading & Multi-Tier Leave Workflow

| Issue # | Task Type | Issue Title & Link | Assigned To | Labels | Files Involved | Status |
| :---: | :--- | :--- | :--- | :--- | :--- | :---: |
| **#8** | `frontend` | [[Academics & Leave] UI: Marks Entry & Multi-Tier Leave Request Workflow Dashboard](https://github.com/vaishnavibodakhe/IntelliCampus/issues/8) | `@siddhi1111111` | `frontend`, `member-2` | `src/components/HodView.jsx`, `src/components/FacultyView.jsx`, `src/components/StudentView.jsx` | ✅ Closed ([PR #35](https://github.com/vaishnavibodakhe/IntelliCampus/pull/35)) |
| **#9** | `backend` | [[Academics & Leave] Backend: Marks Lifecycle & Multi-Level Leave Approval REST API](https://github.com/vaishnavibodakhe/IntelliCampus/issues/9) | `@siddhi1111111` | `backend`, `member-2` | `backend/src/main/java/com/intellicampus/academics/controller/`, `.../leave/controller/` | ✅ Closed ([PR #32](https://github.com/vaishnavibodakhe/IntelliCampus/pull/32)) |
| **#10** | `database` | [[Academics & Leave] Database: Marks, Grade Corrections & Leave Application Schema](https://github.com/vaishnavibodakhe/IntelliCampus/issues/10) | `@siddhi1111111` | `database`, `member-2` | `backend/src/main/java/com/intellicampus/academics/model/`, `.../leave/model/` | ✅ Closed ([PR #28](https://github.com/vaishnavibodakhe/IntelliCampus/pull/28)) |
| **#11** | `validation` | [[Academics & Leave] Validation: Mark Boundaries, Date Range & Leave Policy Validator](https://github.com/vaishnavibodakhe/IntelliCampus/issues/11) | `@siddhi1111111` | `validation`, `member-2` | `backend/src/main/java/com/intellicampus/academics/validation/`, `.../leave/validation/` | Open |
| **#12** | `testing` | [[Academics & Leave] Testing: Marks Draft-Publish Flow & Leave Approval Tests](https://github.com/vaishnavibodakhe/IntelliCampus/issues/12) | `@siddhi1111111` | `testing`, `member-2` | `backend/src/test/java/com/intellicampus/academics/`, `backend/src/test/java/com/intellicampus/leave/` | Open |
| **#13** | `documentation` | [[Academics & Leave] Documentation: Academic Grading & Leave Approval Workflow Guide](https://github.com/vaishnavibodakhe/IntelliCampus/issues/13) | `@siddhi1111111` | `documentation`, `member-2` | `docs/ACADEMICS_AND_LEAVES.md`, `README.md` | Open |

---

### Member 3: Gayatri Jadhav (`@jgayatri664`)
**Module**: User Administration, Academic Setup & Campus Notices

| Issue # | Task Type | Issue Title & Link | Assigned To | Labels | Files Involved | Status |
| :---: | :--- | :--- | :--- | :--- | :--- | :---: |
| **#14** | `frontend` | [[Admin & Notices] UI: User Directory, Room Geofencing & Targeted Notice Broadcast Portal](https://github.com/vaishnavibodakhe/IntelliCampus/issues/14) | `@jgayatri664` | `frontend`, `member-3` | `src/components/AdminView.jsx`, `src/components/Navbar.jsx`, `src/components/StudentView.jsx` | ✅ Closed ([PR #36](https://github.com/vaishnavibodakhe/IntelliCampus/pull/36)) |
| **#15** | `backend` | [[Admin & Notices] Backend: User Directory, Academic Geofence Setup & Targeted Notices API](https://github.com/vaishnavibodakhe/IntelliCampus/issues/15) | `@jgayatri664` | `backend`, `member-3` | `backend/src/main/java/com/intellicampus/user/controller/`, `.../notice/controller/` | ✅ Closed ([PR #30](https://github.com/vaishnavibodakhe/IntelliCampus/pull/30)) |
| **#16** | `database` | [[Admin & Notices] Database: User, Role, Classroom & Notice Broadcast Entities](https://github.com/vaishnavibodakhe/IntelliCampus/issues/16) | `@jgayatri664` | `database`, `member-3` | `backend/src/main/java/com/intellicampus/user/model/`, `.../notice/model/` | ✅ Closed ([PR #26](https://github.com/vaishnavibodakhe/IntelliCampus/pull/26)) |
| **#17** | `validation` | [[Admin & Notices] Validation: CSV Batch Ingestion & Notice Audience Scope Validator](https://github.com/vaishnavibodakhe/IntelliCampus/issues/17) | `@jgayatri664` | `validation`, `member-3` | `backend/src/main/java/com/intellicampus/user/validation/`, `.../notice/validation/` | Open |
| **#18** | `testing` | [[Admin & Notices] Testing: User CRUD, CSV Bulk Ingestion & Notice Delivery Tests](https://github.com/vaishnavibodakhe/IntelliCampus/issues/18) | `@jgayatri664` | `testing`, `member-3` | `backend/src/test/java/com/intellicampus/user/`, `backend/src/test/java/com/intellicampus/notice/` | Open |
| **#19** | `documentation` | [[Admin & Notices] Documentation: Directory Administration & Notice Targeting Manual](https://github.com/vaishnavibodakhe/IntelliCampus/issues/19) | `@jgayatri664` | `documentation`, `member-3` | `docs/ADMIN_SETUP.md`, `README.md` | Open |

---

### Member 4: Vaishnavi Bodakhe (`@vaishnavibodakhe`)
**Module**: Verifiable Credentials, Cryptographic Audit Ledger & AI Risk/RAG

| Issue # | Task Type | Issue Title & Link | Assigned To | Labels | Files Involved | Status |
| :---: | :--- | :--- | :--- | :--- | :--- | :---: |
| **#20** | `frontend` | [[Audit & Trust] UI: Public Document Verifier, Hash Chain Inspector & EWS UI](https://github.com/vaishnavibodakhe/IntelliCampus/issues/20) | `@vaishnavibodakhe` | `frontend`, `member-4` | `src/components/PublicVerifierView.jsx`, `src/components/AuditChainModal.jsx`, `src/components/MentorView.jsx` | ✅ Closed ([PR #37](https://github.com/vaishnavibodakhe/IntelliCampus/pull/37)) |
| **#21** | `backend` | [[Audit & Trust] Backend: Cryptographic Audit Chain & Document Registry REST API](https://github.com/vaishnavibodakhe/IntelliCampus/issues/21) | `@vaishnavibodakhe` | `backend`, `member-4` | `backend/src/main/java/com/intellicampus/audit/controller/`, `.../credential/controller/` | ✅ Closed ([PR #33](https://github.com/vaishnavibodakhe/IntelliCampus/pull/33)) |
| **#22** | `database` | [[Audit & Trust] Database: AuditLogEntry, VerifiableDocument & RiskRecord Entities](https://github.com/vaishnavibodakhe/IntelliCampus/issues/22) | `@vaishnavibodakhe` | `database`, `member-4` | `backend/src/main/java/com/intellicampus/audit/model/`, `.../credential/model/` | ✅ Closed ([PR #29](https://github.com/vaishnavibodakhe/IntelliCampus/pull/29)) |
| **#23** | `validation` | [[Audit & Trust] Validation: SHA-256 Hash Chain Integrity & Document Tamper Check](https://github.com/vaishnavibodakhe/IntelliCampus/issues/23) | `@vaishnavibodakhe` | `validation`, `member-4` | `src/utils/cryptoAndGeo.js`, `backend/src/main/java/com/intellicampus/audit/validation/` | Open |
| **#24** | `testing` | [[Audit & Trust] Testing: Tamper Detection & Document Verification Test Suite](https://github.com/vaishnavibodakhe/IntelliCampus/issues/24) | `@vaishnavibodakhe` | `testing`, `member-4` | `backend/src/test/java/com/intellicampus/audit/`, `src/utils/__tests__/cryptoAndGeo.test.js` | Open |
| **#25** | `documentation` | [[Audit & Trust] Documentation: Cryptographic Audit Specification & Credential Guide](https://github.com/vaishnavibodakhe/IntelliCampus/issues/25) | `@vaishnavibodakhe` | `documentation`, `member-4` | `docs/SECURITY_AUDIT.md`, `README.md` | Open |

---

## 🔍 Verification Commands
Use GitHub CLI (`gh`) to verify each member's assigned issues:

```bash
gh issue list --repo vaishnavibodakhe/IntelliCampus --assignee khairnarbhumika34-ship-it
gh issue list --repo vaishnavibodakhe/IntelliCampus --assignee siddhi1111111
gh issue list --repo vaishnavibodakhe/IntelliCampus --assignee jgayatri664
gh issue list --repo vaishnavibodakhe/IntelliCampus --assignee vaishnavibodakhe
```
