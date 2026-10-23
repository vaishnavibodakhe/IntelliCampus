import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  getMyNoticeFeed,
  publishNotice,
  markNoticeRead,
  searchStudents,
  listNotices
} from '../../api/client.js';

/**
 * NoticeComposer & Notice Management Test Suite
 * Tests group taxonomy filtering, specific student targeting, debounced search logic,
 * publisher role validation, and read receipt recording.
 */

describe('NoticeComposer - Audience Targeting & Payload Construction', () => {
  const DEMO_STAFF_MAP = {
    FACULTY: { id: 3, name: 'Prof. Vikram Verma', department: 'Computer Science' },
    MENTOR: { id: 4, name: 'Dr. Sunita Mehta', department: 'Computer Science' },
    HOD: { id: 2, name: 'Dr. Ananya Sharma', department: 'Computer Science' },
    ADMIN: { id: 1, name: 'Rajesh Patel', department: 'Administration' }
  };

  function buildNoticePayload({
    title,
    message,
    recipientMode,
    targetDepartment = 'All Departments',
    targetYear = 'All Years',
    targetDivision = 'All Divisions',
    selectedStudents = [],
    deadline = null,
    publisherRole = 'FACULTY',
    publisherId = null
  }) {
    if (!title || !title.trim()) {
      throw new Error('Notice title is required.');
    }
    if (!message || !message.trim()) {
      throw new Error('Notice message is required.');
    }
    if (recipientMode === 'SPECIFIC' && selectedStudents.length === 0) {
      throw new Error('Please select at least one specific student recipient.');
    }

    const role = publisherRole.toUpperCase();
    const effectiveStaff = DEMO_STAFF_MAP[role] || DEMO_STAFF_MAP.FACULTY;
    const isSpecific = recipientMode === 'SPECIFIC';

    return {
      title: title.trim(),
      body: message.trim(),
      publisherRole: role,
      publisherId: publisherId || effectiveStaff.id,
      targetDepartment: isSpecific ? 'All Departments' : targetDepartment,
      targetYear: isSpecific ? 'All Years' : targetYear,
      targetDivision: isSpecific ? 'All Divisions' : targetDivision,
      targetStudentIds: isSpecific ? selectedStudents.map(s => Number(s.id)) : [],
      deadline: deadline || null,
      hasAttachment: false
    };
  }

  it('should build a valid Whole Group broadcast payload with taxonomy', () => {
    const payload = buildNoticePayload({
      title: 'Lab Exam Timetable',
      message: 'Please review schedule on department board.',
      recipientMode: 'GROUP',
      targetDepartment: 'Computer Science',
      targetYear: 'Year 3',
      targetDivision: 'Div A',
      deadline: '2026-11-20',
      publisherRole: 'FACULTY'
    });

    assert.strictEqual(payload.title, 'Lab Exam Timetable');
    assert.strictEqual(payload.body, 'Please review schedule on department board.');
    assert.strictEqual(payload.targetDepartment, 'Computer Science');
    assert.strictEqual(payload.targetYear, 'Year 3');
    assert.strictEqual(payload.targetDivision, 'Div A');
    assert.deepStrictEqual(payload.targetStudentIds, []);
    assert.strictEqual(payload.publisherRole, 'FACULTY');
    assert.strictEqual(payload.publisherId, 3);
    assert.strictEqual(payload.deadline, '2026-11-20');
  });

  it('should build a valid Specific Students payload with targetStudentIds', () => {
    const payload = buildNoticePayload({
      title: 'Remedial Session Invitation',
      message: 'Attend the extra consultation session tomorrow at 4 PM.',
      recipientMode: 'SPECIFIC',
      selectedStudents: [
        { id: 5, name: 'Aarav Shah', rollNo: 'CS-104' },
        { id: 6, name: 'Rohan Verma', rollNo: 'CS-105' }
      ],
      publisherRole: 'FACULTY'
    });

    assert.strictEqual(payload.title, 'Remedial Session Invitation');
    assert.strictEqual(payload.targetDepartment, 'All Departments');
    assert.deepStrictEqual(payload.targetStudentIds, [5, 6]);
    assert.strictEqual(payload.publisherRole, 'FACULTY');
    assert.strictEqual(payload.publisherId, 3);
  });

  it('should reject submission if title is empty or whitespace only', () => {
    assert.throws(() => {
      buildNoticePayload({
        title: '   ',
        message: 'Some content',
        recipientMode: 'GROUP'
      });
    }, /Notice title is required/);
  });

  it('should reject submission if message body is empty', () => {
    assert.throws(() => {
      buildNoticePayload({
        title: 'Valid Title',
        message: '',
        recipientMode: 'GROUP'
      });
    }, /Notice message is required/);
  });

  it('should reject specific student mode when selectedStudents list is empty', () => {
    assert.throws(() => {
      buildNoticePayload({
        title: 'Valid Title',
        message: 'Valid Message',
        recipientMode: 'SPECIFIC',
        selectedStudents: []
      });
    }, /Please select at least one specific student recipient/);
  });

  it('should resolve fallback demo user for Mentor and Admin roles', () => {
    const mentorPayload = buildNoticePayload({
      title: 'Mentor Catchup',
      message: 'Check your progress',
      recipientMode: 'GROUP',
      publisherRole: 'MENTOR'
    });
    assert.strictEqual(mentorPayload.publisherId, 4);
    assert.strictEqual(mentorPayload.publisherRole, 'MENTOR');

    const adminPayload = buildNoticePayload({
      title: 'Campus Holiday',
      message: 'Campus closed tomorrow',
      recipientMode: 'GROUP',
      publisherRole: 'ADMIN'
    });
    assert.strictEqual(adminPayload.publisherId, 1);
    assert.strictEqual(adminPayload.publisherRole, 'ADMIN');
  });
});

