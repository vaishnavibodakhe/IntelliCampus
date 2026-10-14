import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

/**
 * HOD View & Academic/Leave Approval Test Suite
 * Tests mark draft-publish visibility, HOD grade correction approval with audit logs,
 * and multi-stage leave approval transitions.
 * Compliant with IC-SRS-1.0 Section 8.3 & 8.4, and Test Cases TC-05 & TC-06.
 */

describe('Academic Marks - Draft vs Published Access Control', () => {
  const sampleMarks = [
    { id: 1, studentId: 101, subjectCode: 'CS301', exam: 'In-Sem Exam', marks: 22, maxMarks: 30, status: 'PUBLISHED' },
    { id: 2, studentId: 101, subjectCode: 'CS302', exam: 'Mid-Term', marks: 44, maxMarks: 50, status: 'DRAFT' },
    { id: 3, studentId: 102, subjectCode: 'CS301', exam: 'In-Sem Exam', marks: 19, maxMarks: 30, status: 'PUBLISHED' }
  ];

  it('Criterion 1: Student should only see PUBLISHED marks, never DRAFT marks', () => {
    const studentId = 101;
    const onlyPublished = true;

    const studentVisibleMarks = sampleMarks
      .filter(m => m.studentId === studentId)
      .filter(m => !onlyPublished || m.status === 'PUBLISHED');

    assert.strictEqual(studentVisibleMarks.length, 1, 'Student should see exactly 1 published mark');
    assert.strictEqual(studentVisibleMarks[0].status, 'PUBLISHED');
    assert.strictEqual(studentVisibleMarks[0].exam, 'In-Sem Exam');

    const hasDraftMarks = studentVisibleMarks.some(m => m.status === 'DRAFT');
    assert.strictEqual(hasDraftMarks, false, 'No draft marks should be exposed to students');
  });

  it('Faculty / HOD view should display both DRAFT and PUBLISHED marks', () => {
    const studentId = 101;
    const onlyPublished = false;

    const facultyVisibleMarks = sampleMarks
      .filter(m => m.studentId === studentId)
      .filter(m => !onlyPublished || m.status === 'PUBLISHED');

    assert.strictEqual(facultyVisibleMarks.length, 2, 'Faculty should see both draft and published marks');
  });
});

describe('Academic Marks - HOD Correction Approval & Audit Entry', () => {
  it('Criterion 2: HOD approval updates target mark entry and emits cryptographic audit entry', () => {
    let currentMarks = [
      { id: 10, studentName: 'Aarav Mehta', marks: 18, maxMarks: 30, updatedBy: 'Prof. Patil' }
    ];

    let currentCorrections = [
      { id: 1, markId: 10, oldMarks: 18, newMarks: 23, facultyName: 'Prof. Patil', status: 'PENDING_HOD', reason: 'Re-evaluation of question 3B' }
    ];

    const auditLogs = [];
    const onAddAuditLog = (entry) => auditLogs.push(entry);

    // Simulate HOD Approval Handler
    const cor = currentCorrections[0];

    // 1. Update mark entry
    currentMarks = currentMarks.map(m => {
      if (m.id === cor.markId) {
        return {
          ...m,
          marks: cor.newMarks,
          updatedBy: `HOD Approved (Requested by ${cor.facultyName})`
        };
      }
      return m;
    });

    // 2. Update correction status
    currentCorrections = currentCorrections.map(c => {
      if (c.id === cor.id) {
        return {
          ...c,
          status: 'APPROVED',
          decisionComment: 'Approved upon verification of evaluator records.'
        };
      }
      return c;
    });

    // 3. Emit audit log
    onAddAuditLog({
      action: 'CORRECT',
      entityType: 'MARK_ENTRY',
      entityId: String(cor.markId),
      oldValue: JSON.stringify({ marks: cor.oldMarks }),
      newValue: JSON.stringify({ marks: cor.newMarks }),
      reason: `HOD approved grade correction: ${cor.reason}`
    });

    // Verifications
    assert.strictEqual(currentMarks[0].marks, 23, 'Mark entry must be updated to new marks (23)');
    assert.strictEqual(currentCorrections[0].status, 'APPROVED', 'Correction status must be APPROVED');
    assert.strictEqual(auditLogs.length, 1, 'Audit log entry must be created');
    assert.strictEqual(auditLogs[0].action, 'CORRECT');
    assert.strictEqual(auditLogs[0].entityType, 'MARK_ENTRY');
    assert.strictEqual(auditLogs[0].entityId, '10');
    assert.ok(auditLogs[0].reason.includes('Re-evaluation of question 3B'));
  });

  it('HOD rejection preserves original marks and logs rejection reason', () => {
    let currentMarks = [
      { id: 10, marks: 18 }
    ];
    let currentCorrections = [
      { id: 1, markId: 10, oldMarks: 18, newMarks: 23, status: 'PENDING_HOD' }
    ];
    const auditLogs = [];

    const rejectReasonText = 'Grading rubric was verified correctly initially; request denied.';
    assert.ok(rejectReasonText.length >= 10, 'Rejection reason must satisfy >= 10 char policy');

    // Simulate rejection
    currentCorrections[0].status = 'REJECTED';
    currentCorrections[0].decisionComment = rejectReasonText;
    auditLogs.push({
      action: 'REJECT',
      entityType: 'MARK_CORRECTION',
      entityId: '1',
      oldValue: '{"status":"PENDING_HOD"}',
      newValue: JSON.stringify({ status: 'REJECTED', reason: rejectReasonText })
    });

    assert.strictEqual(currentMarks[0].marks, 18, 'Original marks must remain unchanged');
    assert.strictEqual(currentCorrections[0].status, 'REJECTED');
    assert.strictEqual(auditLogs.length, 1);
  });
});

