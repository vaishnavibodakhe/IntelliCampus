// =====================================================================
// IntelliCampus Seed & Operational Mock State
// Compliant with IC-BRD-1.0, IC-FRD-1.0, and IC-SRS-1.0
// =====================================================================

export const INITIAL_USERS = [
  {
    id: 1,
    userCode: "admin.patel",
    name: "Rajesh Patel",
    email: "admin@intellicampus.edu",
    role: "ADMIN",
    department: "Administration",
    status: "ACTIVE",
    mustChangePassword: false,
  },
  {
    id: 2,
    userCode: "hod.sharma",
    name: "Dr. Ananya Sharma",
    email: "hod.cs@intellicampus.edu",
    role: "HOD",
    department: "Computer Science",
    status: "ACTIVE",
    mustChangePassword: false,
  },
  {
    id: 3,
    userCode: "prof.verma",
    name: "Prof. Vikram Verma",
    email: "v.verma@intellicampus.edu",
    role: "FACULTY",
    department: "Computer Science",
    status: "ACTIVE",
    mustChangePassword: false,
  },
  {
    id: 4,
    userCode: "dr.mehta",
    name: "Dr. Sunita Mehta",
    email: "s.mehta@intellicampus.edu",
    role: "MENTOR",
    department: "Computer Science",
    status: "ACTIVE",
    mustChangePassword: false,
  },
  {
    id: 5,
    userCode: "student.aarav",
    name: "Aarav Shah",
    email: "aarav.cs24@intellicampus.edu",
    role: "STUDENT",
    rollNo: "CS-104",
    division: "Div A",
    year: "Year 3",
    department: "Computer Science",
    mentorId: 4,
    mentorName: "Dr. Sunita Mehta",
    status: "ACTIVE",
    attendanceRate: 84.5,
    backlogs: 0,
    feeStatus: "PAID",
    faceEnrolled: true,
  },
  {
    id: 6,
    userCode: "student.priya",
    name: "Priya Nair",
    email: "priya.cs25@intellicampus.edu",
    role: "STUDENT",
    rollNo: "CS-105",
    division: "Div A",
    year: "Year 3",
    department: "Computer Science",
    mentorId: 4,
    mentorName: "Dr. Sunita Mehta",
    status: "ACTIVE",
    attendanceRate: 64.0,
    backlogs: 2,
    feeStatus: "DUE",
    dueAmount: 18500,
    faceEnrolled: true,
  },
  {
    id: 7,
    userCode: "student.rohan",
    name: "Rohan Deshmukh",
    email: "rohan.cs26@intellicampus.edu",
    role: "STUDENT",
    rollNo: "CS-106",
    division: "Div A",
    year: "Year 3",
    department: "Computer Science",
    mentorId: 4,
    mentorName: "Dr. Sunita Mehta",
    status: "ACTIVE",
    attendanceRate: 91.2,
    backlogs: 0,
    feeStatus: "PAID",
    faceEnrolled: true,
  },
  {
    id: 8,
    userCode: "student.kavya",
    name: "Kavya Iyer",
    email: "kavya.cs27@intellicampus.edu",
    role: "STUDENT",
    rollNo: "CS-107",
    division: "Div A",
    year: "Year 3",
    department: "Computer Science",
    mentorId: 4,
    mentorName: "Dr. Sunita Mehta",
    status: "ACTIVE",
    attendanceRate: 58.2,
    backlogs: 1,
    feeStatus: "PAID",
    faceEnrolled: false,
  }
];

export const INITIAL_ROOMS = [
  {
    id: 7,
    name: "Room 204 - Smart Lecture Hall",
    latitude: 18.52043,
    longitude: 73.85674,
    radiusM: 50,
    capacity: 75,
    department: "Computer Science",
  },
  {
    id: 8,
    name: "Seminar Hall B",
    latitude: 18.52120,
    longitude: 73.85710,
    radiusM: 60,
    capacity: 150,
    department: "General",
  },
  {
    id: 9,
    name: "IoT & Embedded Systems Lab",
    latitude: 18.51990,
    longitude: 73.85590,
    radiusM: 35,
    capacity: 40,
    department: "Computer Science",
  }
];

export const INITIAL_SUBJECTS = [
  { id: 12, code: "CS-302", name: "Machine Learning & AI", facultyId: 3, facultyName: "Prof. Vikram Verma", credits: 4 },
  { id: 13, code: "CS-301", name: "Database Systems Architecture", facultyId: 3, facultyName: "Prof. Vikram Verma", credits: 4 },
  { id: 14, code: "CS-303", name: "Distributed Cloud Computing", facultyId: 2, facultyName: "Dr. Ananya Sharma", credits: 3 },
  { id: 15, code: "CS-304", name: "Information Security & Cryptography", facultyId: 4, facultyName: "Dr. Sunita Mehta", credits: 3 }
];

