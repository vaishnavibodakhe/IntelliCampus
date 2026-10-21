import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

/**
 * NotificationBell & Student Feed Management Test Suite
 * Tests unread badge count calculation, feed audience isolation,
 * marking notices as read, API failure resilience, and publisher fallbacks.
 */

describe('NotificationBell - Unread Count Calculation', () => {
  function computeUnreadCount(notices) {
    if (!Array.isArray(notices)) return 0;
    return notices.filter(n => !n.read).length;
  }

  it('should return 0 when notices list is empty', () => {
    assert.strictEqual(computeUnreadCount([]), 0);
  });

  it('should return correct unread count when all notices are unread', () => {
    const list = [
      { id: 1, title: 'Notice 1', read: false },
      { id: 2, title: 'Notice 2', read: false },
      { id: 3, title: 'Notice 3' } // undefined read is treated as unread
    ];
    assert.strictEqual(computeUnreadCount(list), 3);
  });

  it('should return 0 when all notices are marked as read', () => {
    const list = [
      { id: 1, title: 'Notice 1', read: true },
      { id: 2, title: 'Notice 2', read: true }
    ];
    assert.strictEqual(computeUnreadCount(list), 0);
  });

  it('should correctly calculate mixed read and unread notices', () => {
    const list = [
      { id: 1, title: 'Notice 1', read: true },
      { id: 2, title: 'Notice 2', read: false },
      { id: 3, title: 'Notice 3', read: true },
      { id: 4, title: 'Notice 4', read: false }
    ];
    assert.strictEqual(computeUnreadCount(list), 2);
  });
});

describe('NotificationBell - Feed Audience Isolation', () => {
  function filterNoticesForStudent(notices, student) {
    if (!student || !Array.isArray(notices)) return [];
    return notices.filter(n => {
      // 1. Direct specific student targeting
      if (n.targetStudentIds && n.targetStudentIds.length > 0) {
        return n.targetStudentIds.includes(student.id) || n.targetStudentIds.includes(Number(student.id));
      }
      // 2. Taxonomy matching
      const matchDept = !n.targetDepartment || n.targetDepartment === 'All Departments' || n.targetDepartment === student.department;
      const matchYear = !n.targetYear || n.targetYear === 'All Years' || n.targetYear === student.year;
      const matchDiv = !n.targetDivision || n.targetDivision === 'All Divisions' || n.targetDivision === student.division;
      return matchDept && matchYear && matchDiv;
    });
  }

  const studentA = {
    id: 5,
    name: 'Aarav Shah',
    department: 'Computer Science',
    year: 'Year 3',
    division: 'Div A'
  };

  const studentB = {
    id: 9,
    name: 'Neha Kulkarni',
    department: 'Information Technology',
    year: 'Year 2',
    division: 'Div B'
  };

  const allNotices = [
    {
      id: 1,
      title: 'Campus Wide Holiday',
      targetDepartment: 'All Departments',
      targetYear: 'All Years',
      targetDivision: 'All Divisions'
    },
    {
      id: 2,
      title: 'CS Year 3 Project Review',
      targetDepartment: 'Computer Science',
      targetYear: 'Year 3',
      targetDivision: 'All Divisions'
    },
    {
      id: 3,
      title: 'IT Year 2 Workshop',
      targetDepartment: 'Information Technology',
      targetYear: 'Year 2',
      targetDivision: 'Div B'
    },
    {
      id: 4,
      title: 'Personal Advisory for Aarav',
      targetStudentIds: [5]
    },
    {
      id: 5,
      title: 'Personal Advisory for Student 99',
      targetStudentIds: [99]
    }
  ];

  it('should isolate feed for Student A (Computer Science, Year 3)', () => {
    const feedA = filterNoticesForStudent(allNotices, studentA);
    const ids = feedA.map(n => n.id);

    assert.ok(ids.includes(1), 'Should include campus wide notice');
    assert.ok(ids.includes(2), 'Should include CS Year 3 notice');
    assert.ok(ids.includes(4), 'Should include personal advisory addressed to student A');
    assert.ok(!ids.includes(3), 'Must NOT include IT department notice');
    assert.ok(!ids.includes(5), 'Must NOT include notice targeted to another student ID');
    assert.strictEqual(feedA.length, 3);
  });

  it('should isolate feed for Student B (Information Technology, Year 2)', () => {
    const feedB = filterNoticesForStudent(allNotices, studentB);
    const ids = feedB.map(n => n.id);

    assert.ok(ids.includes(1), 'Should include campus wide notice');
    assert.ok(ids.includes(3), 'Should include IT Year 2 Div B notice');
    assert.ok(!ids.includes(2), 'Must NOT include CS department notice');
    assert.ok(!ids.includes(4), 'Must NOT include student A personal notice');
    assert.strictEqual(feedB.length, 2);
  });
});

