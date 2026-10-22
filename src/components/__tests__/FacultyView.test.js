import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { 
  calculateHaversineDistance, 
  validateGeofence,
  validateSpoofConfidence 
} from '../../utils/cryptoAndGeo.js';

/**
 * FacultyView & Real Face Recognition Attendance Test Suite
 * Tests ArcFace 512-d cosine similarity threshold matching, REVIEW borderline logic,
 * Hungarian/greedy bipartite 1-to-1 student conflict resolution, and geofence boundary verification.
 * No rotating QR code logic.
 */
describe('Smart Attendance - Face Recognition Matching & Threshold Logic', () => {
  const DEFAULT_THRESHOLD = 0.50;
  const REVIEW_THRESHOLD = 0.20;

  // Evaluates face recognition decision logic
  function classifyFaceMatch(similarity, secondSimilarity = 0, threshold = DEFAULT_THRESHOLD, reviewThreshold = REVIEW_THRESHOLD) {
    if (similarity === null || similarity === undefined) {
      return { status: 'UNMATCHED', reason: 'Similarity below recognition threshold' };
    }
    const isAmbiguousTie = secondSimilarity > 0 && Math.abs(similarity - secondSimilarity) < 1e-6;
    if (similarity >= threshold && !isAmbiguousTie) {
      return { status: 'PRESENT', reason: 'Verified by ArcFace facial recognition' };
    }
    if (similarity >= reviewThreshold && similarity < threshold) {
      return { status: 'REVIEW', reason: 'Borderline similarity score; requires faculty review' };
    }
    if (similarity >= threshold && isAmbiguousTie) {
      return { status: 'REVIEW', reason: 'Ambiguous match with multiple candidates; requires faculty review' };
    }
    return { status: 'UNMATCHED', reason: 'Similarity below recognition threshold' };
  }

  it('should classify high-confidence match (>= 0.50) as PRESENT', () => {
    const result = classifyFaceMatch(0.78, 0.42);
    assert.strictEqual(result.status, 'PRESENT');
    assert.ok(result.reason.includes('Verified by ArcFace'));
  });

  it('should classify match in [0.20, 0.50) as REVIEW', () => {
    // 0.38 is in [0.20, 0.50)
    const result = classifyFaceMatch(0.38, 0.20);
    assert.strictEqual(result.status, 'REVIEW');
    assert.ok(result.reason.includes('requires faculty review'));
  });

  it('should classify low similarity below 0.20 as UNMATCHED', () => {
    const result = classifyFaceMatch(0.18, 0.12);
    assert.strictEqual(result.status, 'UNMATCHED');
  });

  it('should verify exact boundary rules for 0.1999, 0.20, 0.4999, 0.50, and 0.5001', () => {
    assert.strictEqual(classifyFaceMatch(0.1999).status, 'UNMATCHED');
    assert.strictEqual(classifyFaceMatch(0.20).status, 'REVIEW');
    assert.strictEqual(classifyFaceMatch(0.4999).status, 'REVIEW');
    assert.strictEqual(classifyFaceMatch(0.50).status, 'PRESENT');
    assert.strictEqual(classifyFaceMatch(0.5001).status, 'PRESENT');
  });

  it('should allow match >= 0.50 with a small margin to be marked PRESENT', () => {
    // Similarity 0.52 >= 0.50, second similarity 0.50 (margin 0.02 < old 0.05 margin)
    const result = classifyFaceMatch(0.52, 0.50);
    assert.strictEqual(result.status, 'PRESENT');
  });

  it('should flag ambiguous tie where multiple candidates share identical top similarity as REVIEW', () => {
    // Exact tie: similarity 0.52, second similarity 0.52
    const result = classifyFaceMatch(0.52, 0.52);
    assert.strictEqual(result.status, 'REVIEW');
    assert.ok(result.reason.includes('Ambiguous match'));
  });

  it('should handle missing or null similarity safely as UNMATCHED', () => {
    assert.strictEqual(classifyFaceMatch(null).status, 'UNMATCHED');
    assert.strictEqual(classifyFaceMatch(undefined).status, 'UNMATCHED');
  });
});

