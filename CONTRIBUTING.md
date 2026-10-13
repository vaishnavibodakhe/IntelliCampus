# IntelliCampus Contributing Guidelines

Welcome to **IntelliCampus**! This document outlines our team workflow, branch naming conventions, pull request procedures, and code standards to ensure smooth collaboration and zero merge conflicts.

---

## 👥 Team Roles & Responsibilities

| Member | Role | GitHub Username | Assigned Module | Label |
| :--- | :--- | :--- | :--- | :--- |
| **Bhumika Khairnar** | **Team Leader & Reviewer** | `@khairnarbhumika34-ship-it` | Smart Attendance & Multimodal Verification | `member-1` |
| **Siddhi Kolage** | **Developer** | `@siddhi1111111` | Academic Grading & Multi-Tier Leave Workflow | `member-2` |
| **Gayatri Jadhav** | **Developer** | `@jgayatri664` | User Administration, Academic Setup & Campus Notices | `member-3` |
| **Vaishnavi Bodakhe** | **Repo Owner & Developer** | `@vaishnavibodakhe` | Verifiable Credentials, Cryptographic Audit & AI Analytics | `member-4` |

> [!IMPORTANT]
> **Team Leader Duty**: Bhumika Khairnar (`khairnarbhumika34-ship-it`) is responsible for reviewing and approving all Pull Requests before they are merged into `main`.

---

## 🌿 Branching Strategy & Naming Conventions

### Rule 1: Never Commit Directly to `main`
The `main` branch is strictly protected. Direct pushes to `main` are prohibited. All development must occur on dedicated feature branches branched off the latest `main`.

### Rule 2: Standard Branch Format
Feature branches must follow this strict naming format:
```text
feature/<member-name>/<issue-number>-short-title
```

#### Examples:
- `feature/bhumika/1-rotating-qr-interface`
- `feature/siddhi/8-marks-leave-api`
- `feature/gayatri/15-user-classroom-entities`
- `feature/vaishnavi/22-sha256-hash-validation`

---

## 🔄 Development & Pull Request (PR) Workflow

Follow this step-by-step workflow for every assigned task:

### 1. Synchronize with Latest `main`
Before creating a new feature branch, always pull the latest changes:
```bash
git checkout main
git pull origin main
```

### 2. Create Your Feature Branch
```bash
git checkout -b feature/<member-name>/<issue-number>-<short-title>
```

### 3. Implement, Test, and Commit
Write clean, documented code and keep commits focused on the specific issue:
```bash
git status
git add <files-changed>
git commit -m "[Module] Task description (Refs #<issue-number>)"
```

### 4. Push Your Branch
```bash
git push -u origin feature/<member-name>/<issue-number>-<short-title>
```

### 5. Open a Pull Request (PR)
1. Go to the GitHub repository: [IntelliCampus](https://github.com/vaishnavibodakhe/IntelliCampus)
2. Open a Pull Request from your feature branch into `main`.
3. Set the PR title format: `[Module] <Task title> (#<issue-number>)`
4. In the PR description, **link the issue to auto-close it upon merge**:
   ```markdown
   Closes #<issue-number>

   ### Summary of Changes
   - Implemented X feature for Module Y
   - Added validation and error handling for Z
   - Unit tests written and verified passing

   ### Checklist
   - [ ] Code follows formatting standards
   - [ ] No hardcoded secrets or credentials
   - [ ] Tested locally without breaking other views
   ```
5. Assign **Bhumika Khairnar** (`@khairnarbhumika34-ship-it`) as the Reviewer.

### 6. Code Review & Merge
1. The Team Leader reviews the PR diff, checks acceptance criteria, and leaves comments if revisions are required.
2. Once approved, the Team Leader merges using **Squash and Merge** or **Rebase and Merge**.
3. The linked issue will automatically transition to **Closed**.
4. Delete the remote feature branch after merging to keep the repository clean.

---

## ⚠️ Avoiding Merge Conflicts in Shared Files

A few core files are shared across modules:
- `src/components/StudentView.jsx` (Attendance, Academics/Leaves, Notices tabs)
- `src/components/FacultyView.jsx` (Attendance tabs vs Marks tabs)
- `src/utils/cryptoAndGeo.js` (Geofence/QR algorithms vs SHA-256 Audit Chain)
- `backend/src/main/resources/application.yml` (Common server config)

### Conflict Mitigation Guidelines:
1. Only edit functions and JSX blocks strictly belonging to your module's feature area.
2. Coordinate in the team group before modifying shared utility functions.
3. Pull `main` frequently into your local feature branch:
   ```bash
   git fetch origin
   git merge origin/main
   ```

---

## 🧪 Testing & Code Standards

- **React/Frontend**: Clean component architecture, no unused dependencies or leftover console logs.
- **Spring Boot/Backend**: Standard package naming (`com.intellicampus.<module>`), use JPA best practices, validate request DTOs with `@Valid`.
- **Python AI Service**: Fast API schema compliance with Pydantic models.
- Run local tests before opening a PR:
  - Frontend: `npm run build`
  - Backend: `mvn test`
  - AI Service: `pytest`
