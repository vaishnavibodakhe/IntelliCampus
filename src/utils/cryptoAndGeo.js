// =====================================================================
// IntelliCampus Cryptographic, Hash Chain & Geofencing Algorithms
// Compliant with IC-SRS-1.0 Section 8.1, 8.2, 8.6, and 8.7
// =====================================================================

/**
 * Computes Haversine distance in meters between two GPS coordinates
 * IC-SRS-1.0 Section 8.2
 */
export function calculateHaversineDistance(lat1, lon1, lat2, lon2) {
  const R = 6371000; // Radius of Earth in meters
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round(R * c);
}

/**
 * Standard Web Crypto SHA-256 helper
 */
export async function sha256(message) {
  const msgBuffer = new TextEncoder().encode(message);
  const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Synchronous mock hash for immediate UI responsiveness if Web Crypto is delayed
 */
export function quickHash(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0;
  }
  const hex = Math.abs(hash).toString(16).padStart(8, '0');
  return (hex + '8f92a105c3b4e78912345678abcdef0123456789abcdef0123456789abcdef').slice(0, 64);
}

/**
 * Computes the next block entry hash in the audit chain
 * entry_hash = SHA-256(prev_hash + actor_id + action + entity_type + entity_id + old_val + new_val + reason + created_at)
 * IC-SRS-1.0 Section 8.7
 */
export async function computeAuditEntryHash(entry) {
  const payload = [
    entry.prevHash,
    entry.actorId,
    entry.action,
    entry.entityType,
    entry.entityId,
    entry.oldValue,
    entry.newValue,
    entry.reason,
    entry.createdAt
  ].join('|');
  return await sha256(payload);
}

/**
 * Verifies the full audit hash chain
 * Reports INTACT or BROKEN with the first tampered entry ID
 */
export async function verifyAuditChainIntegrity(auditLogs) {
  if (!auditLogs || auditLogs.length === 0) {
    return { status: "INTACT", totalEntries: 0, brokenEntryId: null };
  }

  for (let i = 0; i < auditLogs.length; i++) {
    const current = auditLogs[i];

    // Check link to previous block
    if (i > 0) {
      const prev = auditLogs[i - 1];
      if (current.prevHash !== prev.entryHash) {
        return {
          status: "BROKEN",
          reason: "Broken Link: prevHash does not match previous block's entryHash",
          brokenEntryId: current.id,
          tamperedIndex: i
        };
      }
    }

    // Verify cryptographic integrity of this block's contents
    const expectedHash = await computeAuditEntryHash(current);
    if (current.entryHash !== expectedHash) {
      return {
        status: "BROKEN",
        reason: "Content Tampered: Payload does not produce stored entryHash",
        brokenEntryId: current.id,
        tamperedIndex: i
      };
    }
  }

  return { status: "INTACT", totalEntries: auditLogs.length, brokenEntryId: null };
}

/**
 * Validates whether student GPS position is within classroom boundary.
 * IC-SRS-1.0 Section 8.2 & Test Case TC-02
 * Rejects if accuracy > 100m or distance > maxRadiusM.
 */
export function validateGeofence(studentLat, studentLon, studentAccuracyM, roomLat, roomLon, maxRadiusM = 50) {
  if (studentLat == null || studentLon == null || roomLat == null || roomLon == null) {
    return {
      valid: false,
      distanceM: null,
      error: "Incomplete GPS coordinates provided for geofence verification."
    };
  }

  // Reject check-ins where GPS accuracy is greater than 100 meters
  if (studentAccuracyM != null && studentAccuracyM > 100) {
    return {
      valid: false,
      distanceM: null,
      error: `GPS accuracy (±${Math.round(studentAccuracyM)}m) exceeds maximum allowable threshold of 100m. Please move closer to a window or re-calibrate device GPS.`
    };
  }

  const distanceM = calculateHaversineDistance(studentLat, studentLon, roomLat, roomLon);
  const allowedRadius = maxRadiusM || 50;

  if (distanceM > allowedRadius) {
    return {
      valid: false,
      distanceM,
      error: `Geofence boundary violation: You are ${distanceM}m away from classroom center, exceeding allowable perimeter of ${allowedRadius}m.`
    };
  }

  return {
    valid: true,
    distanceM,
    error: null
  };
}

/**
 * Validates AI face anti-spoofing confidence score.
 * IC-SRS-1.0 Section 8.1 & Test Case TC-04
 * Flags record as REVIEW if spoof confidence is below 0.60.
 */