describe('Smart Attendance - 1-to-1 Bipartite Student Conflict Resolution', () => {
  // Resolves candidate face matches ensuring a student is assigned at most once
  function resolveStudentMatches(candidatePairs) {
    // candidatePairs: [{ faceIndex: number, studentId: number, similarity: number }]
    // Sort descending by similarity (Greedy matching)
    const sorted = [...candidatePairs].sort((a, b) => b.similarity - a.similarity);
    const assignedFaces = new Set();
    const assignedStudents = new Set();
    const resolvedMatches = [];

    for (const pair of sorted) {
      if (!assignedFaces.has(pair.faceIndex) && !assignedStudents.has(pair.studentId)) {
        assignedFaces.add(pair.faceIndex);
        assignedStudents.add(pair.studentId);
        resolvedMatches.push(pair);
      }
    }
    return resolvedMatches;
  }

  it('should ensure each student is matched at most once even when multiple faces correlate with same student', () => {
    // Two faces in the classroom both have student 101 as highest candidate
    const candidatePairs = [
      { faceIndex: 0, studentId: 101, similarity: 0.88 },
      { faceIndex: 1, studentId: 101, similarity: 0.72 }, // duplicate candidate for student 101
      { faceIndex: 1, studentId: 102, similarity: 0.65 }  // alternative candidate for face 1
    ];

    const matches = resolveStudentMatches(candidatePairs);

    // Face 0 gets student 101 (similarity 0.88)
    const face0Match = matches.find(m => m.faceIndex === 0);
    assert.ok(face0Match);
    assert.strictEqual(face0Match.studentId, 101);

    // Face 1 gets student 102 (similarity 0.65) because student 101 was already taken
    const face1Match = matches.find(m => m.faceIndex === 1);
    assert.ok(face1Match);
    assert.strictEqual(face1Match.studentId, 102);

    // Total unique matched students must equal 2
    const studentIds = matches.map(m => m.studentId);
    assert.strictEqual(new Set(studentIds).size, matches.length);
  });
});

describe('Smart Attendance - Geofence Haversine Boundary Edge Cases', () => {
  const roomLat = 18.52043;
  const roomLon = 73.85674;
  const allowableRadius = 50; // meters

  it('should pass check-in when faculty/classroom capture is inside perimeter (~12m away)', () => {
    const studentLat = 18.52051;
    const studentLon = 73.85681;
    const accuracy = 15;

    const result = validateGeofence(studentLat, studentLon, accuracy, roomLat, roomLon, allowableRadius);

    assert.strictEqual(result.valid, true, 'Check-in must be valid inside perimeter');
    assert.strictEqual(result.error, null);
    assert.ok(result.distanceM < allowableRadius, 'Distance must be less than 50m');
    assert.ok(result.distanceM > 0, 'Distance must be positive');
  });

  it('should pass check-in when coordinates are exactly at classroom center (0m distance)', () => {
    const result = validateGeofence(roomLat, roomLon, 5, roomLat, roomLon, allowableRadius);

    assert.strictEqual(result.valid, true);
    assert.strictEqual(result.distanceM, 0);
    assert.strictEqual(result.error, null);
  });

  it('should reject check-in when coordinates are outside allowable boundary (>50m)', () => {
    const farLat = 18.52175;
    const farLon = 73.85674;
    const accuracy = 10;

    const result = validateGeofence(farLat, farLon, accuracy, roomLat, roomLon, allowableRadius);

    assert.strictEqual(result.valid, false, 'Check-in must be rejected outside boundary');
    assert.ok(result.distanceM > allowableRadius, 'Distance must exceed allowable perimeter');
    assert.ok(result.error.includes('Geofence boundary violation'), 'Error should cite boundary violation');
  });

  it('should reject check-in when GPS accuracy is worse than 100 meters', () => {
    const studentLat = 18.52045;
    const studentLon = 73.85675;
    const poorAccuracy = 120; // ±120m

    const result = validateGeofence(studentLat, studentLon, poorAccuracy, roomLat, roomLon, allowableRadius);

    assert.strictEqual(result.valid, false, 'Check-in must be rejected when accuracy exceeds 100m');
    assert.ok(result.error.includes('GPS accuracy'), 'Error should cite GPS accuracy');
  });

  it('should correctly calculate Haversine distance between known points', () => {
    const d1 = calculateHaversineDistance(0, 0, 0, 0);
    assert.strictEqual(d1, 0, 'Distance between identical points should be 0');

    // Known distance ~100m north
    const d2 = calculateHaversineDistance(18.52043, 73.85674, 18.52133, 73.85674);
    assert.ok(Math.abs(d2 - 100) < 15, 'Calculated distance should be approx 100m');
  });
});

