# 💻 IntelliCampus - Frontend Web Client (React 18)

[![React 18](https://img.shields.io/badge/Frontend-React%2018-61DAFB.svg?logo=react)](https://react.dev/)
[![Tailwind CSS](https://img.shields.io/badge/Styling-Tailwind%20CSS-38B2AC.svg?logo=tailwind-css)](https://tailwindcss.com/)
[![WebSocket](https://img.shields.io/badge/Realtime-STOMP%20%2F%20WebSocket-orange.svg)](https://stomp-js.github.io/)
[![QR Scanner](https://img.shields.io/badge/QR%20Scanner-html5--qrcode-brightgreen.svg)](https://github.com/mebjas/html5-qrcode)

---

## 📖 Overview

The **IntelliCampus Frontend** is a mobile-first, responsive Single Page Application (SPA) developed with **React 18**. It serves role-customized experiences across the 6 institutional user personas: **Admin**, **HOD**, **Faculty**, **Mentor**, **Student**, and **Public Verifier**.

The interface integrates directly with modern web APIs—including **MediaDevices (live camera capture)**, **Geolocation (HTML5 GPS coordinates)**, and **WebSocket STOMP (real-time classroom push updates)**—while enforcing strict client-side role guards and device fingerprint binding.

---

## 📱 Screen Inventory (S-01 to S-23)

All 23 standardized application views specified in the Functional Requirements Document (IC-FRD-1.0 Section 7) are implemented in the client:

| Screen ID | Screen Name | Authorized Roles | Core Interactive Features |
| :--- | :--- | :--- | :--- |
| **S-01** | Login Page | Public | User code, password input, lockout notification display. |
| **S-02** | Force Password Change | All Users | Mandatory initial password update with complexity checks. |
| **S-03** | Admin Dashboard | Admin | Active system metrics, flagged session feed, audit health status. |
| **S-04** | User Management | Admin | Searchable user directory, role assignment, CSV bulk import with error report. |
| **S-05** | Academic Setup | Admin | Manage departments, divisions, subjects, classrooms (map pin + radius), mentors. |
| **S-06** | Faculty Dashboard | Faculty | Daily lecture schedule, quick-launch attendance, recent session log. |
| **S-07** | Session Launch | Faculty | Pick subject, division, and room; generates random classroom challenge code. |
| **S-08** | In-App Live Capture | Faculty | **Live camera feed only** (file upload blocked); 10-minute countdown timer. |
| **S-09** | Attendance Review | Faculty | Segmented tabs (*Confirmed*, *Matched-Unconfirmed*, *Review*, *Absent*); manual overrides. |
| **S-10** | Student Attendance | Student | Subject-wise attendance percentages, defaulter alert badges, dispute submission. |
| **S-11** | Confirm Attendance | Student | 120-second active countdown, location confirmation button, dynamic QR scanner. |
| **S-12** | Marks Entry & Edit | Faculty | Exam grade entry grid, CSV bulk marks upload, lock & publish action. |
| **S-13** | Audit Trail Explorer | Admin, HOD | Immutable historical change log, diff viewer, SHA-256 chain verification button. |
| **S-14** | Circulars & Notices | All Roles | Targeted announcement publisher (department/year/division), read receipt tracker. |
| **S-15** | RAG Campus Chatbot | All Logged-in | Document-grounded assistant, source document/page citations, helpfulness ratings. |
| **S-16** | Knowledge Base Admin | Admin | PDF and ordinance uploader, indexing progress bar, document status list. |
| **S-17** | Risk Analytics List | Mentor, HOD | Cohort early-warning dashboard filtered by risk tier (Low, Medium, High). |
| **S-18** | Student Risk Detail | Mentor | Top 3 statistical risk contributors, historical trend graphs, intervention note logger. |
| **S-19** | Document Generator | Admin | Official PDF issuance (fee receipts, bonafides) with embedded QR and hash. |
| **S-20** | Public Verification | Public | Zero-login verification portal; QR camera scanner; confirms Valid/Revoked status. |
| **S-21** | Apply for Leave | Student | Digital leave submission, multi-day calendar picker, medical certificate upload. |
| **S-22** | Leave Approvals | Mentor, HOD | Multi-tier decision queue (Mentor first tier, HOD second tier for $>3$ days). |
| **S-23** | Flagged Session Audit| Admin, HOD | Investigates automated anomaly flags (replays, proxy clusters, spoof attempts). |

---

## 🔒 Hardware & Browser API Integrations

### 1. Live Camera Only (Capture Security)
To enforce business rule `BRU-02` and prevent replay attacks from the photo gallery, Screen `S-08` locks camera access strictly to the browser `navigator.mediaDevices.getUserMedia()` stream:
- File chooser inputs (`<input type="file">`) are explicitly forbidden on the attendance capture screen.
- Front/rear camera switching is supported.

### 2. High-Accuracy Geolocation Binding
Classroom presence requires instantaneous location validation:
```javascript
navigator.geolocation.getCurrentPosition(
  (position) => {
    const { latitude, longitude, accuracy } = position.coords;
    if (accuracy > 100) {
      displayError("GPS accuracy too low. Please move closer to a window.");
      return;
    }
    submitAttendancePayload({ lat: latitude, lng: longitude, accuracy });
  },
  (err) => displayError("Classroom attendance requires location permissions."),
  { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
);
```

### 3. Real-Time STOMP over WebSocket
The student portal listens for instant classroom triggers upon facial recognition:
- Subscribes to private queue `/user/queue/attendance`
- Automatically opens the 120-second confirmation drawer when matched in a lecture photo.

---

## ⚙️ Environment Variables

Create `.env` in the `frontend/` directory:

```env
# Backend REST API Base URL
REACT_APP_API_BASE_URL=http://localhost:8080/api

# Real-time WebSocket Gateway
REACT_APP_WS_BROKER_URL=ws://localhost:8080/ws

# Default App Title
REACT_APP_NAME="IntelliCampus"
```

---

## 🚀 Setup & Execution Guide

### 1. Install Dependencies
```bash
cd frontend
npm install
```

### 2. Run Development Server
```bash
npm start
```
Runs the web app at `http://localhost:3000`.

### 3. Production Build
```bash
npm run build
```
Generates an optimized static bundle in `build/` ready for Nginx deployment.
