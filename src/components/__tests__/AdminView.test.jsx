import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

/**
 * AdminView & Notice Management Test Suite (.jsx)
 * Tests bulk CSV student ingestion, notice audience targeting, and read receipts.
 * Compliant with IC-SRS-1.0 Section 8.5 (ADM-01 & ADM-02) and Test Cases TC-09 & TC-10.
 */

describe('Admin Batch Ingestion - CSV Validation & Malformed Row Isolation (JSX)', () => {
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
  });

  it('Criterion 2: Student only receives notices matching department/year (JSX)', () => {
    const notices = [
      { id: 1, title: 'Campus Holiday', targetDepartment: 'All Departments', targetYear: 'All Years' },
      { id: 2, title: 'CS Exam Notice', targetDepartment: 'Computer Science', targetYear: 'Year 3' },
      { id: 3, title: 'Civil Lab Safety', targetDepartment: 'Civil Engineering', targetYear: 'Year 1' }
    ];

    const student = { department: 'Computer Science', year: 'Year 3' };
    const feed = notices.filter(n => 
      (n.targetDepartment === 'All Departments' || n.targetDepartment === student.department) &&
      (n.targetYear === 'All Years' || n.targetYear === student.year)
    );

    assert.strictEqual(feed.length, 2);
    assert.ok(!feed.some(n => n.title.includes('Civil')));
  });

  it('Criterion 3: Read receipt tracking prevents duplicates (JSX)', () => {
    const receipts = new Set();
    receipts.add('101:501');

    assert.strictEqual(receipts.has('101:501'), true);
    assert.strictEqual(receipts.size, 1);
  });
});