describe('NotificationBell - Marking Notices Read & Backend Persistence', () => {
  async function markNoticeAsReadLocally(notices, noticeId, apiMock) {
    // 1. Invoke backend API
    await apiMock(noticeId);

    // 2. Only on success, update state
    return notices.map(n => {
      if (n.id === noticeId) {
        return { ...n, read: true, readsCount: (n.readsCount || 0) + 1 };
      }
      return n;
    });
  }

  it('should mark notice as read and increment readsCount on API success', async () => {
    const initialNotices = [
      { id: 101, title: 'Circular A', read: false, readsCount: 5 },
      { id: 102, title: 'Circular B', read: false, readsCount: 0 }
    ];

    let apiCalledWith = null;
    const mockSuccessApi = async (id) => {
      apiCalledWith = id;
      return { success: true };
    };

    const updated = await markNoticeAsReadLocally(initialNotices, 101, mockSuccessApi);

    assert.strictEqual(apiCalledWith, 101);
    const notice101 = updated.find(n => n.id === 101);
    assert.strictEqual(notice101.read, true);
    assert.strictEqual(notice101.readsCount, 6);

    const notice102 = updated.find(n => n.id === 102);
    assert.strictEqual(notice102.read, false);
    assert.strictEqual(notice102.readsCount, 0);
  });

  it('should NOT falsely mark notice as read if backend API fails', async () => {
    const initialNotices = [
      { id: 101, title: 'Circular A', read: false, readsCount: 5 }
    ];

    const mockFailingApi = async (id) => {
      throw new Error(`HTTP 500: Server error marking notice ${id}`);
    };

    let errorThrown = false;
    let resultingNotices = initialNotices;

    try {
      resultingNotices = await markNoticeAsReadLocally(initialNotices, 101, mockFailingApi);
    } catch (err) {
      errorThrown = true;
      assert.match(err.message, /500/);
    }

    assert.strictEqual(errorThrown, true, 'Error must be thrown to caller');
    // Notice in state must remain unread
    assert.strictEqual(resultingNotices[0].read, false);
    assert.strictEqual(resultingNotices[0].readsCount, 5);
  });
});

describe('NotificationBell - Publisher Display Safe Fallbacks', () => {
  function getSafePublisherName(notice) {
    if (notice?.publisherName && notice.publisherName.trim()) {
      return notice.publisherName.trim();
    }
    if (notice?.publisherRole && notice.publisherRole.trim()) {
      return notice.publisherRole.trim();
    }
    return 'Campus Administration';
  }

  it('should return publisherName when explicitly provided', () => {
    const notice = { publisherName: 'Dr. Ananya Sharma', publisherRole: 'HOD' };
    assert.strictEqual(getSafePublisherName(notice), 'Dr. Ananya Sharma');
  });

  it('should fall back to publisherRole when publisherName is missing', () => {
    const notice = { publisherRole: 'HOD Office' };
    assert.strictEqual(getSafePublisherName(notice), 'HOD Office');
  });

  it('should fall back to Campus Administration when both are empty or null', () => {
    assert.strictEqual(getSafePublisherName({}), 'Campus Administration');
    assert.strictEqual(getSafePublisherName({ publisherName: '', publisherRole: '' }), 'Campus Administration');
    assert.strictEqual(getSafePublisherName(null), 'Campus Administration');
  });
});