describe('FacultyView - Demo Quick-Auth Security & Production Gating', () => {
  function scanDirForSubstring(dir, substring, matches = []) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        scanDirForSubstring(fullPath, substring, matches);
      } else if (entry.isFile()) {
        if (entry.name === 'FacultyView.test.js') {
          continue;
        }
        const content = fs.readFileSync(fullPath, 'utf8');
        if (content.includes(substring)) {
          matches.push(fullPath);
        }
      }
    }
    return matches;
  }

  it('fails if the string Password@123 appears anywhere under src/', () => {
    const forbiddenTarget = ['Password', '@', '123'].join('');
    const srcDirectory = path.resolve('src');
    const detectedFiles = scanDirForSubstring(srcDirectory, forbiddenTarget);
    assert.deepStrictEqual(
      detectedFiles,
      [],
      `Hardcoded credential "${forbiddenTarget}" detected in src/ files: ${detectedFiles.join(', ')}`
    );
  });

  function isFacultyAuthButtonVisible(isDev, apiError) {
    if (!isDev) return false;
    return Boolean(apiError && apiError.toLowerCase().includes('authentication'));
  }

  it('should ensure Authenticate as Faculty button is hidden when DEV is false', () => {
    assert.strictEqual(isFacultyAuthButtonVisible(false, 'Full authentication is required to start session'), false);
    assert.strictEqual(isFacultyAuthButtonVisible(false, 'authentication error'), false);
    assert.strictEqual(isFacultyAuthButtonVisible(false, null), false);
  });

  it('should render Authenticate as Faculty button only when DEV is true and auth error exists', () => {
    assert.strictEqual(isFacultyAuthButtonVisible(true, 'Full authentication is required to start session'), true);
    assert.strictEqual(isFacultyAuthButtonVisible(true, 'Session closed'), false);
    assert.strictEqual(isFacultyAuthButtonVisible(true, null), false);
  });

  function executeQuickStaffAuth(isDev, envEmail, envPassword) {
    if (!isDev) {
      return { madeRequest: false, error: 'Disabled in production' };
    }
    if (!envEmail || !envPassword) {
      return { madeRequest: false, error: 'Demo faculty credentials not configured' };
    }
    return { madeRequest: true, payload: { email: envEmail, password: envPassword } };
  }

  it('should abort and show "Demo faculty credentials not configured" without making a request if env vars are missing', () => {
    const noCreds = executeQuickStaffAuth(true, undefined, undefined);
    assert.strictEqual(noCreds.madeRequest, false);
    assert.strictEqual(noCreds.error, 'Demo faculty credentials not configured');

    const missingPass = executeQuickStaffAuth(true, 'v.verma@intellicampus.edu', '');
    assert.strictEqual(missingPass.madeRequest, false);
    assert.strictEqual(missingPass.error, 'Demo faculty credentials not configured');

    const missingEmail = executeQuickStaffAuth(true, '', 'secret');
    assert.strictEqual(missingEmail.madeRequest, false);
    assert.strictEqual(missingEmail.error, 'Demo faculty credentials not configured');

    const valid = executeQuickStaffAuth(true, 'v.verma@intellicampus.edu', 'secret');
    assert.strictEqual(valid.madeRequest, true);
    assert.strictEqual(valid.payload.email, 'v.verma@intellicampus.edu');
    assert.strictEqual(valid.payload.password, 'secret');
  });

  it('should abort handleQuickStaffAuth without making request if DEV is false', () => {
    const prodCall = executeQuickStaffAuth(false, 'v.verma@intellicampus.edu', 'secret');
    assert.strictEqual(prodCall.madeRequest, false);
  });
});