export function validateSpoofConfidence(spoofScore, minThreshold = 0.60) {
  if (spoofScore == null) {
    return {
      isPassing: false,
      status: "REVIEW",
      reason: "Missing anti-spoofing liveness verification."
    };
  }

  if (spoofScore < minThreshold) {
    return {
      isPassing: false,
      status: "REVIEW",
      reason: `Anti-spoofing confidence (${spoofScore.toFixed(2)}) is below safety threshold (${minThreshold.toFixed(2)}). Flagged for faculty manual review.`
    };
  }

  return {
    isPassing: true,
    status: "PRESENT",
    reason: `Anti-spoofing verified (${spoofScore.toFixed(2)} >= ${minThreshold.toFixed(2)})`
  };
}

/**
 * Validates document revocation justification string.
 * IC-SRS-1.0 Section 8.7 & Test Case TC-12
 * Enforces mandatory reason of at least 10 characters.
 */
export function validateRevocationReason(reason, minLength = 10) {
  if (!reason || reason.trim().length < minLength) {
    const len = reason ? reason.trim().length : 0;
    return {
      valid: false,
      error: `Revocation justification is mandatory and must contain at least ${minLength} characters (provided: ${len}).`
    };
  }
  return {
    valid: true,
    error: null
  };
}

/**
 * Validates Student Registration Form Inputs
 * Enforces email format, 10-digit Indian mobile, strong password, roll number format, and DPDP Act 2023 consent
 */
export function validateStudentRegistration(data) {
  const errors = {};

  if (!data.fullName || data.fullName.trim().length < 2) {
    errors.fullName = 'Full Name is required (minimum 2 characters).';
  }

  const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
  if (!data.email || !emailRegex.test(data.email.trim())) {
    errors.email = 'Please provide a valid college or personal email address.';
  }

  const indianMobileRegex = /^[6-9]\d{9}$/;
  if (!data.contactNo || !indianMobileRegex.test(data.contactNo.trim())) {
    errors.contactNo = 'Enter a valid 10-digit Indian mobile number starting with 6, 7, 8, or 9.';
  }

  if (!data.rollNo || data.rollNo.trim().length < 3) {
    errors.rollNo = 'Roll number / PRN is required (minimum 3 characters, e.g. CS-104).';
  }

  // Strong password: >= 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special character
  const strongPwRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]).{8,}$/;
  if (!data.password || !strongPwRegex.test(data.password)) {
    errors.password = 'Password must be at least 8 characters and include uppercase, lowercase, number, and special character.';
  }

  if (data.password !== data.confirmPassword) {
    errors.confirmPassword = 'Passwords do not match.';
  }

  if (!data.department || data.department.trim().length === 0) {
    errors.department = 'Please select your academic department.';
  }

  if (!data.division || data.division.trim().length === 0) {
    errors.division = 'Please select your division.';
  }

  if (data.biometricConsent !== true) {
    errors.biometricConsent = 'DPDP Act 2023 biometric consent is required for facial attendance enrollment.';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors
  };
}

/**
 * Calculates Cosine Similarity between two N-dimensional vectors
 * sim(u, v) = (u . v) / (||u|| * ||v||)
 */
export function calculateCosineSimilarity(vecA, vecB) {
  if (!vecA || !vecB || vecA.length !== vecB.length) return 0;
  let dotProduct = 0;
  let normA = 0;
  let normB = 0;
  for (let i = 0; i < vecA.length; i++) {
    dotProduct += vecA[i] * vecB[i];
    normA += vecA[i] * vecA[i];
    normB += vecB[i] * vecB[i];
  }
  if (normA === 0 || normB === 0) return 0;
  return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
}

/**
 * Averages K face embeddings (512-d) and normalizes by L2 norm
 * Stored at enrollment per requirement: 3 best quality frames (front, left, right) averaged
 */
export function computeAverageEmbedding(embeddings) {
  if (!embeddings || embeddings.length === 0) return [];
  if (embeddings.length === 1) return [...embeddings[0]];

  const dim = embeddings[0].length;
  const avg = new Array(dim).fill(0);

  for (const emb of embeddings) {
    for (let i = 0; i < dim; i++) {
      avg[i] += emb[i];
    }
  }

  for (let i = 0; i < dim; i++) {
    avg[i] /= embeddings.length;
  }

  // L2 normalize
  let norm = 0;
  for (let i = 0; i < dim; i++) norm += avg[i] * avg[i];
  norm = Math.sqrt(norm);
  if (norm > 1e-6) {
    for (let i = 0; i < dim; i++) avg[i] /= norm;
  }
  return avg;
}

