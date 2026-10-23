import React, { useState } from 'react';
import { 
  ShieldCheck, 
  Search, 
  CheckCircle, 
  XCircle, 
  AlertOctagon, 
  QrCode, 
  Copy, 
  ExternalLink,
  Lock
} from 'lucide-react';

import { api } from '../api/client';

export default function PublicVerifierView({ issuedDocuments = [] }) {
  const [searchMode, setSearchMode] = useState('CODE'); // 'CODE', 'HASH', 'QR'
  const [searchCode, setSearchCode] = useState(() => {
    if (typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      return params.get('code') || '';
    }
    return '';
  });
  const [searchedDoc, setSearchedDoc] = useState(null);
  const [hasSearched, setHasSearched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [isScanningQr, setIsScanningQr] = useState(false);

  // Auto-verify if code passed in URL
  React.useEffect(() => {
    if (searchCode.trim()) {
      executeVerification(searchCode.trim());
    }
  }, []);

  const executeVerification = async (codeOrHash) => {
    if (!codeOrHash || !codeOrHash.trim()) return;
    setLoading(true);
    setHasSearched(true);
    try {
      const data = await api.verifyCredential(codeOrHash.trim());
      if (data && data.code) {
        setSearchedDoc({
          code: data.code,
          type: (data.credentialType || 'DEGREE').replace(/_/g, ' '),
          maskedStudentName: data.maskedStudentName || data.studentName || 'Confidential Student',
          department: data.department || 'General Academic',
          issueDate: data.issueDate || data.issuedAt || 'Official Record',
          authorizedSignatory: data.authorizedSignatory || 'Office of the Principal',
          status: data.revoked ? 'REVOKED' : (data.status || 'VALID'),
          revokedReason: data.revocationReason || data.revokedReason || 'Document recalled by Principal',
          fileHash: data.documentHash || data.fileHash || 'SHA-256 Validated'
        });
      } else {
        setSearchedDoc(null);
      }
    } catch {
      // Check local issued documents as fallback if offline
      const found = (issuedDocuments || []).find(d => 
        (d.code && d.code.toUpperCase() === codeOrHash.toUpperCase()) ||
        (d.fileHash && d.fileHash.toLowerCase() === codeOrHash.toLowerCase())
      );
      setSearchedDoc(found || null);
    } finally {
      setLoading(false);
    }
  };

  const handleVerify = (e) => {
    if (e) e.preventDefault();
    if (!searchCode.trim()) return;
    executeVerification(searchCode.trim());
  };

  const handleSimulateQrScan = (doc) => {
    setIsScanningQr(true);
    setTimeout(() => {
      setSearchCode(doc.code || doc.verificationCode);
      executeVerification(doc.code || doc.verificationCode);
      setIsScanningQr(false);
    }, 700);
  };

  return (
    <div className="verifier-container animate-fade-in">
      <div className="verifier-hero glass-card text-center">
        <div className="verifier-badge-center">
          <ShieldCheck size={36} className="text-primary" />
        </div>
        <h1 className="verifier-title">IntelliCampus Public Credential Verification</h1>
        <p className="verifier-subtitle">
          Independent cryptographic authenticity verification portal. Any third party, employer, or authority can verify credentials without user authentication (`DOC-03`).
        </p>

        {/* Verification Mode Selector */}
        <div style={{ display: 'flex', justifyContent: 'center', gap: '8px', margin: '16px 0 20px' }}>
          <button
            type="button"
            onClick={() => { setSearchMode('CODE'); setSearchCode('DOC-2026-CS-8921'); }}
            className={`btn-sm ${searchMode === 'CODE' ? 'btn-primary' : 'btn-secondary'}`}
          >
            <Search size={14} /> Verify by Document Code
          </button>
          <button
            type="button"
            onClick={() => { 
              setSearchMode('HASH'); 
              const validDoc = issuedDocuments[0];
              setSearchCode(validDoc ? validDoc.fileHash : 'sha256_9f83a1b2c3d4e5f6'); 
            }}
            className={`btn-sm ${searchMode === 'HASH' ? 'btn-primary' : 'btn-secondary'}`}
          >
            <Lock size={14} /> Verify by Pasted SHA-256 Hash
          </button>
          <button
            type="button"
            onClick={() => setSearchMode('QR')}
            className={`btn-sm ${searchMode === 'QR' ? 'btn-primary' : 'btn-secondary'}`}
          >
            <QrCode size={14} /> Scan QR Certificate
          </button>
        </div>

        {searchMode === 'QR' ? (
          <div className="qr-scan-preview-box glass-card" style={{ maxWidth: '520px', margin: '0 auto 20px', padding: '24px', border: '1px dashed rgba(99, 102, 241, 0.4)' }}>
            <QrCode size={48} className="text-primary" style={{ margin: '0 auto 12px' }} />
            <h3 style={{ margin: '0 0 6px' }}>Digital Certificate QR Scanner</h3>
            <p className="text-secondary text-xs" style={{ margin: '0 0 16px' }}>
              Hold the physical degree certificate QR code up to your scanner camera, or test simulated scans:
            </p>
            {isScanningQr ? (
              <div style={{ padding: '16px', color: '#6366f1' }}>
                <span className="dot online" style={{ marginRight: '8px' }} />
                Reading high-density encrypted QR matrix...
              </div>
            ) : (
              <div style={{ display: 'flex', gap: '10px', justifyContent: 'center', flexWrap: 'wrap' }}>
                {issuedDocuments.slice(0, 2).map((doc, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => handleSimulateQrScan(doc)}
                    className="btn-primary btn-xs"
                  >
                    Scan QR for {doc.code} ({doc.status})
                  </button>
                ))}
              </div>
            )}
          </div>
        ) : (
          <form onSubmit={handleVerify} className="verifier-search-bar">
            <div className="search-input-wrapper">
              <Search size={18} className="search-icon-left text-muted" />
              <input 
                type="text" 
                placeholder={searchMode === 'HASH' ? "Paste SHA-256 file checksum (e.g. sha256_a1b2c3d4...)" : "Enter 16-character document verification code (e.g. DOC-2026-CS-8921)"}
                value={searchCode}
                onChange={e => setSearchCode(e.target.value)}
                className="verifier-input font-mono"
                required
              />
            </div>
            <button type="submit" className="btn-primary verifier-submit-btn">
              Verify Document
            </button>
          </form>
        )}

        <div className="verifier-sample-codes">
          <span className="text-xs text-muted">Try sample credentials:</span>
          <button 
            type="button" 
            onClick={() => { setSearchMode('CODE'); setSearchCode('DOC-2026-CS-8921'); setSearchedDoc(issuedDocuments.find(d => d.code === 'DOC-2026-CS-8921')); }}
            className="sample-chip"
          >
            DOC-2026-CS-8921 (Valid Degree)
          </button>
          <button 
            type="button" 
            onClick={() => { 
              setSearchMode('HASH'); 
              const doc = issuedDocuments[0];
              if (doc) {
                setSearchCode(doc.fileHash);
                setSearchedDoc(doc);
              }
            }}
            className="sample-chip"
          >
            Pasted SHA-256 Hash
          </button>
          <button 
            type="button" 
            onClick={() => { setSearchMode('CODE'); setSearchCode('DOC-2025-BC-1102'); setSearchedDoc(issuedDocuments.find(d => d.code === 'DOC-2025-BC-1102')); }}
            className="sample-chip"
          >
            DOC-2025-BC-1102 (Revoked NOC)
          </button>
          <button 
            type="button" 
            onClick={() => { setSearchMode('CODE'); setSearchCode('DOC-UNKNOWN-0000'); setSearchedDoc(null); }}
            className="sample-chip"
          >
            DOC-UNKNOWN-0000 (Invalid Code)
          </button>
        </div>
      </div>

      {/* Verification Result Card */}
      {hasSearched && (
        <div className="verification-result-wrapper mt-8 animate-fade-in">
          {searchedDoc ? (
            <div className={`verification-card glass-card ${searchedDoc.status === 'VALID' ? 'status-valid' : 'status-revoked'}`}>
              <div className="verification-status-banner">
                {searchedDoc.status === 'VALID' ? (
                  <>
                    <CheckCircle size={32} className="text-success" />
                    <div>
                      <h2>OFFICIALLY VERIFIED &amp; VALID</h2>
                      <p>This credential was issued by the authorized university senate and remains authentic.</p>
                    </div>
                  </>
                ) : (
                  <>
                    <AlertOctagon size={32} className="text-danger" />
                    <div>
                      <h2>REVOKED CREDENTIAL</h2>
                      <p>This document has been invalidated by college authority. Revocation reason provided below.</p>
                    </div>
                  </>
                )}
              </div>

              <div className="doc-details-grid">
                <div className="detail-item">
                  <span className="detail-label">Verification Tracking ID</span>
                  <strong className="detail-value font-mono">{searchedDoc.code}</strong>
                </div>

                <div className="detail-item">
                  <span className="detail-label">Document Classification</span>
                  <strong className="detail-value">{searchedDoc.type}</strong>
                </div>

                <div className="detail-item">
                  <span className="detail-label">Candidate Name (Privacy Masked)</span>
                  <strong className="detail-value">{searchedDoc.maskedStudentName}</strong>
                </div>

                <div className="detail-item">
                  <span className="detail-label">Department</span>
                  <strong className="detail-value">{searchedDoc.department}</strong>
                </div>

                <div className="detail-item">
                  <span className="detail-label">Date of Senate Issue</span>
                  <strong className="detail-value">{searchedDoc.issueDate}</strong>
                </div>

                <div className="detail-item">
                  <span className="detail-label">Authorized Signatory</span>
                  <strong className="detail-value">{searchedDoc.authorizedSignatory}</strong>
                </div>

                {searchedDoc.status === 'REVOKED' && (
                  <div className="detail-item full-width-revocation">
                    <span className="detail-label text-danger">Official Revocation Reason</span>
                    <strong className="detail-value text-danger">"{searchedDoc.revokedReason}"</strong>
                  </div>
                )}

                <div className="detail-item full-width">
                  <span className="detail-label">Cryptographic Fingerprint (SHA-256 Checksum)</span>
                  <code className="detail-value font-mono text-xs">{searchedDoc.fileHash}</code>
                </div>
              </div>

              <div className="privacy-assurance-footer">
                <Lock size={14} className="text-muted" />
                <span>Compliant with DPDP Act 2023: Personal identifiers are masked on public verification views.</span>
              </div>
            </div>
          ) : (
            <div className="glass-card text-center p-8 border-danger">
              <XCircle size={44} className="text-danger mx-auto mb-3" />
              <h3>DOCUMENT NOT FOUND</h3>
              <p className="text-secondary max-w-md mx-auto mb-4">
                No issued record matches the code <code>{searchCode}</code> in the official college ledger. The document may be counterfeit or the tracking number was incorrectly entered.
              </p>
              <button 
                onClick={() => setSearchCode('')} 
                className="btn-secondary"
              >
                Clear &amp; Try Another Code
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