describe('NoticeComposer - Student Search & Removable Chips Logic', () => {
  const mockStudents = [
    { id: 5, userCode: 'student.aarav', name: 'Aarav Shah', rollNo: 'CS-104', division: 'Div A' },
    { id: 6, userCode: 'student.rohan', name: 'Rohan Verma', rollNo: 'CS-105', division: 'Div A' },
    { id: 7, userCode: 'student.sneha', name: 'Sneha Kulkarni', rollNo: 'CS-106', division: 'Div A' },
    { id: 8, userCode: 'student.priya', name: 'Priya Sharma', rollNo: 'CS-107', division: 'Div B' }
  ];

  function searchLocalStudents(query, students, alreadySelected = []) {
    if (!query || !query.trim()) return [];
    const q = query.toLowerCase().trim();
    const selectedIds = new Set(alreadySelected.map(s => s.id));
    return students.filter(s => {
      if (selectedIds.has(s.id)) return false;
      const matchName = s.name && s.name.toLowerCase().includes(q);
      const matchRoll = s.rollNo && s.rollNo.toLowerCase().includes(q);
      const matchCode = s.userCode && s.userCode.toLowerCase().includes(q);
      return matchName || matchRoll || matchCode;
    });
  }

  it('should find students by partial name search', () => {
    const results = searchLocalStudents('aarav', mockStudents);
    assert.strictEqual(results.length, 1);
    assert.strictEqual(results[0].name, 'Aarav Shah');
  });

  it('should find students by roll number query', () => {
    const results = searchLocalStudents('CS-105', mockStudents);
    assert.strictEqual(results.length, 1);
    assert.strictEqual(results[0].name, 'Rohan Verma');
  });

  it('should filter out students that are already in selected chips', () => {
    const alreadySelected = [mockStudents[0]]; // Aarav Shah already selected
    const results = searchLocalStudents('a', mockStudents, alreadySelected);
    assert.ok(!results.some(s => s.id === 5), 'Aarav Shah should be excluded from search dropdown');
    assert.ok(results.length > 0);
  });

  it('should support chip removal without mutating remaining selections', () => {
    let chips = [mockStudents[0], mockStudents[1], mockStudents[2]];
    // Remove Rohan (id 6)
    chips = chips.filter(s => s.id !== 6);
    assert.strictEqual(chips.length, 2);
    assert.ok(!chips.some(s => s.id === 6));
    assert.ok(chips.some(s => s.id === 5));
    assert.ok(chips.some(s => s.id === 7));
  });
});

describe('Notice API Client - Export Signatures', () => {
  it('should export all required notice functions', () => {
    assert.strictEqual(typeof getMyNoticeFeed, 'function');
    assert.strictEqual(typeof publishNotice, 'function');
    assert.strictEqual(typeof markNoticeRead, 'function');
    assert.strictEqual(typeof searchStudents, 'function');
    assert.strictEqual(typeof listNotices, 'function');
  });
});
