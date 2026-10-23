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
import api from '../api/client';

export default function PublicVerifierView({ issuedDocuments = [] }) {
  const [searchMode, setSearchMode] = useState('CODE'); // 'CODE', 'HASH', 'QR'
  const [searchCode, setSearchCode] = useState('');
  const [searchedDoc, setSearchedDoc] = useState(null);
  const [hasSearched, setHasSearched] = useState(false);
  const [isScanningQr, setIsScanningQr] = useState(false);

  const handleVerify = async (e) => {
    if (e) e.preventDefault();
    if (!searchCode.trim()) return;

    const trimmed = searchCode.trim();
    let found = (issuedDocuments || []).find(d =>
      d.code?.toUpperCase() === trimmed.toUpperCase() ||
      d.fileHash?.toLowerCase() === trimmed.toLowerCase() ||
      d.fileHash?.replace(/^sha256_/, '').toLowerCase() === trimmed.toLowerCase() ||
      trimmed.toLowerCase().includes(d.code?.toLowerCase())
    );

    if (!found) {
      try {
        const res = await api.verifyDocument(trimmed);
        if (res && res.code) {
          found = res;
        }
      } catch (err) {
        // Backend returned 404 or document not found
      }
    }

    setSearchedDoc(found || null);
    setHasSearched(true);
  };

  const handleSimulateQrScan = (doc) => {
    setIsScanningQr(true);
    setTimeout(() => {
      setSearchCode(doc.code);
      setSearchedDoc(doc);
      setHasSearched(true);
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
            onClick={() => { setSearchMode('CODE'); setSearchCode(''); }}
            className={`btn-sm ${searchMode === 'CODE' ? 'btn-primary' : 'btn-secondary'}`}
          >
            <Search size={14} /> Verify by Document Code
          </button>
          <button
            type="button"
            onClick={() => {
              setSearchMode('HASH');
              const validDoc = issuedDocuments[0];
              setSearchCode(validDoc ? validDoc.fileHash : '');
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
              Hold the physical degree certificate QR code up to your scanner camera, or select an issued credential:
            </p>
            {isScanningQr ? (
              <div style={{ padding: '16px', color: '#6366f1' }}>
                <span className="dot online" style={{ marginRight: '8px' }} />
                Reading high-density encrypted QR matrix...
              </div>
            ) : (
              <div style={{ display: 'flex', gap: '10px', justifyContent: 'center', flexWrap: 'wrap' }}>
                {issuedDocuments && issuedDocuments.length > 0 ? (
                  issuedDocuments.slice(0, 3).map((doc, idx) => (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => handleSimulateQrScan(doc)}
                      className="btn-primary btn-xs"
                    >
                      Scan QR for {doc.code} ({doc.status})
                    </button>
                  ))
                ) : (
                  <p className="text-xs text-muted">No issued documents in database to scan.</p>
                )}
              </div>
            )}
          </div>
        ) : (
          <form onSubmit={handleVerify} className="verifier-search-bar">
            <div className="search-input-wrapper">
              <Search size={18} className="search-icon-left text-muted" />
              <input
                type="text"
                placeholder={searchMode === 'HASH' ? "Paste SHA-256 file checksum (e.g. 9f83a1b2...)" : "Enter document verification code (e.g. DOC-2026-CS-8921)"}
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

        {issuedDocuments && issuedDocuments.length > 0 && (
          <div className="verifier-sample-codes">
            <span className="text-xs text-muted">Available issued credentials:</span>
            {issuedDocuments.slice(0, 3).map((d) => (
              <button
                key={d.code}
                type="button"
                onClick={() => {
                  setSearchMode('CODE');
                  setSearchCode(d.code);
                  setSearchedDoc(d);
                  setHasSearched(true);
                }}
                className="sample-chip"
              >
                {d.code} ({d.type} - {d.status})
              </button>
            ))}
          </div>
        )}
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
