import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

/**
 * AdminView & Notice Management Test Suite
 * Tests bulk CSV student ingestion, notice audience targeting, and read receipts.
 * Compliant with IC-SRS-1.0 Section 8.5 (ADM-01 & ADM-02) and Test Cases TC-09 & TC-10.
 */

describe('Admin Batch Ingestion - CSV Validation & Malformed Row Isolation', () => {
  const REQUIRED_HEADERS = ['usercode', 'name', 'email', 'department', 'rollno'];

  function parseAndValidateCsv(csvText) {
    const lines = csvText.trim().split('\n').map(l => l.trim()).filter(Boolean);
    if (lines.length === 0) return { validRows: [], errors: ['CSV is empty'] };

    const headerLine = lines[0].toLowerCase();
    const headers = headerLine.split(',').map(h => h.trim());

    for (const req of REQUIRED_HEADERS) {
      if (!headers.includes(req)) {
        return { validRows: [], errors: [`Missing mandatory header: ${req}`] };
      }
    }

    const validRows = [];
    const errors = [];
    const seenEmails = new Set();
    const seenCodes = new Set();

    for (let i = 1; i < lines.length; i++) {
      const parts = lines[i].split(',').map(p => p.trim());
      const row = {};
      headers.forEach((h, idx) => { row[h] = parts[idx]; });

      if (!row.email || !row.email.includes('@')) {
        errors.push(`Line ${i + 1}: Malformed or missing email address`);
        continue;
      }
      if (seenEmails.has(row.email)) {
        errors.push(`Line ${i + 1}: Duplicate email '${row.email}'`);
        continue;
      }
      if (seenCodes.has(row.usercode)) {
        errors.push(`Line ${i + 1}: Duplicate userCode '${row.usercode}'`);
        continue;
      }

      seenEmails.add(row.email);
      seenCodes.add(row.usercode);
      validRows.push(row);
    }

    return { validRows, errors };
  }

  it('Criterion 1: Successfully imports valid rows while isolating malformed rows with line numbers', () => {
    const csvContent = `
usercode,name,email,department,rollno
STU-001,Aarav Mehta,aarav@intellicampus.edu,Computer Science,2024-CS-001
STU-002,Priya Sharma,invalid-email-address,Computer Science,2024-CS-002
STU-003,Rohan Kulkarni,rohan@intellicampus.edu,Computer Science,2024-CS-003
    `.trim();

    const result = parseAndValidateCsv(csvContent);

    assert.strictEqual(result.validRows.length, 2, '2 valid rows should be successfully imported');
    assert.strictEqual(result.errors.length, 1, '1 malformed row should be isolated');
    assert.ok(result.errors[0].includes('Line 3'), 'Error should report exact line number 3');
    assert.ok(result.errors[0].includes('Malformed or missing email'));
  });

  it('Criterion 1: Detects duplicate email inside CSV batch and isolates the collision', () => {
    const csvContent = `
usercode,name,email,department,rollno
STU-010,Kavita Rao,kavita@intellicampus.edu,Computer Science,2024-CS-010
STU-011,Nikhil Verma,kavita@intellicampus.edu,Computer Science,2024-CS-011
    `.trim();

    const result = parseAndValidateCsv(csvContent);

    assert.strictEqual(result.validRows.length, 1);
    assert.strictEqual(result.errors.length, 1);
    assert.ok(result.errors[0].includes('Duplicate email'));
  });

  it('Rejects CSV when mandatory headers are missing', () => {
    const invalidCsv = `
usercode,name,department
STU-001,Aarav,Computer Science
    `.trim();

    const result = parseAndValidateCsv(invalidCsv);
    assert.strictEqual(result.validRows.length, 0);
    assert.ok(result.errors[0].includes('Missing mandatory header'));
  });
});

describe('Notice Audience Targeting & Filtering', () => {
  const notices = [
    {
      id: 1,
      title: 'College Annual Sports Day',
      targetDepartment: 'All Departments',
      targetYear: 'All Years',
      targetDivision: 'All Divisions'
    },
    {
      id: 2,
      title: 'TE Computer Science Mini Project Review',
      targetDepartment: 'Computer Science',
      targetYear: 'Year 3',
      targetDivision: 'All Divisions'
    },
    {
      id: 3,
      title: 'Mechanical Workshop Safety Protocol',
      targetDepartment: 'Mechanical Engineering',
      targetYear: 'Year 2',
      targetDivision: 'All Divisions'
    }
  ];

  function filterNoticesForStudent(student, allNotices) {
    return allNotices.filter(notice => {
      const matchDept = notice.targetDepartment === 'All Departments' || notice.targetDepartment === student.department;
      const matchYear = notice.targetYear === 'All Years' || notice.targetYear === student.year;
      const matchDiv = notice.targetDivision === 'All Divisions' || notice.targetDivision === student.division;
      return matchDept && matchYear && matchDiv;
    });
  }

  it('Criterion 2: Student only receives notices matching their department, year, and campus-wide broadcasts', () => {
    const csThirdYearStudent = {
      name: 'Aarav Mehta',
      department: 'Computer Science',
      year: 'Year 3',
      division: 'TE-CS-A'
    };

    const feed = filterNoticesForStudent(csThirdYearStudent, notices);

    assert.strictEqual(feed.length, 2, 'Student should see sports day and CS mini-project notices');
    assert.ok(feed.some(n => n.title.includes('Sports Day')));
    assert.ok(feed.some(n => n.title.includes('TE Computer Science')));
    assert.ok(!feed.some(n => n.title.includes('Mechanical Workshop')), 'Mechanical notice must be excluded');
  });

  it('Criterion 2: Student from different department does not see departmental notices for other branches', () => {
    const mechStudent = {
      name: 'Vikram Shinde',
      department: 'Mechanical Engineering',
      year: 'Year 2',
      division: 'SE-MECH-B'
    };

    const feed = filterNoticesForStudent(mechStudent, notices);

    assert.strictEqual(feed.length, 2, 'Mech student should see sports day and mech safety notices');
    assert.ok(!feed.some(n => n.title.includes('TE Computer Science')));
  });
});

describe('Notice Read Receipts', () => {
  it('Criterion 3: Generates read receipt when student accesses notice and prevents duplicate counts', () => {
    const readReceipts = new Set(); // Stores "noticeId:userId"

    function recordReadReceipt(noticeId, userId) {
      const key = `${noticeId}:${userId}`;
      if (readReceipts.has(key)) {
        return false; // Already read
      }
      readReceipts.add(key);
      return true; // First time read
    }

    const noticeId = 101;
    const studentId = 205;

    // First access
    const firstRead = recordReadReceipt(noticeId, studentId);
    assert.strictEqual(firstRead, true, 'First access should record a new read receipt');
    assert.strictEqual(readReceipts.size, 1);

    // Duplicate access
    const secondRead = recordReadReceipt(noticeId, studentId);
    assert.strictEqual(secondRead, false, 'Duplicate access should not count again');
    assert.strictEqual(readReceipts.size, 1);
  });
});
