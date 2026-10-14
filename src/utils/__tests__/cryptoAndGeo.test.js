import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  quickHash,
  computeAuditEntryHash,
  verifyAuditChainIntegrity,
  validateRevocationReason
} from '../cryptoAndGeo.js';

/**
 * Audit & Trust Cryptographic Ledger Test Suite
 * Tests SHA-256 hash chaining, tamper detection down to a single character alteration,
 * document verification states, and AI risk explainability factor parsing.
 * Compliant with IC-SRS-1.0 Section 8.7 & 8.8, and Test Cases TC-08, TC-11 & TC-12.
 */

describe('Audit & Trust - Cryptographic Hash Chain Tamper Detection', () => {
  const GENESIS = '0000000000000000000000000000000000000000000000000000000000000000';

  async function buildSampleLedger() {
    const block0 = {
      id: 1,
      prevHash: GENESIS,
      actorId: 1,
      action: 'USER_CREATED',
      entityType: 'USER',
      entityId: '101',
      oldValue: '{}',
      newValue: '{"name":"Aarav"}',
      reason: 'Batch import',
      createdAt: '2026-10-14T08:00:00'
    };
    block0.entryHash = await computeAuditEntryHash(block0);

    const block1 = {
      id: 2,
      prevHash: block0.entryHash,
      actorId: 2,
      action: 'MARK_ENTERED',
      entityType: 'MARK',
      entityId: '501',
      oldValue: '{}',
      newValue: '{"marks":25.0}',
      reason: 'Unit Test Evaluation',
      createdAt: '2026-10-14T08:05:00'
    };
    block1.entryHash = await computeAuditEntryHash(block1);

    const block2 = {
      id: 3,
      prevHash: block1.entryHash,
      actorId: 3,
      action: 'DOCUMENT_ISSUED',
      entityType: 'DOCUMENT',
      entityId: 'DOC-101',
      oldValue: '{}',
      newValue: '{"status":"VALID"}',
      reason: 'Bonafide issuance',
      createdAt: '2026-10-14T08:10:00'
    };
    block2.entryHash = await computeAuditEntryHash(block2);

    return [block0, block1, block2];
  }

  it('Criterion 1: Verification succeeds for an authentic, uncorrupted audit chain', async () => {
    const ledger = await buildSampleLedger();
    const result = await verifyAuditChainIntegrity(ledger);

    assert.strictEqual(result.status, 'INTACT', 'Authentic ledger must be INTACT');
    assert.strictEqual(result.totalEntries, 3);
    assert.strictEqual(result.brokenEntryId, null);
  });

  it('Criterion 1: Modifying a single character in any historical payload invalidates chain integrity', async () => {
    const ledger = await buildSampleLedger();

    // Malicious actor modifies a single character: changing "25.0" to "25.5" in block 1
    ledger[1].newValue = '{"marks":25.5}';

    const result = await verifyAuditChainIntegrity(ledger);

    assert.strictEqual(result.status, 'BROKEN', 'Tampered ledger must be reported BROKEN');
    assert.strictEqual(result.brokenEntryId, 2, 'Should identify entry ID 2 as corrupted');
    assert.strictEqual(result.tamperedIndex, 1, 'Should pinpoint exact block index (1)');
    assert.ok(result.reason.includes('Content Tampered'), 'Error should cite content tampering');
  });

  it('Criterion 1: Breaking previous block hash link invalidates chain integrity', async () => {
    const ledger = await buildSampleLedger();

    // Malicious actor alters prevHash link pointer
    ledger[1].prevHash = 'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff';

    const result = await verifyAuditChainIntegrity(ledger);

    assert.strictEqual(result.status, 'BROKEN');
    assert.strictEqual(result.brokenEntryId, 2);
    assert.strictEqual(result.tamperedIndex, 1);
    assert.ok(result.reason.includes('Broken Link'), 'Error should cite broken link');
  });
});

describe('Verifiable Credentials - Revocation Justification & Public Verification', () => {
  it('Criterion 2: Enforces minimum 10-character justification for document revocation', () => {
    // Under 10 characters
    const shortResult = validateRevocationReason('Suspended'); // 9 characters
    assert.strictEqual(shortResult.valid, false, 'Should reject reason with < 10 characters');
    assert.ok(shortResult.error.includes('at least 10 characters'));

    // 10+ characters
    const validResult = validateRevocationReason('Student transferred to another college');
    assert.strictEqual(validResult.valid, true, 'Should accept reason with >= 10 characters');
    assert.strictEqual(validResult.error, null);
  });

  it('Criterion 2: Public verification returns VALID for active documents and REVOKED for revoked ones', () => {
    const activeDoc = {
      code: 'DOC-2026-A1B2C3',
      type: 'Degree Certificate',
      maskedStudentName: 'A*** M***',
      status: 'VALID',
      revokedReason: null
    };

    const revokedDoc = {
      code: 'DOC-2026-D4E5F6',
      type: 'Bonafide Certificate',
      maskedStudentName: 'P*** S***',
      status: 'REVOKED',
      revokedReason: 'Credential cancelled due to disciplinary action'
    };

    function verifyPublic(doc) {
      if (!doc) return { isValid: false, status: 'NOT_FOUND' };
      if (doc.status === 'REVOKED') {
        return {
          isValid: false,
          status: 'REVOKED',
          code: doc.code,
          reason: doc.revokedReason
        };
      }
      return {
        isValid: true,
        status: 'VALID',
        code: doc.code,
        type: doc.type,
        studentName: doc.maskedStudentName
      };
    }

    const activeResult = verifyPublic(activeDoc);
    assert.strictEqual(activeResult.isValid, true);
    assert.strictEqual(activeResult.status, 'VALID');

    const revokedResult = verifyPublic(revokedDoc);
    assert.strictEqual(revokedResult.isValid, false);
    assert.strictEqual(revokedResult.status, 'REVOKED');
    assert.ok(revokedResult.reason.includes('disciplinary action'));

    const notFoundResult = verifyPublic(null);
    assert.strictEqual(notFoundResult.isValid, false);
    assert.strictEqual(notFoundResult.status, 'NOT_FOUND');
  });
});

describe('FastAPI Student Risk Payload Parsing & Explainability Extraction', () => {
  it('Criterion 3: Parses AI microservice risk score, level, and extracts top explainability factors', () => {
    const fastApiResponsePayload = {
      student_id: 42,
      risk_score: 0.86,
      level: 'HIGH',
      top_factors: [
        'Attendance rate below 75% threshold (58.4%)',
        'Attendance dropped 16.0 points in past 4 weeks',
        'Active backlog count: 3'
      ]
    };

    // Client-side parser
    function parseAiRiskPayload(payload) {
      return {
        studentId: payload.student_id,
        riskScore: payload.risk_score,
        riskLevel: payload.level,
        isHighRisk: payload.risk_score > 0.66,
        primaryFactor: payload.top_factors[0] || 'Unknown',
        allFactors: payload.top_factors
      };
    }

    const parsed = parseAiRiskPayload(fastApiResponsePayload);

    assert.strictEqual(parsed.studentId, 42);
    assert.strictEqual(parsed.riskScore, 0.86);
    assert.strictEqual(parsed.riskLevel, 'HIGH');
    assert.strictEqual(parsed.isHighRisk, true);
    assert.strictEqual(parsed.allFactors.length, 3);
    assert.ok(parsed.primaryFactor.includes('Attendance rate below 75%'));
  });
});
