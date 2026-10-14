import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { 
  getRotatingQRToken, 
  calculateHaversineDistance, 
  validateGeofence,
  validateSpoofConfidence 
} from '../../utils/cryptoAndGeo.js';

/**
 * FacultyView & Smart Attendance Test Suite
 * Tests dynamic QR token rotation every 15 seconds and geofence boundary verification.
 * Compliant with IC-SRS-1.0 Section 8.2 & 8.6, and Test Case TC-02.
 */
describe('Smart Attendance - Dynamic QR Code Token Rotation', () => {
  it('should generate token with IC- prefix and valid countdown seconds (1-15)', () => {
    const sessionId = 101;
    const tokenData = getRotatingQRToken(sessionId);

    assert.ok(tokenData.token, 'Token string should be present');
    assert.ok(tokenData.token.startsWith('IC-'), 'Token must start with IC- prefix');
    assert.strictEqual(tokenData.token.length, 19, 'Token length should be exactly 19 characters (IC- + 16 hex)');
    assert.ok(tokenData.secondsLeft >= 1 && tokenData.secondsLeft <= 15, 'secondsLeft must be between 1 and 15');
    assert.strictEqual(typeof tokenData.slot, 'number', 'slot index must be a number');
  });

  it('should rotate token slot every 15 seconds', () => {
    const sessionId = 101;
    const now = Date.now();
    const currentSlot = Math.floor(now / 15000);

    const tokenData = getRotatingQRToken(sessionId);
    assert.strictEqual(tokenData.slot, currentSlot, 'Slot must match floor(epoch / 15000)');

    // Simulate 16 seconds into the future
    const originalNow = Date.now;
    try {
      Date.now = () => now + 16000;
      const nextTokenData = getRotatingQRToken(sessionId);
      assert.strictEqual(nextTokenData.slot, currentSlot + 1, 'Slot must advance after 15+ seconds');
      assert.notStrictEqual(nextTokenData.token, tokenData.token, 'Token hash must rotate when slot advances');
    } finally {
      Date.now = originalNow;
    }
  });

  it('should generate deterministic token for the same session and slot', () => {
    const sessionId = 42;
    const t1 = getRotatingQRToken(sessionId);
    const t2 = getRotatingQRToken(sessionId);

    if (t1.slot === t2.slot) {
      assert.strictEqual(t1.token, t2.token, 'Tokens within the same 15s window must be identical');
    }
  });
});

describe('Smart Attendance - Geofence Haversine Boundary Edge Cases', () => {
  // Benchmark classroom coordinates (Campus Center)
  const roomLat = 18.52043;
  const roomLon = 73.85674;
  const allowableRadius = 50; // meters

  it('should pass check-in when student is inside perimeter (~12m away)', () => {
    const studentLat = 18.52051;
    const studentLon = 73.85681;
    const accuracy = 15;

    const result = validateGeofence(studentLat, studentLon, accuracy, roomLat, roomLon, allowableRadius);

    assert.strictEqual(result.valid, true, 'Check-in must be valid inside perimeter');
    assert.strictEqual(result.error, null);
    assert.ok(result.distanceM < allowableRadius, 'Distance must be less than 50m');
    assert.ok(result.distanceM > 0, 'Distance must be positive');
  });

  it('should pass check-in when student is exactly at classroom center (0m distance)', () => {
    const result = validateGeofence(roomLat, roomLon, 5, roomLat, roomLon, allowableRadius);

    assert.strictEqual(result.valid, true);
    assert.strictEqual(result.distanceM, 0);
    assert.strictEqual(result.error, null);
  });

  it('should reject check-in when student is outside allowable boundary (>50m)', () => {
    // ~150 meters away
    const farLat = 18.52175;
    const farLon = 73.85674;
    const accuracy = 10;

    const result = validateGeofence(farLat, farLon, accuracy, roomLat, roomLon, allowableRadius);

    assert.strictEqual(result.valid, false, 'Check-in must be rejected outside boundary');
    assert.ok(result.distanceM > allowableRadius, 'Distance must exceed allowable perimeter');
    assert.ok(result.error.includes('Geofence boundary violation'), 'Error should cite boundary violation');
  });

  it('should reject check-in when device GPS accuracy is worse than 100 meters', () => {
    const studentLat = 18.52045; // ~3m away geographically
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

describe('Smart Attendance - Biometric Anti-Spoofing Verification', () => {
  it('should pass biometric verification when spoof confidence meets threshold (>= 0.60)', () => {
    const result = validateSpoofConfidence(0.88, 0.60);
    assert.strictEqual(result.isPassing, true);
    assert.strictEqual(result.status, 'PRESENT');
  });

  it('should flag attendance for REVIEW when spoof confidence is below threshold (< 0.60)', () => {
    const result = validateSpoofConfidence(0.42, 0.60);
    assert.strictEqual(result.isPassing, false);
    assert.strictEqual(result.status, 'REVIEW');
    assert.ok(result.reason.includes('Anti-spoofing confidence'));
  });
});