/**
 * Duplicate Face Detection Check
 * ArcFace cosine SIMILARITY is HIGHER for the same person:
 * - similarity >= threshold (default 0.45) => Same person (REJECT registration as duplicate)
 * - similarity < reviewThreshold (default 0.35) => Different person (CLEAR)
 * - similarity in [reviewThreshold, threshold) => Borderline (Flag for verification)
 */
export function checkDuplicateFace(
  candidateEmbedding,
  enrolledStudents,
  threshold = 0.45,
  reviewThreshold = 0.35
) {
  if (!candidateEmbedding || candidateEmbedding.length === 0 || !enrolledStudents || enrolledStudents.length === 0) {
    return { isDuplicate: false, status: 'CLEAR', maxSimilarity: 0, matchedStudent: null };
  }

  let maxSimilarity = -1;
  let matchedStudent = null;

  for (const student of enrolledStudents) {
    const studentEmb = student.faceEmbedding || student.embedding;
    if (!studentEmb || studentEmb.length !== candidateEmbedding.length) continue;

    const sim = calculateCosineSimilarity(candidateEmbedding, studentEmb);
    if (sim > maxSimilarity) {
      maxSimilarity = sim;
      matchedStudent = student;
    }
  }

  // Similarity >= threshold means SAME person => REJECT registration
  if (maxSimilarity >= threshold) {
    return {
      isDuplicate: true,
      status: 'DUPLICATE',
      maxSimilarity: parseFloat(maxSimilarity.toFixed(4)),
      matchedStudent,
      error: `Duplicate face detected: Matches enrolled student (${matchedStudent?.name || matchedStudent?.rollNo || 'ID: ' + matchedStudent?.id}) with similarity ${maxSimilarity.toFixed(2)} >= ${threshold.toFixed(2)}.`
    };
  }

  if (maxSimilarity >= reviewThreshold) {
    return {
      isDuplicate: false,
      status: 'BORDERLINE',
      maxSimilarity: parseFloat(maxSimilarity.toFixed(4)),
      matchedStudent,
      warning: `Borderline face similarity (${maxSimilarity.toFixed(2)}) requires administrative review.`
    };
  }

  return {
    isDuplicate: false,
    status: 'CLEAR',
    maxSimilarity: parseFloat(maxSimilarity.toFixed(4)),
    matchedStudent: null
  };
}

/**
 * Validates image brightness from raw pixel RGBA data.
 * Ideal average luminance should be between 40 (too dark) and 220 (too bright).
 */
export function validateImageBrightness(rgbaData) {
  if (!rgbaData || rgbaData.length < 4) {
    return { valid: false, brightness: 0, status: 'INVALID', error: 'No image data provided.' };
  }

  let totalLuminance = 0;
  const pixelCount = Math.floor(rgbaData.length / 4);

  // Sample pixels (every 4th pixel for speed if large)
  const step = pixelCount > 10000 ? 4 : 1;
  let sampledCount = 0;

  for (let i = 0; i < rgbaData.length; i += 4 * step) {
    const r = rgbaData[i];
    const g = rgbaData[i + 1];
    const b = rgbaData[i + 2];
    // Standard relative luminance
    const lum = 0.299 * r + 0.587 * g + 0.114 * b;
    totalLuminance += lum;
    sampledCount++;
  }

  const avgBrightness = sampledCount > 0 ? totalLuminance / sampledCount : 0;
  const rounded = Math.round(avgBrightness);

  if (rounded < 40) {
    return {
      valid: false,
      brightness: rounded,
      status: 'TOO_DARK',
      error: `Lighting is too dark (${rounded}/255). Please ensure your face is well illuminated.`
    };
  }

  if (rounded > 220) {
    return {
      valid: false,
      brightness: rounded,
      status: 'TOO_BRIGHT',
      error: `Lighting is overexposed (${rounded}/255). Please avoid direct backlight.`
    };
  }

  return {
    valid: true,
    brightness: rounded,
    status: 'OPTIMAL',
    error: null
  };
}