export const INITIAL_SESSIONS = [
  {
    id: 51,
    subjectId: 12,
    subjectName: "CS-302 Machine Learning & AI",
    division: "Div A",
    roomId: 7,
    roomName: "Room 204",
    facultyId: 3,
    challengeCode: "K7Q2",
    status: "OPEN", // OPEN, PROCESSING, REVIEW, FINALISED
    startedAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
    photoWindowEndsAt: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
    capturedPhoto: null,
    attendanceRecords: [
      {
        studentId: 5,
        studentName: "Aarav Shah",
        rollNo: "CS-104",
        faceScore: 0.94,
        spoofScore: 0.98,
        confirmed: true,
        confirmedAt: new Date().toISOString(),
        finalStatus: "PRESENT",
        statusReason: null
      },
      {
        studentId: 6,
        studentName: "Priya Nair",
        rollNo: "CS-105",
        faceScore: 0.88,
        spoofScore: 0.95,
        confirmed: false,
        confirmedAt: null,
        finalStatus: "MATCHED",
        statusReason: null
      },
      {
        studentId: 7,
        studentName: "Rohan Deshmukh",
        rollNo: "CS-106",
        faceScore: 0.91,
        spoofScore: 0.99,
        confirmed: true,
        confirmedAt: new Date().toISOString(),
        finalStatus: "PRESENT",
        statusReason: null
      },
      {
        studentId: 8,
        studentName: "Kavya Iyer",
        rollNo: "CS-107",
        faceScore: 0.32,
        spoofScore: 0.45,
        confirmed: false,
        confirmedAt: null,
        finalStatus: "REVIEW",
        statusReason: "Low face confidence (0.32) & spoof score alert"
      }
    ]
  }
];

export const INITIAL_MARKS = [
  {
    id: 101,
    studentId: 5,
    studentName: "Aarav Shah",
    rollNo: "CS-104",
    subjectId: 12,
    subjectCode: "CS-302",
    exam: "Mid-Term Examination",
    marks: 27,
    maxMarks: 30,
    status: "PUBLISHED", // DRAFT, PUBLISHED
    updatedBy: "Prof. Vikram Verma",
    updatedAt: "2026-10-04T10:15:00Z"
  },
  {
    id: 102,
    studentId: 6,
    studentName: "Priya Nair",
    rollNo: "CS-105",
    subjectId: 12,
    subjectCode: "CS-302",
    exam: "Mid-Term Examination",
    marks: 14,
    maxMarks: 30,
    status: "PUBLISHED",
    updatedBy: "Prof. Vikram Verma",
    updatedAt: "2026-10-04T10:15:00Z"
  },
  {
    id: 103,
    studentId: 7,
    studentName: "Rohan Deshmukh",
    rollNo: "CS-106",
    subjectId: 12,
    subjectCode: "CS-302",
    exam: "Mid-Term Examination",
    marks: 29,
    maxMarks: 30,
    status: "PUBLISHED",
    updatedBy: "Prof. Vikram Verma",
    updatedAt: "2026-10-04T10:15:00Z"
  },
  {
    id: 104,
    studentId: 5,
    studentName: "Aarav Shah",
    rollNo: "CS-104",
    subjectId: 13,
    subjectCode: "CS-301",
    exam: "Lab Assignment 1",
    marks: 18,
    maxMarks: 20,
    status: "DRAFT",
    updatedBy: "Prof. Vikram Verma",
    updatedAt: "2026-10-07T14:20:00Z"
  }
];

export const INITIAL_MARK_CORRECTIONS = [
  {
    id: 201,
    markId: 102,
    studentName: "Priya Nair",
    rollNo: "CS-105",
    subjectCode: "CS-302",
    exam: "Mid-Term Examination",
    oldMarks: 14,
    newMarks: 21,
    facultyName: "Prof. Vikram Verma",
    reason: "Evaluation error in Section B Question 4 totalling. Verified with answer sheet.",
    status: "PENDING_HOD", // PENDING_HOD, APPROVED, REJECTED
    requestedAt: "2026-10-08T09:30:00Z",
    decisionComment: null
  }
];

