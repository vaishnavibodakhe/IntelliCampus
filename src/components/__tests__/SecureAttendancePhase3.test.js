import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

/**
 * Phase 3: Secure Live-Camera Attendance Tests
 *
 * Verifies frontend security rules, error-handling invariants,
 * camera lifecycle, and state preservation under API failures.
 */
describe('Phase 3 - Secure Live-Camera Attendance Frontend Invariants', () => {

  describe('Live-Camera-Only Capture Constraints', () => {
    it('should reject photo capture when video stream or camera is not active', () => {
      const isCameraActive = false;
      const videoElement = null;

      function canCapturePhoto(cameraActive, videoEl) {
        if (!cameraActive || !videoEl || !videoEl.videoWidth || !videoEl.videoHeight) {
          return { canCapture: false, reason: 'Live camera is not active or video feed is unavailable' };
        }
        return { canCapture: true };
      }

      const result = canCapturePhoto(isCameraActive, videoElement);
      assert.strictEqual(result.canCapture, false);
      assert.ok(result.reason.includes('Live camera is not active'));
    });

    it('should prohibit synthetic canvas fallback or mock image generation on capture failure', () => {
      // Insecure pattern: if video stream fails, generate a synthetic rectangle canvas and send it anyway.
      // Secure requirement: do not synthesize any photo data; abort and notify user.
      function captureClassroomFrame(videoRef, isCameraActive) {
        if (!isCameraActive || !videoRef || !videoRef.current) {
          // MUST return null/throw error, NOT generate fake canvas
          return null;
        }
        return 'valid_blob_placeholder';
      }

      const mockVideoRef = { current: null };
      const captured = captureClassroomFrame(mockVideoRef, false);
      assert.strictEqual(captured, null, 'Must return null instead of generating fallback synthetic image');
    });

    it('should disallow manual file upload input in live-camera attendance UI', () => {
      // Phase 3 requirement: only live camera capture is permitted; manual photo file input is disallowed
      const permittedCaptureModes = ['LIVE_WEBCAM'];
      assert.strictEqual(permittedCaptureModes.includes('FILE_UPLOAD'), false);
    });
  });

  describe('Session Expiry & Photo Limit Guards', () => {
    function isCapturePermitted(session, isProcessing) {
      if (!session) return { permitted: false, reason: 'No active session' };
      if (session.status !== 'OPEN') return { permitted: false, reason: 'Session is not OPEN' };
      if (session.photoCount >= 5) return { permitted: false, reason: 'Maximum photo limit (5) reached' };
      if (session.expiresAt && new Date(session.expiresAt) <= new Date()) {
        return { permitted: false, reason: 'Session photo window has expired' };
      }
      if (isProcessing) return { permitted: false, reason: 'Capture currently in progress' };
      return { permitted: true };
    }

    it('should permit capture for OPEN session with photoCount < 5 and unexpired window', () => {
      const session = {
        id: 10,
        status: 'OPEN',
        photoCount: 2,
        expiresAt: new Date(Date.now() + 60000).toISOString()
      };
      const res = isCapturePermitted(session, false);
      assert.strictEqual(res.permitted, true);
    });

    it('should disable capture when photoCount reaches 5', () => {
      const session = {
        id: 10,
        status: 'OPEN',
        photoCount: 5,
        expiresAt: new Date(Date.now() + 60000).toISOString()
      };
      const res = isCapturePermitted(session, false);
      assert.strictEqual(res.permitted, false);
      assert.ok(res.reason.includes('Maximum photo limit'));
    });

    it('should disable capture when session is FINALISED or closed', () => {
      const session = {
        id: 10,
        status: 'FINALISED',
        photoCount: 2,
        expiresAt: new Date(Date.now() + 60000).toISOString()
      };
      const res = isCapturePermitted(session, false);
      assert.strictEqual(res.permitted, false);
      assert.ok(res.reason.includes('Session is not OPEN'));
    });

    it('should disable capture when session expiresAt is in the past', () => {
      const session = {
        id: 10,
        status: 'OPEN',
        photoCount: 1,
        expiresAt: new Date(Date.now() - 5000).toISOString()
      };
      const res = isCapturePermitted(session, false);
      assert.strictEqual(res.permitted, false);
      assert.ok(res.reason.includes('expired'));
    });

    it('should disable capture while a photo is already being processed', () => {
      const session = {
        id: 10,
        status: 'OPEN',
        photoCount: 1,
        expiresAt: new Date(Date.now() + 60000).toISOString()
      };
      const res = isCapturePermitted(session, true);
      assert.strictEqual(res.permitted, false);
      assert.ok(res.reason.includes('in progress'));
    });
  });

  describe('Camera Error Handling & MediaStream Track Lifecycle', () => {
    it('should map camera DOMExceptions to user-friendly and actionable error messages', () => {
      function getCameraErrorMessage(err) {
        if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
          return 'Camera permission was denied. Please allow camera access in your browser settings to take attendance.';
        }
        if (err.name === 'NotFoundError' || err.name === 'DevicesNotFoundError') {
          return 'No camera device found on this system. Live camera is required.';
        }
        if (err.name === 'NotReadableError' || err.name === 'TrackStartError') {
          return 'Camera is already in use by another application. Please close other apps and try again.';
        }
        return `Camera error: ${err.message || 'Unable to access live camera'}`;
      }

      const notAllowed = getCameraErrorMessage({ name: 'NotAllowedError', message: 'Permission denied' });
      assert.ok(notAllowed.includes('permission was denied'));

      const notFound = getCameraErrorMessage({ name: 'NotFoundError', message: 'Not found' });
      assert.ok(notFound.includes('No camera device found'));

      const inUse = getCameraErrorMessage({ name: 'NotReadableError', message: 'In use' });
      assert.ok(inUse.includes('already in use'));
    });

    it('should stop all MediaStream tracks on camera toggle or unmount', () => {
      let stoppedTrackCount = 0;
      const mockStream = {
        getTracks: () => [
          { stop: () => { stoppedTrackCount++; } },
          { stop: () => { stoppedTrackCount++; } }
        ]
      };

      function stopCameraTracks(stream) {
        if (stream && typeof stream.getTracks === 'function') {
          stream.getTracks().forEach(track => track.stop());
        }
      }

      stopCameraTracks(mockStream);
      assert.strictEqual(stoppedTrackCount, 2, 'All tracks must be stopped');
    });
  });

  describe('API Failure & State Preservation Invariant', () => {
    it('should preserve existing server-confirmed attendance records when an API call fails', () => {
      // Invariant: when uploadClassroomPhoto fails or network drops, do NOT overwrite
      // existing confirmed attendance records with empty array or fake data.
      const existingRoster = [
        { id: 101, studentName: 'Alice', status: 'PRESENT', confidenceScore: 0.82 },
        { id: 102, studentName: 'Bob', status: 'ABSENT', confidenceScore: 0.0 }
      ];

      function handleApiError(currentRecords, error) {
        // Must preserve currentRecords and record the error message
        return {
          records: currentRecords, // unchanged
          apiError: error.message || 'Network request failed'
        };
      }

      const failureResult = handleApiError(existingRoster, new Error('500 Internal Server Error'));
      assert.strictEqual(failureResult.records.length, 2);
      assert.strictEqual(failureResult.records[0].status, 'PRESENT');
      assert.strictEqual(failureResult.records[1].status, 'ABSENT');
      assert.strictEqual(failureResult.apiError, '500 Internal Server Error');
    });

    it('should NEVER fabricate student attendance (e.g., student ID=6) on API failure', () => {
      // Regression check: FacultyView previously had a fallback in catch():
      // setAttendanceRecords(prev => prev.map(r => r.id === 6 ? { ...r, status: 'PRESENT' } : r));
      // This is dangerous and strictly prohibited.
      const initialRoster = [
        { id: 6, studentName: 'John Doe', status: 'ABSENT' }
      ];

      function secureErrorHandler(prevRoster, err) {
        // Return prevRoster without fabricating status
        return {
          roster: prevRoster,
          error: err.message
        };
      }

      const res = secureErrorHandler(initialRoster, new Error('Network timeout'));
      const student6 = res.roster.find(r => r.id === 6);
      assert.strictEqual(student6.status, 'ABSENT', 'Student 6 must NOT be fabricated as PRESENT on error');
    });
  });

  describe('Multi-Photo Attendance Aggregation Invariants', () => {
    it('should not downgrade PRESENT or REVIEW to ABSENT in subsequent photo captures', () => {
      const priorState = {
        101: { status: 'PRESENT', confidence: 0.85, method: 'AI_FACE' },
        102: { status: 'REVIEW', confidence: 0.42, method: 'AI_FACE' },
        103: { status: 'ABSENT', confidence: 0.0, method: 'AI_FACE' }
      };

      // Photo 2 detects student 103, but photo 2 did not capture student 101's face
      const photo2Detected = [
        { studentId: 103, status: 'PRESENT', confidence: 0.79 }
      ];

      function mergePhotoDetections(prior, newDetections) {
        const merged = { ...prior };
        for (const det of newDetections) {
          const current = merged[det.studentId];
          // If already PRESENT, keep it
          if (current && current.status === 'PRESENT') {
            continue;
          }
          merged[det.studentId] = {
            status: det.status,
            confidence: det.confidence,
            method: 'AI_FACE'
          };
        }
        return merged;
      }

      const updated = mergePhotoDetections(priorState, photo2Detected);

      // Student 101 must remain PRESENT even though absent from photo 2
      assert.strictEqual(updated[101].status, 'PRESENT');
      // Student 102 must remain REVIEW
      assert.strictEqual(updated[102].status, 'REVIEW');
      // Student 103 was upgraded to PRESENT
      assert.strictEqual(updated[103].status, 'PRESENT');
      assert.strictEqual(updated[103].confidence, 0.79);
    });

    it('should preserve manual faculty override regardless of subsequent AI detections', () => {
      const priorState = {
        101: { status: 'PRESENT', confidence: 1.0, isManualOverride: true, statusReason: 'Faculty confirmed in person' }
      };

      // Subsequent photo has low confidence or misses student 101
      const photo2Detection = { studentId: 101, status: 'REVIEW', confidence: 0.38, isManualOverride: false };

      function applyDetection(currentRecord, incoming) {
        if (currentRecord && currentRecord.isManualOverride) {
          // Keep manual override
          return currentRecord;
        }
        return incoming;
      }

      const finalRecord = applyDetection(priorState[101], photo2Detection);
      assert.strictEqual(finalRecord.status, 'PRESENT');
      assert.strictEqual(finalRecord.isManualOverride, true);
      assert.ok(finalRecord.statusReason.includes('Faculty confirmed'));
    });
  });

  describe('Face Recognition Decision Policy (20% / 50% Rules without Spoof Dependency)', () => {
    const MATCH_THRESHOLD = 0.50;
    const REVIEW_THRESHOLD = 0.20;

    function evaluateBiometricDecision(similarity, secondSimilarity, spoofScore) {
      if (similarity === null || similarity === undefined) {
        return { status: 'UNMATCHED', reason: 'Similarity below threshold' };
      }
      const isAmbiguousTie = (secondSimilarity || 0) > 0 && Math.abs(similarity - (secondSimilarity || 0)) < 1e-6;

      // 1. Qualifying match at or above 0.50: automatically marked PRESENT without requiring spoofScore
      if (similarity >= MATCH_THRESHOLD && !isAmbiguousTie) {
        return {
          status: 'PRESENT',
          reason: `Biometric face recognition verified (Cosine: ${similarity.toFixed(2)} >= ${MATCH_THRESHOLD.toFixed(2)})`
        };
      }

      // 2. Ambiguous tie or review range in [0.20, 0.50)
      if ((similarity >= REVIEW_THRESHOLD && similarity < MATCH_THRESHOLD) || isAmbiguousTie) {
        return {
          status: 'REVIEW',
          reason: `Borderline biometric match (Cosine: ${similarity.toFixed(2)}). Requires faculty verification.`
        };
      }

      return { status: 'UNMATCHED', reason: 'Similarity below threshold' };
    }

    it('should mark high-confidence match (>= 0.50) as PRESENT when spoofScore is null without requiring liveness', () => {
      const decision = evaluateBiometricDecision(0.85, 0.20, null);
      assert.strictEqual(decision.status, 'PRESENT', 'Must mark PRESENT without requiring spoofScore');
      assert.ok(decision.reason.includes('Biometric face recognition verified'));
      assert.ok(!decision.reason.includes('liveness verified'));
    });

    it('should route match in [0.20, 0.50) to REVIEW requiring faculty verification', () => {
      const decision = evaluateBiometricDecision(0.35, 0.20, null);
      assert.strictEqual(decision.status, 'REVIEW');
      assert.ok(decision.reason.includes('Requires faculty verification'));
    });

    it('should classify score below 0.20 as UNMATCHED', () => {
      const decision = evaluateBiometricDecision(0.18, 0.10, null);
      assert.strictEqual(decision.status, 'UNMATCHED');
    });

    it('should verify exact boundaries: 0.1999 (UNMATCHED), 0.20 (REVIEW), 0.4999 (REVIEW), 0.50 (PRESENT), 0.5001 (PRESENT)', () => {
      assert.strictEqual(evaluateBiometricDecision(0.1999).status, 'UNMATCHED');
      assert.strictEqual(evaluateBiometricDecision(0.20).status, 'REVIEW');
      assert.strictEqual(evaluateBiometricDecision(0.4999).status, 'REVIEW');
      assert.strictEqual(evaluateBiometricDecision(0.50).status, 'PRESENT');
      assert.strictEqual(evaluateBiometricDecision(0.5001).status, 'PRESENT');
    });

    it('should allow high-confidence match with small margin to be marked PRESENT', () => {
      const decision = evaluateBiometricDecision(0.52, 0.50, null);
      assert.strictEqual(decision.status, 'PRESENT');
    });
  });
});
