import React, { useState, useEffect, useRef } from 'react';
import { 
  Camera, 
  QrCode, 
  CheckCircle2, 
  AlertTriangle, 
  Clock, 
  Lock, 
  RefreshCw, 
  UserCheck, 
  ShieldAlert, 
  FileCheck,
  Send,
  Eye,
  Edit3,
  MapPin,
  Sparkles,
  Maximize2
} from 'lucide-react';
import { getRotatingQRToken, calculateHaversineDistance } from '../utils/cryptoAndGeo';

export default function FacultyView({
  sessions,
  setSessions,
  subjects,
  rooms,
  users,
  marks,
  setMarks,
  markCorrections,
  setMarkCorrections,
  onAddAuditLog
}) {
  const [activeTab, setActiveTab] = useState('attendance');
  const [selectedSubjectId, setSelectedSubjectId] = useState(12);
  const [selectedRoomId, setSelectedRoomId] = useState(7);
  const [activeSession, setActiveSession] = useState(sessions[0] || null);

  // Live Camera state
  const [isCameraActive, setIsCameraActive] = useState(false);
  const [capturedPhotoUrl, setCapturedPhotoUrl] = useState(null);
  const [isAnalyzingFaces, setIsAnalyzingFaces] = useState(false);
  const videoRef = useRef(null);

  // Rotating QR code token state
  const [qrTokenData, setQrTokenData] = useState({ token: 'IC-INIT-8942', secondsLeft: 15 });
  const [isProjectorModalOpen, setIsProjectorModalOpen] = useState(false);

  // Marks management state
  const [selectedExam, setSelectedExam] = useState('Mid-Term Examination');
  const [currentMarkList, setCurrentMarkList] = useState(marks);
  const [correctionModal, setCorrectionModal] = useState({ open: false, markId: null, currentVal: 0 });
  const [correctionNewMarks, setCorrectionNewMarks] = useState(0);
  const [correctionReason, setCorrectionReason] = useState('');

  // Manual attendance override modal
  const [overrideModal, setOverrideModal] = useState({ open: false, studentId: null, currentStatus: '' });
  const [overrideNewStatus, setOverrideNewStatus] = useState('PRESENT');
  const [overrideReason, setOverrideReason] = useState('');

  // 15-second dynamic QR token rotator
  useEffect(() => {
    const timer = setInterval(() => {
      if (activeSession) {
        setQrTokenData(getRotatingQRToken(activeSession.id));
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [activeSession]);

  // Handle Camera start/stop
  const handleToggleCamera = async () => {
    if (isCameraActive) {
      if (videoRef.current && videoRef.current.srcObject) {
        videoRef.current.srcObject.getTracks().forEach(track => track.stop());
      }
      setIsCameraActive(false);
    } else {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
        }
        setIsCameraActive(true);
      } catch (err) {
        console.warn('Camera access error, fallback to simulated classroom feed', err);
        setIsCameraActive(true);
      }
    }
  };

  // Capture Photo & Run AI Pipeline
  const handleCapturePhoto = () => {
    setIsAnalyzingFaces(true);
    // Simulate real snapshot capture
    setTimeout(() => {
      setCapturedPhotoUrl('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360"><rect width="640" height="360" fill="%231e293b"/><text x="50%" y="45%" fill="%2394a3b8" font-size="20" font-family="sans-serif" text-anchor="middle">Classroom Capture - Live Camera Feed</text><text x="50%" y="55%" fill="%2338bdf8" font-size="14" font-family="sans-serif" text-anchor="middle">Challenge Code: K7Q2 | 4 Faces Detected</text></svg>');
      setIsAnalyzingFaces(false);
      handleToggleCamera();

      // Update session records with simulated AI detections
      if (activeSession) {
        const updatedRecords = activeSession.attendanceRecords.map(rec => {
          if (rec.studentId === 6) {
            return { ...rec, faceScore: 0.92, spoofScore: 0.96, finalStatus: 'MATCHED' };
          }
          return rec;
        });

        const updatedSession = {
          ...activeSession,
          capturedPhoto: true,
          attendanceRecords: updatedRecords
        };

        setActiveSession(updatedSession);
        setSessions(sessions.map(s => s.id === updatedSession.id ? updatedSession : s));

        onAddAuditLog({
          action: 'CAPTURE_PHOTO',
          entityType: 'ATTENDANCE_SESSION',
          entityId: String(activeSession.id),
          oldValue: '{}',
          newValue: JSON.stringify({ facesDetected: 4, geofencePassed: true }),
          reason: `Faculty captured live classroom photo; Geofence verified within Room 204 (12m < 50m)`
        });
      }
    }, 1200);
  };

  // Start New Attendance Session
  const handleStartSession = () => {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let code = '';
    for (let i = 0; i < 4; i++) {
      code += chars.charAt(Math.floor(Math.random() * chars.length));
    }

    const room = rooms.find(r => r.id === selectedRoomId);
    const subject = subjects.find(s => s.id === selectedSubjectId);

    const newSess = {
      id: Date.now(),
      subjectId: selectedSubjectId,
      subjectName: subject?.name || 'Class',
      division: 'Div A',
      roomId: selectedRoomId,
      roomName: room?.name || 'Room',
      facultyId: 3,
      challengeCode: code,
      status: 'OPEN',
      startedAt: new Date().toISOString(),
      photoWindowEndsAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
      capturedPhoto: null,
      attendanceRecords: users.filter(u => u.role === 'STUDENT').map(st => ({
        studentId: st.id,
        studentName: st.name,
        rollNo: st.rollNo,
        faceScore: 0,
        spoofScore: 0,
        confirmed: false,
        confirmedAt: null,
        finalStatus: 'PENDING',
        statusReason: null
      }))
    };

    setActiveSession(newSess);
    setSessions([newSess, ...sessions]);

    onAddAuditLog({
      action: 'START_SESSION',
      entityType: 'ATTENDANCE_SESSION',
      entityId: String(newSess.id),
      oldValue: '{}',
      newValue: JSON.stringify({ code: newSess.challengeCode, subject: newSess.subjectName }),
      reason: `Faculty initiated attendance session with challenge code ${newSess.challengeCode} (ATT-02)`
    });
  };

  // Finalise Session
  const handleFinaliseSession = () => {
    if (!activeSession) return;

    const lockedSession = {
      ...activeSession,
      status: 'FINALISED',
      finalisedAt: new Date().toISOString(),
      attendanceRecords: activeSession.attendanceRecords.map(r => {
        // If face matched and student confirmed -> PRESENT
        if (r.faceScore >= 0.5 && r.confirmed) {
          return { ...r, finalStatus: 'PRESENT' };
        }
        if (r.finalStatus === 'PENDING') {
          return { ...r, finalStatus: 'ABSENT' };
        }
        return r;
      })
    };

    setActiveSession(lockedSession);
    setSessions(sessions.map(s => s.id === lockedSession.id ? lockedSession : s));

    onAddAuditLog({
      action: 'FINALISE_SESSION',
      entityType: 'ATTENDANCE_SESSION',
      entityId: String(activeSession.id),
      oldValue: '{"status":"OPEN"}',
      newValue: '{"status":"FINALISED"}',
      reason: `Attendance session #${activeSession.id} locked & finalised by Prof. Verma. Records committed to DB.`
    });
    alert('Attendance session successfully locked and finalised. Records committed to audit log.');
  };

  // Handle Manual Override
  const handleSubmitOverride = (e) => {
    e.preventDefault();
    if (!overrideReason || overrideReason.trim().length < 10) {
      alert('A justification reason of at least 10 characters is mandatory (ATT-10).');
      return;
    }

    const updatedRecords = activeSession.attendanceRecords.map(rec => {
      if (rec.studentId === overrideModal.studentId) {
        return {
          ...rec,
          finalStatus: overrideNewStatus,
          statusReason: `Override: ${overrideReason}`
        };
      }
      return rec;
    });

    const updatedSess = { ...activeSession, attendanceRecords: updatedRecords };
    setActiveSession(updatedSess);
    setSessions(sessions.map(s => s.id === updatedSess.id ? updatedSess : s));

    const st = users.find(u => u.id === overrideModal.studentId);
    onAddAuditLog({
      action: 'OVERRIDE_ATTENDANCE',
      entityType: 'ATTENDANCE_RECORD',
      entityId: `${activeSession.id}_${overrideModal.studentId}`,
      oldValue: JSON.stringify({ status: overrideModal.currentStatus }),
      newValue: JSON.stringify({ status: overrideNewStatus }),
      reason: `Faculty override for ${st?.name}: ${overrideReason}`
    });

    setOverrideModal({ open: false, studentId: null, currentStatus: '' });
    alert(`Status overridden to ${overrideNewStatus} with logged audit justification.`);
  };

  // Publish Marks (locks marks)
  const handlePublishMarks = () => {
    setCurrentMarkList(currentMarkList.map(m => ({ ...m, status: 'PUBLISHED' })));
    setMarks(marks.map(m => ({ ...m, status: 'PUBLISHED' })));

    onAddAuditLog({
      action: 'PUBLISH_MARKS',
      entityType: 'MARK_ENTRIES',
      entityId: 'EXAM_' + selectedExam.replace(/\s+/g, '_'),
      oldValue: '{"status":"DRAFT"}',
      newValue: '{"status":"PUBLISHED"}',
      reason: `Official publishing and locking of ${selectedExam} marks (MRK-05)`
    });
    alert(`${selectedExam} marks successfully published and locked. Further changes will require HOD correction approval.`);
  };

  // Request Mark Correction to HOD
  const handleSubmitCorrectionRequest = (e) => {
    e.preventDefault();
    if (!correctionReason || correctionReason.trim().length < 10) {
      alert('Correction reason must be at least 10 characters.');
      return;
    }

    const targetMark = marks.find(m => m.id === correctionModal.markId);
    const newCorrection = {
      id: Date.now(),
      markId: correctionModal.markId,
      studentName: targetMark?.studentName || 'Student',
      rollNo: targetMark?.rollNo || 'CS-104',
      subjectCode: targetMark?.subjectCode || 'CS-302',
      exam: targetMark?.exam || selectedExam,
      oldMarks: targetMark?.marks || 0,
      newMarks: Number(correctionNewMarks),
      facultyName: 'Prof. Vikram Verma',
      reason: correctionReason,
      status: 'PENDING_HOD',
      requestedAt: new Date().toISOString(),
      decisionComment: null
    };

    setMarkCorrections([newCorrection, ...markCorrections]);
    onAddAuditLog({
      action: 'REQUEST_CORRECTION',
      entityType: 'MARK_CORRECTION',
      entityId: String(newCorrection.id),
      oldValue: JSON.stringify({ marks: targetMark?.marks }),
      newValue: JSON.stringify({ proposedMarks: newCorrection.newMarks }),
      reason: `Faculty requested HOD correction approval: ${correctionReason}`
    });

    setCorrectionModal({ open: false, markId: null, currentVal: 0 });
    alert('Correction request dispatched to HOD Dr. Sharma for approval.');
  };

  return (
    <div className="faculty-container animate-fade-in">
      <div className="section-header">
        <div>
          <h1 className="page-title">Faculty Academic Portal</h1>
          <p className="page-desc">
            Class attendance session management with multi-factor photo &amp; rotating QR verification, marks grading, and HOD correction workflows.
          </p>
        </div>
        <div className="header-meta-badge">
          <span className="dot online" /> Prof. Vikram Verma (Dept of Computer Science)
        </div>
      </div>

      {/* Tabs */}
      <div className="sub-nav-tabs">
        <button 
          onClick={() => setActiveTab('attendance')} 
          className={`tab-btn ${activeTab === 'attendance' ? 'active' : ''}`}
        >
          <Camera size={16} /> Live Attendance Engine
        </button>
        <button 
          onClick={() => setActiveTab('marks')} 
          className={`tab-btn ${activeTab === 'marks' ? 'active' : ''}`}
        >
          <Edit3 size={16} /> Enter &amp; Publish Marks
        </button>
      </div>

      {/* TAB 1: ATTENDANCE WORKFLOW */}
      {activeTab === 'attendance' && (
        <div className="tab-pane animate-fade-in">
          {/* Active Session Control Bar */}
          <div className="session-status-banner glass-card">
            <div className="session-header-col">
              <div className="session-title-wrap">
                <span className="badge-pill info">CS-302 Div A</span>
                <h2>{activeSession?.subjectName || 'Select Class & Room'}</h2>
                <div className="session-meta">
                  <span><MapPin size={14} /> {activeSession?.roomName} (Lat: 18.5204, Lng: 73.8567)</span>
                  <span><Clock size={14} /> 10-Min Photo Window</span>
                </div>
              </div>
            </div>

            <div className="session-code-col">
              <div className="challenge-code-box">
                <span className="code-label">Classroom Challenge Code:</span>
                <span className="code-display font-mono">{activeSession?.challengeCode || '----'}</span>
                <span className="code-sub">Display on blackboard for photo binding</span>
              </div>
            </div>

            <div className="session-actions-col">
              {activeSession?.status === 'OPEN' ? (
                <button onClick={handleFinaliseSession} className="btn-primary">
                  <Lock size={15} /> Finalise &amp; Lock Session
                </button>
              ) : (
                <button onClick={handleStartSession} className="btn-secondary">
                  <RefreshCw size={15} /> Start New Session
                </button>
              )}
            </div>
          </div>

          <div className="grid-two-col">
            {/* Live Camera & Geofence Photo Capture */}
            <div className="glass-card">
              <div className="card-header-flex">
                <h3>Live Classroom Camera</h3>
                <span className="badge-pill success">
                  <CheckCircle2 size={12} /> Geofence: Inside Room 204 (12m &lt; 50m)
                </span>
              </div>
              <p className="card-desc">
                Capture live classroom photo with challenge code visible (`ATT-03`). File upload bypass disabled.
              </p>

              <div className="camera-feed-box">
                {isCameraActive ? (
                  <div className="video-viewport">
                    <video ref={videoRef} autoPlay playsInline className="live-video-element" />
                    <div className="camera-overlay-hud">
                      <div className="hud-code">CODE: {activeSession?.challengeCode}</div>
                      <div className="hud-scanner-bar" />
                    </div>
                  </div>
                ) : capturedPhotoUrl ? (
                  <div className="captured-preview-wrap">
                    <img src={capturedPhotoUrl} alt="Captured Classroom" className="captured-image" />
                    <div className="face-bounding-box-demo" style={{ top: '35%', left: '25%', width: '12%', height: '22%' }}>
                      <span className="face-tag">Aarav (94%)</span>
                    </div>
                    <div className="face-bounding-box-demo" style={{ top: '38%', left: '45%', width: '12%', height: '22%' }}>
                      <span className="face-tag">Rohan (91%)</span>
                    </div>
                  </div>
                ) : (
                  <div className="camera-standby">
                    <Camera size={48} className="text-muted" />
                    <p>Camera standby. Click below to initialize live viewfinder.</p>
                  </div>
                )}
              </div>

              <div className="camera-controls">
                <button 
                  onClick={handleToggleCamera} 
                  className={`btn-secondary ${isCameraActive ? 'active' : ''}`}
                >
                  <Camera size={16} /> {isCameraActive ? 'Turn Off Camera' : 'Open Live Camera'}
                </button>
                {isCameraActive && (
                  <button 
                    onClick={handleCapturePhoto} 
                    disabled={isAnalyzingFaces}
                    className="btn-primary animate-pulse-glow"
                  >
                    <Sparkles size={16} /> {isAnalyzingFaces ? 'Analyzing Faces...' : 'Capture Photo & Analyze'}
                  </button>
                )}
              </div>
            </div>

            {/* Rotating QR Fallback Token Display */}
            <div className="glass-card">
              <div className="card-header-flex">
                <h3>Dynamic QR Fallback (15s Rotation)</h3>
                <span className="badge-pill warning">Slot: {qrTokenData.slot}</span>
              </div>
              <p className="card-desc">
                Rotating dynamic QR code token for students whose faces were not recognized (`ATT-11`). Valid for 30s.
              </p>

              <div className="qr-display-container">
                <div className="qr-box-inner">
                  {/* Visual QR representation */}
                  <div className="mock-qr-code">
                    <QrCode size={130} className="text-primary" />
                    <div className="qr-center-logo">IC</div>
                  </div>
                  <div className="qr-token-pill font-mono">{qrTokenData.token}</div>
                </div>

                <div className="qr-timer-bar-wrap">
                  <div className="qr-timer-label">
                    <span>Code refreshes in:</span>
                    <strong className="text-primary font-mono">{qrTokenData.secondsLeft}s</strong>
                  </div>
                  <div className="qr-progress-track">
                    <div 
                      className="qr-progress-fill" 
                      style={{ width: `${(qrTokenData.secondsLeft / 15) * 100}%` }}
                    />
                  </div>
                </div>

                <button 
                  type="button"
                  onClick={() => setIsProjectorModalOpen(true)}
                  className="btn-secondary btn-sm mt-4 w-full flex items-center justify-center gap-2"
                  style={{ width: '100%', marginTop: '16px' }}
                >
                  <Maximize2 size={14} /> Project Dynamic QR Fullscreen
                </button>
              </div>
            </div>
          </div>

          {/* Real-Time Attendance Review Table (Four Lists) */}
          <div className="glass-card mt-6">
            <div className="card-header-flex">
              <div>
                <h3>Attendance Review &amp; Exception Handling</h3>
                <p className="card-desc">
                  Two independent signals required: Face match &ge; 0.50 + student confirmation inside geofence within 2 minutes (`ATT-08`).
                </p>
              </div>
              <div className="attendance-kpi-pills">
                <span className="badge-pill success">
                  Present: {activeSession?.attendanceRecords.filter(r => r.finalStatus === 'PRESENT').length}
                </span>
                <span className="badge-pill warning">
                  Review: {activeSession?.attendanceRecords.filter(r => r.finalStatus === 'REVIEW').length}
                </span>
                <span className="badge-pill danger">
                  Absent: {activeSession?.attendanceRecords.filter(r => r.finalStatus === 'ABSENT').length}
                </span>
              </div>
            </div>

            <div className="table-card">
              <table className="custom-table">
                <thead>
                  <tr>
                    <th>Roll No</th>
                    <th>Student Name</th>
                    <th>Face Match Score</th>
                    <th>Spoof Liveness</th>
                    <th>Student Confirmed</th>
                    <th>Status</th>
                    <th>Teacher Review &amp; Override</th>
                  </tr>
                </thead>
                <tbody>
                  {activeSession?.attendanceRecords.map(rec => (
                    <tr key={rec.studentId}>
                      <td className="font-mono">{rec.rollNo}</td>
                      <td><strong>{rec.studentName}</strong></td>
                      <td>
                        <span className={rec.faceScore >= 0.5 ? 'text-success font-mono' : 'text-warning font-mono'}>
                          {(rec.faceScore * 100).toFixed(0)}%
                        </span>
                      </td>
                      <td>
                        <span className={rec.spoofScore >= 0.6 ? 'text-success font-mono' : 'text-danger font-mono'}>
                          {(rec.spoofScore * 100).toFixed(0)}%
                        </span>
                      </td>
                      <td>
                        {rec.confirmed ? (
                          <span className="badge-pill success"><CheckCircle2 size={12} /> Confirmed (12m in room)</span>
                        ) : (
                          <span className="badge-pill muted"><Clock size={12} /> Awaiting (2 min)</span>
                        )}
                      </td>
                      <td>
                        <span className={`badge-pill ${
                          rec.finalStatus === 'PRESENT' ? 'success' :
                          rec.finalStatus === 'MATCHED' ? 'info' :
                          rec.finalStatus === 'REVIEW' ? 'warning' : 'danger'
                        }`}>
                          {rec.finalStatus}
                        </span>
                      </td>
                      <td>
                        <button 
                          onClick={() => setOverrideModal({ open: true, studentId: rec.studentId, currentStatus: rec.finalStatus })}
                          className="btn-secondary btn-xs"
                          title="Override with logged justification"
                        >
                          <Edit3 size={12} /> Override Status
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: MARKS MANAGEMENT */}
      {activeTab === 'marks' && (
        <div className="tab-pane animate-fade-in">
          <div className="pane-toolbar">
            <div className="filter-group">
              <label>Select Examination:</label>
              <select value={selectedExam} onChange={e => setSelectedExam(e.target.value)}>
                <option value="Mid-Term Examination">Mid-Term Examination (Max: 30)</option>
                <option value="Lab Assignment 1">Lab Assignment 1 (Max: 20)</option>
              </select>
            </div>
            <div className="toolbar-actions">
              <button onClick={handlePublishMarks} className="btn-primary">
                <Lock size={15} /> Publish &amp; Lock Marks
              </button>
            </div>
          </div>

          <div className="table-card">
            <table className="custom-table">
              <thead>
                <tr>
                  <th>Roll No</th>
                  <th>Student Name</th>
                  <th>Course</th>
                  <th>Score Obtained</th>
                  <th>Max Marks</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {marks.filter(m => m.exam === selectedExam).map(m => (
                  <tr key={m.id}>
                    <td className="font-mono">{m.rollNo}</td>
                    <td><strong>{m.studentName}</strong></td>
                    <td>{m.subjectCode}</td>
                    <td>
                      <span className="font-mono font-bold text-primary">{m.marks}</span>
                    </td>
                    <td className="font-mono">{m.maxMarks}</td>
                    <td>
                      <span className={`badge-pill ${m.status === 'PUBLISHED' ? 'success' : 'warning'}`}>
                        {m.status === 'PUBLISHED' ? '🔒 LOCKED (PUBLISHED)' : '📝 DRAFT'}
                      </span>
                    </td>
                    <td>
                      {m.status === 'PUBLISHED' ? (
                        <button 
                          onClick={() => {
                            setCorrectionModal({ open: true, markId: m.id, currentVal: m.marks });
                            setCorrectionNewMarks(m.marks);
                          }}
                          className="btn-secondary btn-xs"
                        >
                          Request Correction
                        </button>
                      ) : (
                        <span className="text-muted text-xs">Directly Editable</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Override Attendance Modal */}
      {overrideModal.open && (
        <div className="modal-backdrop">
          <div className="modal-dialog animate-fade-in">
            <div className="modal-header">
              <h3>Faculty Attendance Override</h3>
              <button onClick={() => setOverrideModal({ open: false, studentId: null, currentStatus: '' })} className="modal-close">&times;</button>
            </div>
            <form onSubmit={handleSubmitOverride} className="form-stack">
              <p className="modal-instruction">
                As required by BRU-04 and ATT-10, any manual status modification requires a logged justification reason of at least 10 characters.
              </p>
              <div className="form-group">
                <label>Target Status</label>
                <select value={overrideNewStatus} onChange={e => setOverrideNewStatus(e.target.value)}>
                  <option value="PRESENT">Mark PRESENT (Teacher Verified in Class)</option>
                  <option value="ABSENT">Mark ABSENT</option>
                  <option value="REVIEW">Mark Under REVIEW</option>
                </select>
              </div>
              <div className="form-group">
                <label>Justification Reason</label>
                <textarea 
                  rows={3}
                  placeholder="e.g. Student present in seat row 3; camera glare prevented face embedding match..."
                  value={overrideReason}
                  onChange={e => setOverrideReason(e.target.value)}
                  required
                />
              </div>
              <div className="modal-footer">
                <button type="button" onClick={() => setOverrideModal({ open: false, studentId: null, currentStatus: '' })} className="btn-secondary">
                  Cancel
                </button>
                <button type="submit" className="btn-primary">
                  Save Override &amp; Write Audit Log
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Mark Correction Modal */}
      {correctionModal.open && (
        <div className="modal-backdrop">
          <div className="modal-dialog animate-fade-in">
            <div className="modal-header">
              <h3>Submit Mark Correction Proposal to HOD</h3>
              <button onClick={() => setCorrectionModal({ open: false, markId: null, currentVal: 0 })} className="modal-close">&times;</button>
            </div>
            <form onSubmit={handleSubmitCorrectionRequest} className="form-stack">
              <div className="form-group">
                <label>Current Marks</label>
                <input type="number" value={correctionModal.currentVal} disabled />
              </div>
              <div className="form-group">
                <label>Proposed New Marks</label>
                <input 
                  type="number" 
                  value={correctionNewMarks} 
                  onChange={e => setCorrectionNewMarks(e.target.value)} 
                  required 
                />
              </div>
              <div className="form-group">
                <label>Evaluation Correction Justification</label>
                <textarea 
                  rows={4}
                  placeholder="e.g. Re-evaluation of question 3 totaling reveals 7 marks omitted during sheet entry..."
                  value={correctionReason}
                  onChange={e => setCorrectionReason(e.target.value)}
                  required
                />
              </div>
              <div className="modal-footer">
                <button type="button" onClick={() => setCorrectionModal({ open: false, markId: null, currentVal: 0 })} className="btn-secondary">
                  Cancel
                </button>
                <button type="submit" className="btn-primary">
                  Submit to HOD Dr. Sharma
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Fullscreen Dynamic QR Projector Modal */}
      {isProjectorModalOpen && (
        <div className="modal-backdrop projector-backdrop">
          <div className="modal-dialog projector-dialog animate-fade-in" style={{ maxWidth: '600px', textAlign: 'center', padding: '32px' }}>
            <div className="modal-header" style={{ borderBottom: 'none', paddingBottom: '0' }}>
              <div>
                <span className="badge-pill info">Classroom Live Projection Mode</span>
                <h2 style={{ fontSize: '1.4rem', marginTop: '8px' }}>{activeSession?.subjectName} ({activeSession?.roomName})</h2>
              </div>
              <button type="button" onClick={() => setIsProjectorModalOpen(false)} className="modal-close">&times;</button>
            </div>
            
            <div className="projector-qr-wrapper" style={{ margin: '24px auto', padding: '24px', background: '#ffffff', borderRadius: '16px', display: 'inline-block', boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.4)' }}>
              <QrCode size={220} color="#0f172a" />
              <div style={{ marginTop: '12px', fontWeight: 'bold', fontSize: '1.3rem', letterSpacing: '2px', color: '#0284c7', fontFamily: 'monospace' }}>
                {qrTokenData.token}
              </div>
            </div>

            <div style={{ maxWidth: '420px', margin: '0 auto' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '0.9rem', color: '#94a3b8' }}>
                <span>Dynamic rotating token refreshes in:</span>
                <strong style={{ color: '#38bdf8', fontFamily: 'monospace', fontSize: '1.1rem' }}>{qrTokenData.secondsLeft}s</strong>
              </div>
              <div className="qr-progress-track" style={{ height: '8px', background: 'rgba(255,255,255,0.1)', borderRadius: '999px', overflow: 'hidden' }}>
                <div 
                  className="qr-progress-fill" 
                  style={{ 
                    width: `${(qrTokenData.secondsLeft / 15) * 100}%`,
                    height: '100%',
                    background: 'linear-gradient(90deg, #38bdf8, #818cf8)',
                    transition: 'width 1s linear'
                  }}
                />
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'center', gap: '16px', marginTop: '24px' }}>
              <div className="challenge-code-box" style={{ padding: '8px 16px' }}>
                <span className="code-label" style={{ fontSize: '0.75rem' }}>Challenge Code:</span>
                <span className="code-display font-mono" style={{ fontSize: '1.2rem', color: '#f59e0b' }}>{activeSession?.challengeCode || '----'}</span>
              </div>
              <div className="challenge-code-box" style={{ padding: '8px 16px' }}>
                <span className="code-label" style={{ fontSize: '0.75rem' }}>Verified Students:</span>
                <span className="code-display font-mono" style={{ fontSize: '1.2rem', color: '#10b981' }}>
                  {activeSession?.attendanceRecords?.filter(r => r.finalStatus === 'PRESENT').length} / {activeSession?.attendanceRecords?.length}
                </span>
              </div>
            </div>

            <button type="button" onClick={() => setIsProjectorModalOpen(false)} className="btn-secondary mt-6" style={{ width: '100%', marginTop: '24px' }}>
              Exit Projector Mode
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
