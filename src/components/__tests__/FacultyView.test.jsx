import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
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
    const result = classifyFaceMatch(0.52, 0.50);
    assert.strictEqual(result.status, 'PRESENT');
  });

  it('should flag ambiguous tie where multiple candidates share identical top similarity as REVIEW', () => {
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
  function resolveStudentMatches(candidatePairs) {
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
    const candidatePairs = [
      { faceIndex: 0, studentId: 101, similarity: 0.88 },
      { faceIndex: 1, studentId: 101, similarity: 0.72 },
      { faceIndex: 1, studentId: 102, similarity: 0.65 }
    ];

    const matches = resolveStudentMatches(candidatePairs);

    const face0Match = matches.find(m => m.faceIndex === 0);
    assert.ok(face0Match);
    assert.strictEqual(face0Match.studentId, 101);

    const face1Match = matches.find(m => m.faceIndex === 1);
    assert.ok(face1Match);
    assert.strictEqual(face1Match.studentId, 102);

    const studentIds = matches.map(m => m.studentId);
    assert.strictEqual(new Set(studentIds).size, matches.length);
  });
});

describe('Smart Attendance - Geofence Haversine Boundary Edge Cases', () => {
  const roomLat = 18.52043;
  const roomLon = 73.85674;
  const allowableRadius = 50;

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
    const poorAccuracy = 120;

    const result = validateGeofence(studentLat, studentLon, poorAccuracy, roomLat, roomLon, allowableRadius);

    assert.strictEqual(result.valid, false, 'Check-in must be rejected when accuracy exceeds 100m');
    assert.ok(result.error.includes('GPS accuracy'), 'Error should cite GPS accuracy');
  });

  it('should correctly calculate Haversine distance between known points', () => {
    const d1 = calculateHaversineDistance(0, 0, 0, 0);
    assert.strictEqual(d1, 0, 'Distance between identical points should be 0');

    const d2 = calculateHaversineDistance(18.52043, 73.85674, 18.52133, 73.85674);
    assert.ok(Math.abs(d2 - 100) < 15, 'Calculated distance should be approx 100m');
  });
});