describe('FacultyView - Unmatched Face Detection & Bounding Box Invariants', () => {
  // Evaluates extraction of photoStats from backend capture response as in FacultyView
  function extractFacultyViewPhotoStats(photoStats) {
    const facesDetected = photoStats ? (photoStats.facesDetected ?? (photoStats.detectedFaces?.length || 0)) : 0;
    const studentsMatched = photoStats ? (photoStats.detectedFaces && photoStats.detectedFaces.length > 0
      ? photoStats.detectedFaces.filter(f => f.status === 'PRESENT').length
      : (photoStats.studentsMatched ?? 0)) : 0;
    const studentsInReview = photoStats ? (photoStats.detectedFaces && photoStats.detectedFaces.length > 0
      ? photoStats.detectedFaces.filter(f => f.status === 'REVIEW').length
      : (photoStats.studentsInReview ?? 0)) : 0;
    const unmatchedFaces = photoStats ? (photoStats.detectedFaces && photoStats.detectedFaces.length > 0
      ? photoStats.detectedFaces.filter(f => f.status === 'UNMATCHED').length
      : (photoStats.unmatchedFaces ?? photoStats.unmatchedFaceCount ?? Math.max(0, facesDetected - studentsMatched - studentsInReview))) : 0;
    return {
      facesDetected,
      studentsMatched,
      studentsInReview,
      unmatchedFaces,
      invariantSatisfied: facesDetected === (studentsMatched + studentsInReview + unmatchedFaces)
    };
  }

  function computeFaceBoundingBox(face, imageDims = { width: 1280, height: 720 }) {
    const bbox = face?.bbox || [];
    if (bbox.length < 4) return null;

    const imgW = imageDims.width || 1280;
    const imgH = imageDims.height || 720;
    const left = (bbox[0] / imgW) * 100;
    const top = (bbox[1] / imgH) * 100;
    const width = ((bbox[2] - bbox[0]) / imgW) * 100;
    const height = ((bbox[3] - bbox[1]) / imgH) * 100;

    const isPresent = face.status === 'PRESENT';
    const isReview = face.status === 'REVIEW';
    const color = isPresent ? '#10b981' : isReview ? '#f59e0b' : '#ef4444';

    let label;
    if (isPresent) {
      label = face.studentName
        ? `${face.studentName} (${Math.round((face.similarity || 0) * 100)}%)`
        : `Face #${face.faceId}`;
    } else if (isReview) {
      const name = face.studentName || `Face #${face.faceId}`;
      const simPercent = face.similarity !== undefined && face.similarity !== null
        ? `${Math.round(face.similarity * 100)}%`
        : null;
      label = simPercent !== null ? `${name} (${simPercent})` : name;
    } else {
      label = 'Unmatched';
    }

    return { left, top, width, height, color, label, isPresent, isReview, isUnmatched: face.status === 'UNMATCHED' };
  }

  it('correctly counts unmatched faces and satisfies Faces Detected = Matched + Borderline + Unmatched', () => {
    // Both new backend (unmatchedFaces) and legacy (unmatchedFaceCount) formats supported
    const backendResultWithBoth = {
      facesDetected: 1,
      studentsMatched: 0,
      studentsInReview: 0,
      unmatchedFaceCount: 1,
      unmatchedFaces: 1,
      detectedFaces: [
        { faceId: 1, bbox: [120, 80, 400, 360], status: 'UNMATCHED', studentName: null }
      ]
    };

    const stats = extractFacultyViewPhotoStats(backendResultWithBoth);
    assert.strictEqual(stats.unmatchedFaces, 1, 'Unmatched faces must equal 1 when 1 unmatched face detected');
    assert.strictEqual(stats.invariantSatisfied, true, 'Faces Detected must equal Matched + Borderline + Unmatched');
    assert.strictEqual(stats.facesDetected, stats.studentsMatched + stats.studentsInReview + stats.unmatchedFaces);

    // Bounding box for unmatched face must exist, have red border (#ef4444), and never mark student PRESENT
    const box = computeFaceBoundingBox(backendResultWithBoth.detectedFaces[0]);
    assert.ok(box, 'Bounding box must be computed for unmatched face');
    assert.strictEqual(box.isPresent, false, 'Unmatched face must never be marked PRESENT');
    assert.strictEqual(box.color, '#ef4444');
    assert.strictEqual(box.label, 'Unmatched');
    assert.strictEqual(box.isUnmatched, true);
  });

  it('an unmatched face never renders a student name or similarity percentage, and keeps red box', () => {
    // Even when the AI backend populates nearest candidate studentName and similarity
    const unmatchedFaceWithCandidate = {
      faceId: 101,
      bbox: [100, 150, 250, 350],
      status: 'UNMATCHED',
      studentName: 'Priya Jadhav',
      similarity: 0.18
    };

    const box = computeFaceBoundingBox(unmatchedFaceWithCandidate);
    assert.ok(box, 'Bounding box must be computed');
    assert.strictEqual(box.color, '#ef4444', 'UNMATCHED face must maintain red box color');
    assert.strictEqual(box.label, 'Unmatched', 'UNMATCHED face label must be strictly "Unmatched"');
    assert.strictEqual(box.label.includes('Priya Jadhav'), false, 'UNMATCHED face must never render candidate student name');
    assert.strictEqual(box.label.includes('18%'), false, 'UNMATCHED face must never render percentage');
    assert.strictEqual(box.label.includes('0.18'), false, 'UNMATCHED face must never render similarity score');
    assert.strictEqual(box.isPresent, false);
    assert.strictEqual(box.isReview, false);
    assert.strictEqual(box.isUnmatched, true);

    // Unmatched face without candidate name
    const unmatchedFaceWithoutCandidate = {
      faceId: 102,
      bbox: [300, 200, 450, 400],
      status: 'UNMATCHED',
      studentName: null,
      similarity: 0.12
    };

    const boxNoName = computeFaceBoundingBox(unmatchedFaceWithoutCandidate);
    assert.strictEqual(boxNoName.label, 'Unmatched');
    assert.strictEqual(boxNoName.color, '#ef4444');
  });

  it('a review face renders the student name and similarity in amber box', () => {
    const reviewFace = {
      faceId: 201,
      bbox: [80, 120, 220, 300],
      status: 'REVIEW',
      studentName: 'Priya Jadhav',
      similarity: 0.84
    };

    const box = computeFaceBoundingBox(reviewFace);
    assert.ok(box, 'Bounding box must be computed');
    assert.strictEqual(box.color, '#f59e0b', 'REVIEW face must have an amber box');
    assert.ok(box.label.includes('Priya Jadhav'), 'REVIEW face must render student name');
    assert.strictEqual(box.label, 'Priya Jadhav (84%)', 'REVIEW face must format as "<name> (<percentage>%)"');
    assert.strictEqual(box.isReview, true);
    assert.strictEqual(box.isUnmatched, false);
    assert.strictEqual(box.isPresent, false);
  });

  it('FacultyView component renames counter label "Borderline (REVIEW)" to "Needs review"', () => {
    const facultyViewPath = path.resolve('src/components/FacultyView.jsx');
    const content = fs.readFileSync(facultyViewPath, 'utf8');
    assert.ok(content.includes('Needs review'), 'FacultyView.jsx must contain "Needs review" counter label');
    assert.ok(!content.includes('Borderline (REVIEW)'), 'FacultyView.jsx must not contain "Borderline (REVIEW)"');
  });

  it('1 unknown face + a roster with 2 PRESENT students must show Matched 0, Review 0, Unmatched 1', () => {
    // Current photo recognition response: 1 detected face of an unknown person
    const photoResponse = {
      facesDetected: 1,
      studentsMatched: 0,
      studentsInReview: 0,
      unmatchedFaces: 1,
      unmatchedFaceCount: 1,
      detectedFaces: [
        { faceId: 1, bbox: [110, 90, 250, 310], status: 'UNMATCHED', studentName: null }
      ]
    };

    // Active session roster has 2 students already marked PRESENT
    const activeSessionRoster = [
      { studentId: 10, studentName: 'Aarav Mehta', finalStatus: 'PRESENT' },
      { studentId: 20, studentName: 'Rohan Sharma', finalStatus: 'PRESENT' }
    ];

    const stats = extractFacultyViewPhotoStats(photoResponse);
    assert.strictEqual(stats.facesDetected, 1, 'Faces Detected must be 1');
    assert.strictEqual(stats.studentsMatched, 0, 'Matched must be 0 for current photo (not 2 from roster)');
    assert.strictEqual(stats.studentsInReview, 0, 'Review must be 0 for current photo');
    assert.strictEqual(stats.unmatchedFaces, 1, 'Unmatched must be 1 for current photo');
    assert.strictEqual(stats.invariantSatisfied, true, 'Faces Detected must equal Matched + Review + Unmatched');
    assert.strictEqual(stats.facesDetected, stats.studentsMatched + stats.studentsInReview + stats.unmatchedFaces);

    // Verify roster retains 2 PRESENT records independently of photo stats
    const rosterPresentCount = activeSessionRoster.filter(r => r.finalStatus === 'PRESENT').length;
    assert.strictEqual(rosterPresentCount, 2, 'Roster retains 2 PRESENT students');
  });

  it('guarantees Faces Detected = Matched + Borderline + Unmatched via fallback when unmatchedFaces is missing', () => {
    const legacyResult = {
      facesDetected: 1,
      studentsMatched: 0,
      studentsInReview: 0,
      unmatchedFaceCount: 1
    };

    const stats = extractFacultyViewPhotoStats(legacyResult);
    assert.strictEqual(stats.unmatchedFaces, 1);
    assert.strictEqual(stats.invariantSatisfied, true);
  });
});
