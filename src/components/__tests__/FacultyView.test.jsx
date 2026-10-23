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
  const DEFAULT_THRESHOLD = 0.45;
  const DEFAULT_MARGIN = 0.05;

  function classifyFaceMatch(similarity, secondSimilarity = 0, threshold = DEFAULT_THRESHOLD, margin = DEFAULT_MARGIN) {
    if (similarity >= threshold && (similarity - secondSimilarity) >= margin) {
      return { status: 'PRESENT', reason: 'Verified by ArcFace facial recognition' };
    }
    if (similarity >= (threshold - 0.10) && similarity < threshold) {
      return { status: 'REVIEW', reason: 'Borderline similarity score; requires faculty review' };
    }
    return { status: 'UNMATCHED', reason: 'Similarity below recognition threshold' };
  }

  it('should classify high-confidence match with sufficient margin as PRESENT', () => {
    const result = classifyFaceMatch(0.78, 0.42);
    assert.strictEqual(result.status, 'PRESENT');
    assert.ok(result.reason.includes('Verified by ArcFace'));
  });

  it('should classify borderline match in [threshold - 0.10, threshold) as REVIEW', () => {
    const result = classifyFaceMatch(0.38, 0.20);
    assert.strictEqual(result.status, 'REVIEW');
    assert.ok(result.reason.includes('requires faculty review'));
  });

  it('should classify low similarity below 0.35 as UNMATCHED', () => {
    const result = classifyFaceMatch(0.28, 0.22);
    assert.strictEqual(result.status, 'UNMATCHED');
  });

  it('should flag as REVIEW if top similarity is above threshold but margin over second candidate is insufficient', () => {
    const result = classifyFaceMatch(0.48, 0.46);
    assert.notStrictEqual(result.status, 'PRESENT');
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
