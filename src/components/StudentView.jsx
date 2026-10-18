import React, { useState } from 'react';
import { 
  CheckCircle2, 
  Clock, 
  MapPin, 
  FileText, 
  MessageSquare, 
  AlertTriangle, 
  Calendar, 
  Send, 
  Download, 
  ShieldCheck, 
  Sparkles, 
  ExternalLink, 
  ChevronRight, 
  RefreshCw, 
  Bell,
  LogOut 
} from 'lucide-react';
import { calculateHaversineDistance, validateGeofence } from '../utils/cryptoAndGeo';

export default function StudentView({
  currentUser,
  activeSession,
  sessions,
  setSessions,
  marks,
  leaveRequests,
  setLeaveRequests,
  notices,
  issuedDocuments,
  onOpenChat,
  onAddAuditLog,
  onLogout
}) {
  const [activeTab, setActiveTab] = useState('dashboard');

  // Leave application state
  const [leaveForm, setLeaveForm] = useState({
    fromDate: '',
    toDate: '',
    reason: '',
    attachmentName: 'medical_certificate.pdf'
  });

  // Dispute attendance modal state
  const [disputeModal, setDisputeModal] = useState({ open: false, sessionId: null });
  const [disputeComment, setDisputeComment] = useState('');

  // Find attendance record for current student in active session
  const studentSessionRecord = activeSession?.attendanceRecords?.find(
    r => r.studentId === currentUser.id
  );

  // Student Device GPS and Geofence distance state
  const [deviceGps, setDeviceGps] = useState({ lat: 18.52044, lng: 73.85675, accuracy: 12, isLive: false });
  const [distanceM, setDistanceM] = useState(12);
  const [isLocating, setIsLocating] = useState(false);
  const [geoNotice, setGeoNotice] = useState(null);

  const roomLat = 18.52043;
  const roomLng = 73.85674;

  // Requests device geolocation (with fallback to campus room beacon)
  const requestDeviceLocation = (forceSimulated = null) => {
    setIsLocating(true);
    setGeoNotice(null);

    if (forceSimulated !== null) {
      const simulatedLat = forceSimulated > 50 ? 18.52120 : 18.52044;
      const simulatedLng = forceSimulated > 50 ? 73.85760 : 73.85675;
      const dist = calculateHaversineDistance(simulatedLat, simulatedLng, roomLat, roomLng);
      setDeviceGps({ lat: simulatedLat, lng: simulatedLng, accuracy: 12, isLive: false });
      setDistanceM(dist);
      setIsLocating(false);
      setGeoNotice(dist > 50 ? `Location set to outside geofence (${dist}m away)` : `Location set inside classroom (${dist}m away)`);
      return;
    }

    if (typeof navigator !== 'undefined' && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const lat = pos.coords.latitude;
          const lng = pos.coords.longitude;
          const acc = Math.round(pos.coords.accuracy);
          const dist = calculateHaversineDistance(lat, lng, roomLat, roomLng);
          setDeviceGps({ lat, lng, accuracy: acc, isLive: true });
          setDistanceM(dist);
          setIsLocating(false);
          setGeoNotice(`Live GPS acquired (Accuracy: ±${acc}m). Distance: ${dist}m.`);
        },
        (err) => {
          console.warn('Live geolocation unavailable:', err);
          const dist = calculateHaversineDistance(18.52044, 73.85675, roomLat, roomLng);
          setDeviceGps({ lat: 18.52044, lng: 73.85675, accuracy: 12, isLive: false });
          setDistanceM(dist);
          setIsLocating(false);
          setGeoNotice(`Campus calibrated GPS node applied (${dist}m from classroom center).`);
        },
        { enableHighAccuracy: true, timeout: 6000 }
      );
    } else {
      const dist = calculateHaversineDistance(18.52044, 73.85675, roomLat, roomLng);
      setDeviceGps({ lat: 18.52044, lng: 73.85675, accuracy: 12, isLive: false });
      setDistanceM(dist);
      setIsLocating(false);
      setGeoNotice(`Calibrated classroom node applied (${dist}m).`);
    }
  };

  // Handle Confirm Attendance (ATT-07) with Present / Matched / Geo-out-of-range statuses
  // Student attendance is automated via faculty classroom photo recognition (ATT-03).
  // StudentView remains read-only for session verification.

  // Submit Leave Request (LVE-01)
  const handleApplyLeave = (e) => {
    e.preventDefault();
    if (!leaveForm.fromDate || !leaveForm.toDate || !leaveForm.reason) return;

    const start = new Date(leaveForm.fromDate);
    const end = new Date(leaveForm.toDate);
    if (end < start) {
      alert('End date cannot be prior to start date.');
      return;
    }

    const diffDays = Math.ceil((end - start) / (1000 * 60 * 60 * 24)) + 1;

    const newReq = {
      id: Date.now(),
      studentId: currentUser.id,
      studentName: currentUser.name,
      rollNo: currentUser.rollNo,
      fromDate: leaveForm.fromDate,
      toDate: leaveForm.toDate,
      daysCount: diffDays,
      reason: leaveForm.reason,
      attachmentName: leaveForm.attachmentName,
      mentorStatus: 'SUBMITTED',
      mentorComment: null,
      hodStatus: 'PENDING',
      finalStatus: 'SUBMITTED',
      submittedAt: new Date().toISOString()
    };

    setLeaveRequests([newReq, ...leaveRequests]);
    onAddAuditLog({
      action: 'APPLY_LEAVE',
      entityType: 'LEAVE_REQUEST',
      entityId: String(newReq.id),
      oldValue: '{}',
      newValue: JSON.stringify({ days: diffDays, reason: leaveForm.reason }),
      reason: `Student ${currentUser.name} submitted official leave application for ${diffDays} days (LVE-01)`
    });

    setLeaveForm({ fromDate: '', toDate: '', reason: '', attachmentName: 'medical_certificate.pdf' });
    alert('Leave application submitted to your mentor Dr. Sunita Mehta for review.');
  };

  // Dispute attendance
  const handleSubmitDispute = (e) => {
    e.preventDefault();
    if (!disputeComment || disputeComment.trim().length < 10) {
      alert('Dispute comment must be at least 10 characters.');
      return;
    }

    onAddAuditLog({
      action: 'DISPUTE_ATTENDANCE',
      entityType: 'ATTENDANCE_SESSION',
      entityId: String(disputeModal.sessionId),
      oldValue: '{}',
      newValue: JSON.stringify({ comment: disputeComment }),
      reason: `Student ${currentUser.name} disputed attendance record: ${disputeComment} (ATT-14)`
    });

    setDisputeModal({ open: false, sessionId: null });
    setDisputeComment('');
    alert('Dispute logged and forwarded to course faculty Prof. Vikram Verma.');
  };

  const studentMarks = marks.filter(m => m.rollNo === currentUser.rollNo);
  const myLeaves = leaveRequests.filter(l => l.studentId === currentUser.id);
  const myDocuments = issuedDocuments.filter(d => d.rollNo === currentUser.rollNo);
  const targetedNotices = notices.filter(n => {
    const matchDept = !n.targetDepartment || n.targetDepartment === 'All Departments' || n.targetDepartment === currentUser.department;
    const matchYear = !n.targetYear || n.targetYear === 'All Years' || n.targetYear === currentUser.year;
    const matchDiv = !n.targetDivision || n.targetDivision === 'All Divisions' || n.targetDivision === currentUser.division;
    return matchDept && matchYear && matchDiv;
  });

  return (
    <div className="student-container animate-fade-in">
      <div className="section-header">
        <div>
          <h1 className="page-title">Student Portal &amp; Self-Service</h1>
          <p className="page-desc">
            Monitor attendance metrics, confirm classroom presence, verify grades, track leaves, and consult the document-grounded chatbot.
          </p>
        </div>
        <div className="header-user-actions" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div className="header-meta-badge">
            <span className="dot online" /> Welcome, {currentUser?.name} ({currentUser?.rollNo}, {currentUser?.division})
          </div>
          {onLogout && (
            <button 
              onClick={onLogout}
              className="student-logout-btn"
              title="Logout from Student Portal"
            >
              <LogOut size={15} />
              <span>Logout</span>
            </button>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="sub-nav-tabs">
        <button 
          onClick={() => setActiveTab('dashboard')} 
          className={`tab-btn ${activeTab === 'dashboard' ? 'active' : ''}`}
        >
          <CheckCircle2 size={16} /> Overview &amp; Attendance ({currentUser.attendanceRate}%)
        </button>
        <button 
          onClick={() => setActiveTab('marks')} 
          className={`tab-btn ${activeTab === 'marks' ? 'active' : ''}`}
        >
          <FileText size={16} /> Internal Marks &amp; Transcripts
        </button>
        <button 
          onClick={() => setActiveTab('leave')} 
          className={`tab-btn ${activeTab === 'leave' ? 'active' : ''}`}
        >
          <Calendar size={16} /> Leave Applications ({myLeaves.length})
        </button>
        <button 
          onClick={() => setActiveTab('documents')} 
          className={`tab-btn ${activeTab === 'documents' ? 'active' : ''}`}
        >
          <ShieldCheck size={16} /> Issued Documents ({myDocuments.length})
        </button>
        <button 
          onClick={() => setActiveTab('notices')} 
          className={`tab-btn ${activeTab === 'notices' ? 'active' : ''}`}
        >
          <Bell size={16} /> Notices &amp; Circulars ({targetedNotices.length})
        </button>
      </div>

      {/* ACTIVE ATTENDANCE SESSION FLOATING NOTIFICATION BANNER */}
      {activeSession && activeSession.status === 'OPEN' && (
        <div className="active-attendance-alert-banner glass-card animate-pulse-glow" style={{ marginBottom: '24px' }}>
          <div className="alert-banner-left">
            <span className="live-pulsing-badge">● LIVE CLASS IN PROGRESS</span>
            <h3 style={{ marginTop: '4px' }}>{activeSession.subjectName} — {activeSession.roomName}</h3>
            
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap', marginTop: '6px' }}>
              <span className="text-secondary text-sm">
                <MapPin size={14} className="inline mr-1" />
                Distance: <strong className={distanceM <= 50 ? 'text-success font-mono' : 'text-danger font-mono'}>{distanceM}m</strong> (Max: 50m)
              </span>

              <span className={`badge-pill ${distanceM <= 50 ? 'success' : 'danger'}`}>
                {distanceM <= 50 ? '✓ Inside Geofence' : '⚠ Outside Geofence'}
              </span>

              <span className="text-secondary text-xs">
                Code: <strong className="font-mono text-primary">{activeSession.challengeCode}</strong>
              </span>
            </div>

            {geoNotice && (
              <div style={{ fontSize: '0.8rem', color: '#38bdf8', marginTop: '6px' }}>
                ℹ {geoNotice}
              </div>
            )}

            <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
              <button 
                type="button" 
                onClick={() => requestDeviceLocation()} 
                className="btn-secondary btn-xs flex items-center gap-1"
                disabled={isLocating}
              >
                <RefreshCw size={11} className={isLocating ? 'animate-spin' : ''} /> {isLocating ? 'Acquiring GPS...' : 'Acquire Live GPS'}
              </button>
              <button 
                type="button" 
                onClick={() => requestDeviceLocation(12)} 
                className="btn-secondary btn-xs"
              >
                Test 12m (Inside)
              </button>
              <button 
                type="button" 
                onClick={() => requestDeviceLocation(85)} 
                className="btn-secondary btn-xs"
              >
                Test 85m (Outside)
              </button>
            </div>
          </div>

          <div className="alert-banner-actions" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '8px' }}>
            {studentSessionRecord?.finalStatus === 'PRESENT' && (
              <div className="badge-pill success text-sm">
                <CheckCircle2 size={16} /> Present (Facial Biometric Verified)
              </div>
            )}

            {studentSessionRecord?.finalStatus === 'REVIEW' && (
              <div className="badge-pill warning text-sm">
                <AlertTriangle size={16} /> Under Faculty Review (Borderline Face Match)
              </div>
            )}

            {studentSessionRecord?.finalStatus === 'ABSENT' && (
              <div className="badge-pill danger text-sm">
                <AlertTriangle size={16} /> Absent (Unmatched / Finalised)
              </div>
            )}

            {(!studentSessionRecord || studentSessionRecord?.finalStatus === 'PENDING') && (
              <div className="badge-pill muted text-sm">
                <Clock size={16} /> Attendance In Progress (Classroom Photo Processing)
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 1: DASHBOARD & ATTENDANCE */}
      {activeTab === 'dashboard' && (
        <div className="tab-pane animate-fade-in">
          <div className="grid-summary-kpis">
            <div className={`kpi-card ${currentUser.attendanceRate < 75 ? 'warning' : 'success'}`}>
              <span className="kpi-label">Overall Attendance Percentage</span>
              <span className="kpi-val">{currentUser.attendanceRate}%</span>
              <span className="kpi-sub">
                {currentUser.attendanceRate < 75 ? '⚠️ Defaulter risk - Below 75% minimum' : '✓ Compliant with university norms'}
              </span>
            </div>

            <div className="kpi-card">
              <span className="kpi-label">Assigned Faculty Mentor</span>
              <span className="kpi-val text-sm">{currentUser.mentorName}</span>
              <span className="kpi-sub">Advisory &amp; Leave Counselor</span>
            </div>

            <div className="kpi-card">
              <span className="kpi-label">Tuition Fee Clearance</span>
              <span className="kpi-val text-sm">
                <span className={`badge-pill ${currentUser.feeStatus === 'PAID' ? 'success' : 'warning'}`}>
                  {currentUser.feeStatus}
                </span>
              </span>
              <span className="kpi-sub">
                {currentUser.feeStatus === 'PAID' ? 'Zero outstanding dues' : `Due: $ ${currentUser.dueAmount}`}
              </span>
            </div>
          </div>

          <div className="grid-two-col mt-6">
            {/* Subject Attendance Breakdown */}
            <div className="glass-card">
              <div className="card-header-flex">
                <h3>Subject-Wise Attendance</h3>
                <span className="badge-pill info">Semester 5</span>
              </div>
              
              <div className="subject-progress-list">
                <div className="subject-progress-item">
                  <div className="subject-header">
                    <span>CS-302 Machine Learning &amp; AI</span>
                    <strong className="text-success font-mono">87.5% (28/32)</strong>
                  </div>
                  <div className="progress-bar-bg"><div className="progress-bar-fill" style={{ width: '87.5%' }} /></div>
                </div>

                <div className="subject-progress-item">
                  <div className="subject-header">
                    <span>CS-301 Database Systems</span>
                    <strong className="text-success font-mono">83.3% (25/30)</strong>
                  </div>
                  <div className="progress-bar-bg"><div className="progress-bar-fill" style={{ width: '83.3%' }} /></div>
                </div>

                <div className="subject-progress-item">
                  <div className="subject-header">
                    <span>CS-303 Distributed Cloud</span>
                    <strong className="text-warning font-mono">72.0% (18/25) ⚠️</strong>
                  </div>
                  <div className="progress-bar-bg"><div className="progress-bar-fill bg-warning" style={{ width: '72%' }} /></div>
                </div>
              </div>
            </div>

            {/* Quick AI Assistant Card */}
            <div className="glass-card ai-assistant-hero">
              <div className="ai-hero-header">
                <div className="sparkle-circle"><Sparkles size={20} className="text-primary" /></div>
                <div>
                  <h3>IntelliCampus Knowledge Chatbot</h3>
                  <p className="card-desc">Ground-truth answers directly from college circulars and syllabi (`BOT-03`).</p>
                </div>
              </div>
              <div className="sample-questions-box">
                <p className="text-xs text-muted mb-2">Try asking the document AI:</p>
                <button onClick={onOpenChat} className="sample-q-pill">
                  "What is the deadline for backlog exam form submission?"
                </button>
                <button onClick={onOpenChat} className="sample-q-pill">
                  "What is the minimum attendance requirement before detention?"
                </button>
              </div>
              <button onClick={onOpenChat} className="btn-primary w-full mt-4">
                <MessageSquare size={16} /> Open Document Assistant
              </button>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: MARKS */}
      {activeTab === 'marks' && (
        <div className="tab-pane animate-fade-in">
          <div className="table-card">
            <table className="custom-table">
              <thead>
                <tr>
                  <th>Course Code</th>
                  <th>Examination</th>
                  <th>Marks Scored</th>
                  <th>Maximum</th>
                  <th>Percentage</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {studentMarks.map(m => (
                  <tr key={m.id}>
                    <td><strong>{m.subjectCode}</strong></td>
                    <td>{m.exam}</td>
                    <td className="font-mono font-bold text-primary">{m.marks}</td>
                    <td className="font-mono">{m.maxMarks}</td>
                    <td className="font-mono">{((m.marks / m.maxMarks) * 100).toFixed(1)}%</td>
                    <td>
                      <span className="badge-pill success">
                        🔒 Verified &amp; Published
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 3: LEAVE APPLICATION */}
      {activeTab === 'leave' && (
        <div className="tab-pane animate-fade-in">
          <div className="grid-two-col">
            <div className="glass-card">
              <h3>Apply for Official Leave</h3>
              <p className="card-desc">
                Leave follows the chain: Student &rarr; Mentor &rarr; HOD. Approved leave is automatically excluded from absence calculation (`LVE-04`).
              </p>

              <form onSubmit={handleApplyLeave} className="form-stack">
                <div className="grid-two-col-compact">
                  <div className="form-group">
                    <label>From Date</label>
                    <input 
                      type="date" 
                      value={leaveForm.fromDate}
                      onChange={e => setLeaveForm({...leaveForm, fromDate: e.target.value})}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>To Date</label>
                    <input 
                      type="date" 
                      value={leaveForm.toDate}
                      onChange={e => setLeaveForm({...leaveForm, toDate: e.target.value})}
                      required
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label>Reason for Leave</label>
                  <textarea 
                    rows={4}
                    placeholder="Specify detailed reason (e.g. Medical emergency, University Hackathon delegation)..."
                    value={leaveForm.reason}
                    onChange={e => setLeaveForm({...leaveForm, reason: e.target.value})}
                    required
                  />
                </div>

                <div className="form-group">
                  <label>Document Evidence Attachment (Doctor Certificate / Official Letter)</label>
                  <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
                    <input 
                      type="file" 
                      id="leave-attachment-upload"
                      accept=".pdf,.png,.jpg,.jpeg"
                      style={{ display: 'none' }}
                      onChange={e => {
                        if (e.target.files && e.target.files[0]) {
                          setLeaveForm({ ...leaveForm, attachmentName: e.target.files[0].name });
                        }
                      }}
                    />
                    <label 
                      htmlFor="leave-attachment-upload" 
                      className="btn-secondary btn-sm"
                      style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '6px', margin: 0 }}
                    >
                      <FileText size={14} /> Select Proof File...
                    </label>
                    <div className="attachment-chip font-mono text-xs">
                      <FileText size={14} /> <span>{leaveForm.attachmentName}</span>
                    </div>
                  </div>
                </div>

                <button type="submit" className="btn-primary w-full">
                  <Send size={15} /> Submit Application
                </button>
              </form>
            </div>

            <div className="glass-card">
              <h3>My Leave Applications &amp; Tracking</h3>
              <div className="leave-status-list">
                {myLeaves.map(leave => (
                  <div key={leave.id} className="leave-item-card">
                    <div className="leave-item-header">
                      <div>
                        <strong>{leave.fromDate} &rarr; {leave.toDate}</strong>
                        <span className="days-badge">({leave.daysCount} Days)</span>
                      </div>
                      <span className={`badge-pill ${
                        leave.finalStatus === 'APPROVED' ? 'success' :
                        leave.finalStatus === 'REJECTED' ? 'danger' : 'warning'
                      }`}>
                        {leave.finalStatus}
                      </span>
                    </div>

                    <p className="leave-reason-text">"{leave.reason}"</p>

                    <div className="workflow-stepper">
                      <div className={`step-dot ${leave.mentorStatus !== 'PENDING' ? 'done' : 'active'}`}>
                        1. Mentor Review
                      </div>
                      <ChevronRight size={14} className="text-muted" />
                      <div className={`step-dot ${leave.hodStatus === 'APPROVED' ? 'done' : leave.hodStatus === 'REJECTED' ? 'rejected' : 'active'}`}>
                        2. HOD Final Decision
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 4: ISSUED VERIFIABLE DOCUMENTS */}
      {activeTab === 'documents' && (
        <div className="tab-pane animate-fade-in">
          <div className="glass-card">
            <h3>My Tamper-Proof Official Credentials</h3>
            <p className="card-desc">
              All credentials contain a unique verification code and cryptographic hash verifiable by anyone on the public portal (`DOC-03`).
            </p>

            <div className="doc-cards-grid mt-4">
              {myDocuments.map(doc => (
                <div key={doc.code} className="credential-certificate-card">
                  <div className="cert-top">
                    <div className="cert-seal">🎓</div>
                    <div>
                      <h4 className="cert-title">{doc.type}</h4>
                      <span className="cert-code font-mono">{doc.code}</span>
                    </div>
                  </div>

                  <div className="cert-body">
                    <p>Issued to: <strong>{doc.studentName}</strong> ({doc.rollNo})</p>
                    <p>Department: {doc.department}</p>
                    <p>Status: <span className="text-success font-bold">● {doc.status}</span></p>
                    <p className="text-xs text-muted font-mono truncate">Hash: {doc.fileHash}</p>
                  </div>

                  <div className="cert-footer">
                    <span className="text-xs text-muted">Issued: {doc.issueDate}</span>
                    <a 
                      href={`#`} 
                      onClick={(e) => {
                        e.preventDefault();
                        alert(`Public verification link: /verify/${doc.code}`);
                      }}
                      className="btn-secondary btn-xs"
                    >
                      <ExternalLink size={12} /> Public Verification Link
                    </a>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* TAB 5: NOTICES & CIRCULARS */}
      {activeTab === 'notices' && (
        <div className="tab-pane animate-fade-in">
          <div className="glass-card">
            <div className="card-header-flex">
              <div>
                <h3><Bell size={18} /> Official Notices &amp; Broadcast Circulars</h3>
                <p className="card-desc">
                  Curated circulars targeted to {currentUser.department} • {currentUser.year || 'All Years'} • {currentUser.division || 'All Divisions'}.
                </p>
              </div>
              <span className="badge-pill info">{targetedNotices.length} Active Circulars</span>
            </div>

            {targetedNotices.length === 0 ? (
              <div className="empty-state" style={{ padding: '32px', textAlign: 'center' }}>
                <Bell size={36} className="text-secondary" style={{ margin: '0 auto 12px', display: 'block', opacity: 0.5 }} />
                <p className="text-secondary">No current circulars matching your department and academic division.</p>
              </div>
            ) : (
              <div className="notices-stream" style={{ display: 'flex', flexDirection: 'column', gap: '16px', marginTop: '16px' }}>
                {targetedNotices.map(notice => (
                  <div key={notice.id} className="notice-item-card glass-card" style={{ padding: '16px', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '12px' }}>
                    <div className="notice-item-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                      <h4 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 600 }}>{notice.title}</h4>
                      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                        <span className="badge-pill info">{notice.targetDepartment}</span>
                        {notice.targetYear && notice.targetYear !== 'All Years' && (
                          <span className="badge-pill accent">{notice.targetYear}</span>
                        )}
                        {notice.targetDivision && notice.targetDivision !== 'All Divisions' && (
                          <span className="badge-pill warning">{notice.targetDivision}</span>
                        )}
                      </div>
                    </div>
                    <p style={{ color: 'var(--text-secondary, #cbd5e1)', lineHeight: 1.5, margin: '8px 0' }}>
                      {notice.body}
                    </p>
                    <div className="notice-item-footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '12px', fontSize: '0.8rem', color: 'rgba(255,255,255,0.5)' }}>
                      <div>
                        <span>📢 Issued by <strong>{notice.publisherName || notice.publisherRole || 'Administration'}</strong></span>
                        <span style={{ margin: '0 8px' }}>•</span>
                        <span>{new Date(notice.publishedAt).toLocaleDateString()}</span>
                      </div>
                      {notice.deadline && (
                        <span className="badge-pill danger">
                          Deadline: {notice.deadline}
                        </span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