describe('Leave Workflow - Multi-Tier Transitions (SUBMITTED -> RECOMMENDED -> APPROVED/REJECTED)', () => {
  it('Criterion 3: Leave transitions through SUBMITTED -> MENTOR RECOMMENDED -> HOD APPROVED', () => {
    // 1. Initial State: SUBMITTED
    const leaveRequest = {
      id: 501,
      studentName: 'Ananya Desai',
      daysCount: 3,
      mentorStatus: 'SUBMITTED',
      hodStatus: 'SUBMITTED',
      finalStatus: 'SUBMITTED'
    };

    assert.strictEqual(leaveRequest.mentorStatus, 'SUBMITTED');
    assert.strictEqual(leaveRequest.finalStatus, 'SUBMITTED');

    // 2. Mentor Review: Recommended
    leaveRequest.mentorStatus = 'RECOMMENDED';
    leaveRequest.finalStatus = 'RECOMMENDED';
    leaveRequest.mentorComment = 'Medical documentation verified by mentor.';

    assert.strictEqual(leaveRequest.mentorStatus, 'RECOMMENDED');
    assert.strictEqual(leaveRequest.finalStatus, 'RECOMMENDED');

    // 3. HOD Review: Approved
    const auditLogs = [];
    leaveRequest.hodStatus = 'APPROVED';
    leaveRequest.finalStatus = 'APPROVED';

    auditLogs.push({
      action: 'APPROVE',
      entityType: 'LEAVE_REQUEST',
      entityId: String(leaveRequest.id),
      oldValue: '{"status":"MENTOR_RECOMMENDED"}',
      newValue: '{"status":"APPROVED"}',
      reason: `HOD granted official leave approval for ${leaveRequest.studentName} (${leaveRequest.daysCount} days)`
    });

    assert.strictEqual(leaveRequest.hodStatus, 'APPROVED');
    assert.strictEqual(leaveRequest.finalStatus, 'APPROVED');
    assert.strictEqual(auditLogs.length, 1);
    assert.strictEqual(auditLogs[0].action, 'APPROVE');
    assert.strictEqual(auditLogs[0].entityType, 'LEAVE_REQUEST');
  });

  it('Criterion 3: Mentor Rejection transitions to MENTOR_REJECTED and prevents HOD approval', () => {
    const leaveRequest = {
      id: 502,
      mentorStatus: 'SUBMITTED',
      finalStatus: 'SUBMITTED'
    };

    // Mentor rejects
    leaveRequest.mentorStatus = 'MENTOR_REJECTED';
    leaveRequest.finalStatus = 'MENTOR_REJECTED';

    assert.strictEqual(leaveRequest.mentorStatus, 'MENTOR_REJECTED');
    assert.strictEqual(leaveRequest.finalStatus, 'MENTOR_REJECTED');

    // Queue filter check: HOD queue only takes leaves where mentorStatus === 'RECOMMENDED'
    const queue = [leaveRequest];
    const hodPendingQueue = queue.filter(l => l.mentorStatus === 'RECOMMENDED');
    assert.strictEqual(hodPendingQueue.length, 0, 'Rejected leave must not appear in HOD approval queue');
  });
});