export const INITIAL_LEAVE_REQUESTS = [
  {
    id: 301,
    studentId: 6,
    studentName: "Priya Nair",
    rollNo: "CS-105",
    fromDate: "2026-10-12",
    toDate: "2026-10-14",
    daysCount: 3,
    reason: "Severe viral fever and typhoid recuperation. Doctor medical certificate attached.",
    attachmentName: "medical_fit_cert_priya.pdf",
    mentorStatus: "RECOMMENDED", // SUBMITTED, RECOMMENDED, REJECTED
    mentorComment: "Verified medical certificate from campus clinic doctor. Recommended for exemption.",
    hodStatus: "PENDING", // PENDING, APPROVED, REJECTED
    finalStatus: "MENTOR_RECOMMENDED", // SUBMITTED, MENTOR_RECOMMENDED, APPROVED, REJECTED
    submittedAt: "2026-10-07T11:45:00Z"
  },
  {
    id: 302,
    studentId: 5,
    studentName: "Aarav Shah",
    rollNo: "CS-104",
    fromDate: "2026-10-16",
    toDate: "2026-10-17",
    daysCount: 2,
    reason: "Representing college at Inter-University Smart India Hackathon Nationals.",
    attachmentName: "hackathon_invitation_letter.pdf",
    mentorStatus: "SUBMITTED",
    mentorComment: null,
    hodStatus: "PENDING",
    finalStatus: "SUBMITTED",
    submittedAt: "2026-10-09T08:10:00Z"
  }
];

export const INITIAL_NOTICES = [
  {
    id: 401,
    title: "End-Semester Examination Schedule & Backlog Forms Released",
    body: "Final-year and third-year university examination timetable is now published. The deadline to submit backlog forms is November 15, 2026. Hall tickets will be issued only to candidates with at least 75% verified attendance.",
    targetDepartment: "All Departments",
    targetYear: "Year 3 & 4",
    targetDivision: "All Divisions",
    publisherRole: "Admin Office",
    publisherName: "Dr. Ananya Sharma",
    deadline: "2026-11-15",
    publishedAt: "2026-10-07T09:00:00Z",
    readsCount: 142,
    totalTargeted: 180,
    hasAttachment: true,
    fileName: "Exam_Timetable_Nov2026.pdf"
  },
  {
    id: 402,
    title: "Mandatory Face-Enrolment Verification for Campus Gate & Attendance",
    body: "All newly enrolled and transfer students must verify their biometric face embedding consent via the student dashboard by Friday. Photos are not saved; only privacy-preserving AES encrypted mathematical vectors are retained.",
    targetDepartment: "Computer Science",
    targetYear: "All Years",
    targetDivision: "Div A",
    publisherRole: "Faculty",
    publisherName: "Prof. Vikram Verma",
    deadline: "2026-10-15",
    publishedAt: "2026-10-08T14:30:00Z",
    readsCount: 58,
    totalTargeted: 65,
    hasAttachment: false,
    fileName: null
  }
];

export const INITIAL_VERIFIABLE_DOCUMENTS = [
  {
    code: "DOC-2026-CS-8921",
    type: "Provisional Degree Certificate",
    studentName: "Aarav Shah",
    maskedStudentName: "Aa*** Sh**",
    rollNo: "CS-104",
    department: "Computer Science & Engineering",
    issueDate: "2026-10-05",
    status: "VALID", // VALID, REVOKED
    revokedReason: null,
    fileHash: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    gpa: "8.92 / 10.0",
    authorizedSignatory: "Registrar & Controller of Examinations"
  },
  {
    code: "DOC-2026-FEE-4019",
    type: "Tuition Fee Clearance Receipt",
    studentName: "Aarav Shah",
    maskedStudentName: "Aa*** Sh**",
    rollNo: "CS-104",
    department: "Computer Science",
    issueDate: "2026-09-28",
    status: "VALID",
    revokedReason: null,
    fileHash: "9f83c6051a843666ec47a4f34d5ce73a220817f5a9b941e11370183074aac056",
    amountPaid: "$ 64,500.00",
    authorizedSignatory: "Accounts Officer"
  },
  {
    code: "DOC-2025-BC-1102",
    type: "Internship No Objection Certificate",
    studentName: "Kunal Mehra",
    maskedStudentName: "Ku*** Me***",
    rollNo: "CS-082",
    department: "Computer Science",
    issueDate: "2025-11-14",
    status: "REVOKED",
    revokedReason: "Forged faculty signature detected on audit review",
    fileHash: "6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b",
    authorizedSignatory: "HOD Computer Science"
  }
];