/**
 * Estimates image sharpness via Laplacian variance approximation.
 * Sharpness variance score >= 10.0 passes the blur threshold.
 */
export function estimateImageSharpness(rgbaData, width, height) {
  if (!rgbaData || !width || !height || width < 3 || height < 3) {
    return { valid: false, sharpness: 0, status: 'INVALID', error: 'Invalid frame dimensions.' };
  }

  // Convert to grayscale matrix
  const gray = new Float32Array(width * height);
  for (let i = 0, p = 0; i < rgbaData.length; i += 4, p++) {
    gray[p] = 0.299 * rgbaData[i] + 0.587 * rgbaData[i + 1] + 0.114 * rgbaData[i + 2];
  }

  // Discrete Laplacian 3x3 kernel: [[0, 1, 0], [1, -4, 1], [0, 1, 0]]
  let sum = 0;
  let sumSq = 0;
  let count = 0;

  for (let y = 1; y < height - 1; y++) {
    const rowOffset = y * width;
    for (let x = 1; x < width - 1; x++) {
      const idx = rowOffset + x;
      const lap =
        gray[idx - width] +
        gray[idx - 1] -
        4 * gray[idx] +
        gray[idx + 1] +
        gray[idx + width];

      sum += lap;
      sumSq += lap * lap;
      count++;
    }
  }

  if (count === 0) {
    return { valid: false, sharpness: 0, status: 'INVALID', error: 'Insufficient pixels for sharpness check.' };
  }

  const mean = sum / count;
  const variance = sumSq / count - mean * mean;
  const sharpness = parseFloat(Math.max(0, variance).toFixed(2));

  if (sharpness < 10.0) {
    return {
      valid: false,
      sharpness,
      status: 'BLURRY',
      error: `Frame is blurry (sharpness score ${sharpness.toFixed(1)} < 10.0). Please hold your device steady.`
    };
  }

  return {
    valid: true,
    sharpness,
    status: 'SHARP',
    error: null
  };
}

/**
 * Validates pose consistency across pairwise frame cosine similarities.
 * Enforces pairwise cosine >= minThreshold (default 0.5).
 */
export function validatePoseConsistency(pairwiseSimilarities, minThreshold = 0.5) {
  if (!pairwiseSimilarities || pairwiseSimilarities.length === 0) {
    return { valid: false, minSimilarity: 0, error: 'No pose similarities provided.' };
  }

  let minSim = 1.0;
  for (const sim of pairwiseSimilarities) {
    if (typeof sim !== 'number') continue;
    if (sim < minSim) minSim = sim;
  }

  const minRounded = parseFloat(minSim.toFixed(4));
  if (minRounded < minThreshold) {
    return {
      valid: false,
      minSimilarity: minRounded,
      error: `Pose consistency check failed: Pairwise similarity (${minRounded}) is below required minimum threshold (${minThreshold}). Frames must belong to the same person.`
    };
  }

  return {
    valid: true,
    minSimilarity: minRounded,
    error: null
  };
}

/**
 * Validates uniqueness of frame SHA-256 hashes across the 3 capture poses.
 */
export function validateFrameUniqueness(frameHashes) {
  if (!frameHashes || frameHashes.length !== 3) {
    return {
      valid: false,
      uniqueCount: frameHashes ? frameHashes.length : 0,
      error: `Enrollment requires exactly 3 distinct frame hashes. Received: ${frameHashes ? frameHashes.length : 0}`
    };
  }

  const set = new Set(frameHashes);
  if (set.size !== 3) {
    return {
      valid: false,
      uniqueCount: set.size,
      error: 'Duplicate frames detected: All 3 capture poses (front, left, right) must be distinct live camera frames.'
    };
  }

  return {
    valid: true,
    uniqueCount: 3,
    error: null
  };
}

/**
 * Verifies that the client execution environment permits getUserMedia camera streaming
 * (HTTPS or localhost per W3C Media Capture and Streams specification).
 */
export function checkCameraSecurityEnvironment(hostname = 'localhost', isSecureContext = true) {
  const isLocal =
    hostname === 'localhost' ||
    hostname === '127.0.0.1' ||
    hostname === '[::1]' ||
    hostname === '0.0.0.0';

  if (!isSecureContext && !isLocal) {
    return {
      isSecure: false,
      error: 'Camera access is restricted. Live facial capture requires a secure HTTPS connection or localhost origin.'
    };
  }

  return {
    isSecure: true,
    error: null
  };
}