export const INITIAL_AUDIT_LOGS = [
  {
    id: 1,
    prevHash: "0000000000000000000000000000000000000000000000000000000000000000",
    entryHash: "a1f5926b4859a7f34c2199f12d8a391e0a9d821374f4b2384a8d423985b1a034",
    actorId: 1,
    actorName: "Rajesh Patel (Admin)",
    action: "CREATE",
    entityType: "SYSTEM_GENESIS",
    entityId: "1",
    oldValue: "{}",
    newValue: '{"genesis":"IntelliCampus initialized"}',
    reason: "Initial system deployment & root cryptographic chain anchor",
    createdAt: "2026-10-01T08:00:00Z"
  },
  {
    id: 2,
    prevHash: "a1f5926b4859a7f34c2199f12d8a391e0a9d821374f4b2384a8d423985b1a034",
    entryHash: "c482bf41d92a106f35b23d9a712f5a892b153920cda49e1903498ffaa92143b8",
    actorId: 3,
    actorName: "Prof. Vikram Verma",
    action: "PUBLISH",
    entityType: "MARK_ENTRY",
    entityId: "101",
    oldValue: '{"status":"DRAFT","marks":27}',
    newValue: '{"status":"PUBLISHED","marks":27}',
    reason: "Mid-Term CS-302 results finalized and verified with external paper evaluator",
    createdAt: "2026-10-04T10:15:00Z"
  },
  {
    id: 3,
    prevHash: "c482bf41d92a106f35b23d9a712f5a892b153920cda49e1903498ffaa92143b8",
    entryHash: "8ef1b739103c812d4a520bf15024d9c720512839a89d44321008bf3942a1097e",
    actorId: 1,
    actorName: "Rajesh Patel (Admin)",
    action: "ISSUE",
    entityType: "VERIFIABLE_DOCUMENT",
    entityId: "DOC-2026-CS-8921",
    oldValue: "{}",
    newValue: '{"code":"DOC-2026-CS-8921","status":"VALID"}',
    reason: "Provisional degree certificate issued upon senate clearance",
    createdAt: "2026-10-05T12:00:00Z"
  }
];

export const INITIAL_KNOWLEDGE_DOCS = [
  {
    id: 1,
    title: "University Exam Regulations & Backlog Rules 2026",
    filename: "Exam_Rules_Circular_2026.pdf",
    uploadedBy: "Admin Office",
    uploadDate: "2026-10-02",
    pages: 14,
    chunksIndexed: 48,
    status: "INDEXED",
    summary: "Rules regarding backlog exam eligibility, supplementary dates, passing grade thresholds, and revaluation fee submission guidelines."
  },
  {
    id: 2,
    title: "Attendance & Detention Policy Handbook v3.2",
    filename: "Attendance_Detention_Policy.pdf",
    uploadedBy: "HOD Office",
    uploadDate: "2026-10-03",
    pages: 8,
    chunksIndexed: 26,
    status: "INDEXED",
    summary: "75% mandatory class attendance rule, medical leave waiver conditions (up to 15% with hospital evidence), and mentor escalation triggers."
  },
  {
    id: 3,
    title: "College Fee Structure & Scholarship Ordinance",
    filename: "Fee_Structure_and_Scholarships.pdf",
    uploadedBy: "Accounts Office",
    uploadDate: "2026-10-04",
    pages: 12,
    chunksIndexed: 34,
    status: "INDEXED",
    summary: "Tuition installment due dates, late fees of $50/day after Oct 25, sports and merit scholarship quotas, and refund rules."
  }
];

export const INITIAL_RISK_STUDENTS = [
  {
    studentId: 6,
    name: "Priya Nair",
    rollNo: "CS-105",
    division: "Div A",
    riskScore: 0.78,
    level: "HIGH",
    trend: "WORSENING",
    attendanceRate: 64.0,
    topFactors: [
      "Attendance dropped 18 points in past 4 weeks (from 82% to 64%)",
      "Internal marks in CS-302 fell to 14/30 (below 50% passing threshold)",
      "Unresolved backlogs in 2 prerequisite courses"
    ],
    mentorName: "Dr. Sunita Mehta",
    actionsTaken: [
      {
        id: 1,
        date: "2026-10-06",
        mentorName: "Dr. Sunita Mehta",
        note: "Met student in office; diagnosed health-related absences. Connected with remedial instructor for CS-302."
      }
    ]
  },
  {
    studentId: 8,
    name: "Kavya Iyer",
    rollNo: "CS-107",
    division: "Div A",
    riskScore: 0.82,
    level: "HIGH",
    trend: "CRITICAL",
    attendanceRate: 58.2,
    topFactors: [
      "Attendance rate critically below threshold at 58.2%",
      "Absence streak: missed 8 consecutive lectures across 3 subjects",
      "Has not yet completed biometric face consent"
    ],
    mentorName: "Dr. Sunita Mehta",
    actionsTaken: []
  },
  {
    studentId: 5,
    name: "Aarav Shah",
    rollNo: "CS-104",
    division: "Div A",
    riskScore: 0.12,
    level: "LOW",
    trend: "STABLE",
    attendanceRate: 84.5,
    topFactors: [
      "Consistent attendance above 80%",
      "Strong internal test score (27/30 in CS-302)",
      "Zero active backlogs"
    ],
    mentorName: "Dr. Sunita Mehta",
    actionsTaken: []
  }
];
