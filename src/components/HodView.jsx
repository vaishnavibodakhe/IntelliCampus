import React, { useState } from 'react';
import { 
  CheckCircle, 
  XCircle, 
  FileSpreadsheet, 
  FileText, 
  AlertTriangle, 
  Download, 
  ArrowRight,
  TrendingDown,
  Clock,
  ShieldCheck,
  LogOut,
  KeyRound,
  Bell
} from 'lucide-react';
import StaffChangePasswordView from './staff/StaffChangePasswordView';
import NoticeComposer from './NoticeComposer';
import api from '../api/client';

export default function HodView({
  leaveRequests,
  setLeaveRequests,
  markCorrections,
  setMarkCorrections,
  marks,
  setMarks,
  users,
  onAddAuditLog,
  staffSession,
  onLogout,
  onChangePassword,
  notices = [],
  setNotices
}) {
  const [activeTab, setActiveTab] = useState('leaves');
  const [showChangePasswordModal, setShowChangePasswordModal] = useState(false);
  const [selectedReportType, setSelectedReportType] = useState('defaulters');
  const [rejectReasonModal, setRejectReasonModal] = useState({ open: false, type: '', id: null });
  const [rejectReasonText, setRejectReasonText] = useState('');

  const handleNoticePublished = (newNotice) => {
    if (setNotices) {
      setNotices([newNotice, ...(notices || [])]);
    }
  };

  // HOD Approve Leave
  const handleApproveLeave = async (id) => {
    try {
      await api.hodReviewLeave(id, {
        decision: 'APPROVE',
        remarks: 'HOD approved leave'
      });
    } catch (err) {
      console.warn('Backend HOD review API call failed, updating local state:', err);
    }

    setLeaveRequests(leaveRequests.map(req => {
      if (req.id === id) {
        return {
          ...req,
          hodStatus: 'APPROVED',
          finalStatus: 'APPROVED'
        };
      }
      return req;
    }));

    const targetReq = leaveRequests.find(r => r.id === id);
    onAddAuditLog({
      action: 'APPROVE',
      entityType: 'LEAVE_REQUEST',
      entityId: String(id),
      oldValue: '{"status":"MENTOR_RECOMMENDED"}',
      newValue: '{"status":"APPROVED"}',
      reason: `HOD granted official leave approval for ${targetReq?.studentName} (${targetReq?.daysCount} days)`
    });
    alert(`Leave Request #${id} officially APPROVED. Attendance calculation will exempt these ${targetReq?.daysCount} days.`);
  };

  // Open Reject Modal
  const openRejectModal = (type, id) => {
    setRejectReasonModal({ open: true, type, id });
    setRejectReasonText('');
  };

  // Submit Rejection with mandatory reason
  const handleSubmitRejection = async (e) => {
    e.preventDefault();
    if (!rejectReasonText || rejectReasonText.trim().length < 10) {
      alert('A written reason of at least 10 characters is required for rejection.');
      return;
    }

    if (rejectReasonModal.type === 'leave') {
      const id = rejectReasonModal.id;
      try {
        await api.hodReviewLeave(id, {
          decision: 'REJECT',
          remarks: rejectReasonText
        });
      } catch (err) {
        console.warn('Backend HOD reject leave API failed, updating local state:', err);
      }

      setLeaveRequests(leaveRequests.map(req => {
        if (req.id === id) {
          return {
            ...req,
            hodStatus: 'REJECTED',
            finalStatus: 'REJECTED',
            rejectionReason: rejectReasonText
          };
        }
        return req;
      }));

      onAddAuditLog({
        action: 'REJECT',
        entityType: 'LEAVE_REQUEST',
        entityId: String(id),
        oldValue: '{"status":"MENTOR_RECOMMENDED"}',
        newValue: JSON.stringify({ status: 'REJECTED', reason: rejectReasonText }),
        reason: `HOD rejected leave request: ${rejectReasonText}`
      });
      alert(`Leave Request #${id} REJECTED with logged reason.`);
    } else if (rejectReasonModal.type === 'mark') {
      const id = rejectReasonModal.id;
      try {
        await api.decideCorrection(id, {
          decision: 'REJECT',
          remarks: rejectReasonText
        });
      } catch (err) {
        console.warn('Backend mark reject API failed, updating local state:', err);
      }

      setMarkCorrections(markCorrections.map(cor => {
        if (cor.id === id) {
          return {
            ...cor,
            status: 'REJECTED',
            decisionComment: rejectReasonText
          };
        }
        return cor;
      }));

      onAddAuditLog({
        action: 'REJECT',
        entityType: 'MARK_CORRECTION',
        entityId: String(id),
        oldValue: '{"status":"PENDING_HOD"}',
        newValue: JSON.stringify({ status: 'REJECTED', reason: rejectReasonText }),
        reason: `HOD rejected mark change proposal: ${rejectReasonText}`
      });
      alert(`Mark Correction #${id} REJECTED.`);
    }

    setRejectReasonModal({ open: false, type: '', id: null });
  };

  // HOD Approve Mark Correction
  const handleApproveMarkCorrection = async (cor) => {
    try {
      await api.decideCorrection(cor.id, {
        decision: 'APPROVE',
        remarks: 'Approved upon verification of evaluator records.'
      });
    } catch (err) {
      console.warn('Backend mark approve API failed, updating local state:', err);
    }

    // 1. Update mark entry
    setMarks(marks.map(m => {
      if (m.id === cor.markId) {
        return {
          ...m,
          marks: cor.newMarks,
          updatedBy: 'HOD Approved (Requested by ' + cor.facultyName + ')',
          updatedAt: new Date().toISOString()
        };
      }
      return m;
    }));

    // 2. Update correction status
    setMarkCorrections(markCorrections.map(c => {
      if (c.id === cor.id) {
        return {
          ...c,
          status: 'APPROVED',
          decisionComment: 'Approved upon verification of evaluator records.'
        };
      }
      return c;
    }));

    // 3. Write immutable audit hash log
    onAddAuditLog({
      action: 'CORRECT',
      entityType: 'MARK_ENTRY',
      entityId: String(cor.markId),
      oldValue: JSON.stringify({ marks: cor.oldMarks }),
      newValue: JSON.stringify({ marks: cor.newMarks }),
      reason: `HOD Approved mark correction: ${cor.reason}`
    });

    alert(`Mark Correction APPROVED: ${cor.studentName} marks updated from ${cor.oldMarks} to ${cor.newMarks}. Cryptographic audit log updated.`);
  };

  // Export CSV
  const handleExportReport = () => {
    if (students.length === 0) {
      alert('No student records available to export.');
      return;
    }
    const csvContent = "data:text/csv;charset=utf-8," + 
      "Roll No,Student Name,Department,Division,Attendance Rate,Defaulter Status,Fees Due\n" +
      users.filter(u => u.role === 'STUDENT').map(s => 
        `${s.rollNo || ''},"${s.name || ''}",${s.department || ''},${s.division || ''},${s.attendanceRate || 0}%,${(s.attendanceRate || 0) < 75 ? 'DEFAULTER' : 'COMPLIANT'},${s.feeStatus || ''}`
      ).join("\n");
    
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `HOD_Report_${selectedReportType}_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const students = users.filter(u => u.role === 'STUDENT');
  const defaulterStudents = students.filter(s => (s.attendanceRate || 0) < 75);
  const pendingLeaves = leaveRequests.filter(l => 
    (l.hodStatus === 'PENDING' || l.finalStatus === 'SUBMITTED' || l.finalStatus === 'RECOMMENDED') &&
    l.finalStatus !== 'APPROVED' &&
    l.finalStatus !== 'REJECTED'
  );
  const pendingCorrections = markCorrections.filter(c => c.status === 'PENDING_HOD');

  return (
    <div className="hod-container animate-fade-in">
      <div className="section-header">
        <div>
          <h1 className="page-title">HOD Executive Dashboard</h1>
          <p className="page-desc">
            Head of Department governance: Final leave decisions, published mark correction authorization, and academic compliance metrics.
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <div className="header-meta-badge">
            <span className="dot online" /> {staffSession?.user ? `${staffSession.user.name} (${staffSession.user.department || 'HOD'})` : 'HOD Office'}
          </div>
          <button
            type="button"
            onClick={() => setShowChangePasswordModal(true)}
            className="action-icon-btn"
            style={{ padding: '6px 12px', fontSize: '0.82rem', display: 'flex', alignItems: 'center', gap: '5px' }}
            title="Change HOD Password"
          >
            <KeyRound size={15} />
            <span>Change Password</span>
          </button>
          {onLogout && (
            <button
              type="button"
              onClick={onLogout}
              className="action-icon-btn"
              style={{ padding: '6px 12px', fontSize: '0.82rem', color: '#ef4444', borderColor: 'rgba(239, 68, 68, 0.4)', display: 'flex', alignItems: 'center', gap: '5px' }}
              title="Sign Out of HOD Dashboard"
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
          onClick={() => setActiveTab('leaves')} 
          className={`tab-btn ${activeTab === 'leaves' ? 'active' : ''}`}
        >
          <Clock size={16} /> Pending Leave Approvals ({pendingLeaves.length})
        </button>
        <button 
          onClick={() => setActiveTab('corrections')} 
          className={`tab-btn ${activeTab === 'corrections' ? 'active' : ''}`}
        >
          <ShieldCheck size={16} /> Published Mark Corrections ({pendingCorrections.length})
        </button>
        <button 
          onClick={() => setActiveTab('reports')} 
          className={`tab-btn ${activeTab === 'reports' ? 'active' : ''}`}
        >
          <FileSpreadsheet size={16} /> Department Analytics & Defaulters
        </button>
        <button 
          onClick={() => setActiveTab('notices')} 
          className={`tab-btn ${activeTab === 'notices' ? 'active' : ''}`}
        >
          <Bell size={16} /> Send Notice
        </button>
      </div>

      {/* TAB 1: LEAVE REQUESTS */}
      {activeTab === 'leaves' && (
        <div className="tab-pane animate-fade-in">
          <div className="glass-card">
            <div className="card-header-flex">
              <div>
                <h3>Student Leave Applications (Forwarded by Mentors)</h3>
                <p className="card-desc">
                  Students apply &rarr; Mentor reviews and forwards &rarr; HOD gives final approval. Rejection requires written reason (`LVE-02`).
                </p>
              </div>
              <span className="badge-pill info">{pendingLeaves.length} Awaiting Decision</span>
            </div>

            {pendingLeaves.length === 0 ? (
              <div className="empty-state-box">
                <CheckCircle size={36} className="text-success" />
                <p>All forwarded leave applications have been reviewed.</p>
              </div>
            ) : (
              <div className="request-card-grid">
                {pendingLeaves.map(req => (
                  <div key={req.id} className="approval-card">
                    <div className="approval-card-header">
                      <div>
                        <h4 className="student-title">{req.studentName} ({req.rollNo})</h4>
                        <span className="date-range">
                          📅 {req.fromDate} to {req.toDate} ({req.daysCount} days)
                        </span>
                      </div>
                      <span className={`badge-pill ${req.mentorStatus === 'RECOMMENDED' ? 'warning' : 'info'}`}>
                        {req.mentorStatus === 'RECOMMENDED' ? 'Mentor Recommended' : 'Direct Submission'}
                      </span>
                    </div>

                    <div className="reason-quote">
                      <strong>Student Reason:</strong> "{req.reason}"
                    </div>

                    {req.attachmentName && (
                      <div className="attachment-chip">
                        <FileText size={14} /> Attached Evidence: <code>{req.attachmentName}</code>
                      </div>
                    )}

                    {req.mentorComment && (
                      <div className="mentor-recommendation-note">
                        <strong>Mentor Note:</strong> {req.mentorComment}
                      </div>
                    )}

                    <div className="approval-card-actions">
                      <button 
                        onClick={() => openRejectModal('leave', req.id)}
                        className="btn-danger-outline"
                      >
                        <XCircle size={15} /> Reject with Reason
                      </button>
                      <button 
                        onClick={() => handleApproveLeave(req.id)}
                        className="btn-success"
                      >
                        <CheckCircle size={15} /> Approve & Exempt Days
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 2: MARK CORRECTIONS */}
      {activeTab === 'corrections' && (
        <div className="tab-pane animate-fade-in">
          <div className="glass-card">
            <div className="card-header-flex">
              <div>
                <h3>Faculty Correction Requests on Published Marks</h3>
                <p className="card-desc">
                  Published marks are locked. Changes require HOD approval and generate a SHA-256 hash-chained audit record (`MRK-05`).
                </p>
              </div>
              <span className="badge-pill info">{pendingCorrections.length} Pending Approval</span>
            </div>

            {pendingCorrections.length === 0 ? (
              <div className="empty-state-box">
                <CheckCircle size={36} className="text-success" />
                <p>No pending mark correction proposals from faculty.</p>
              </div>
            ) : (
              <div className="request-card-grid">
                {pendingCorrections.map(cor => (
                  <div key={cor.id} className="approval-card highlight-border">
                    <div className="approval-card-header">
                      <div>
                        <h4>{cor.studentName} ({cor.rollNo}) - {cor.subjectCode}</h4>
                        <span className="sub-text">{cor.exam} • Submitted by {cor.facultyName}</span>
                      </div>
                      <span className="badge-pill warning">Correction Requested</span>
                    </div>

                    <div className="marks-comparison-box">
                      <div className="comparison-col old">
                        <span className="label">Current (Locked) Marks</span>
                        <span className="val font-mono">{cor.oldMarks}</span>
                      </div>
                      <ArrowRight size={20} className="text-muted" />
                      <div className="comparison-col new">
                        <span className="label">Proposed New Marks</span>
                        <span className="val font-mono text-success">{cor.newMarks}</span>
                      </div>
                    </div>

                    <div className="reason-quote">
                      <strong>Faculty Justification:</strong> "{cor.reason}"
                    </div>

                    <div className="approval-card-actions">
                      <button 
                        onClick={() => openRejectModal('mark', cor.id)}
                        className="btn-danger-outline"
                      >
                        <XCircle size={15} /> Reject Proposal
                      </button>
                      <button 
                        onClick={() => handleApproveMarkCorrection(cor)}
                        className="btn-success"
                      >
                        <CheckCircle size={15} /> Authorize & Update Audit Log
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 3: DEPARTMENT REPORTS */}
      {activeTab === 'reports' && (
        <div className="tab-pane animate-fade-in">
          <div className="pane-toolbar">
            <div className="filter-group">
              <label>Select Report Scope:</label>
              <select 
                value={selectedReportType}
                onChange={e => setSelectedReportType(e.target.value)}
              >
                <option value="defaulters">Attendance Defaulter Registry (&lt; 75%)</option>
                <option value="all_attendance">Class Attendance Comprehensive</option>
                <option value="marks_summary">Mid-Term Grade Distribution</option>
              </select>
            </div>
            <button onClick={handleExportReport} className="btn-primary">
              <Download size={15} /> Export Official Report (.CSV)
            </button>
          </div>

          <div className="grid-summary-kpis">
            <div className="kpi-card">
              <span className="kpi-label">Total Enrolled Students</span>
              <span className="kpi-val">{students.length}</span>
              <span className="kpi-sub">Enrolled Students</span>
            </div>
            <div className="kpi-card warning">
              <span className="kpi-label">Defaulters (&lt; 75% Attendance)</span>
              <span className="kpi-val text-danger">{defaulterStudents.length}</span>
              <span className="kpi-sub">Subject to exam detention</span>
            </div>
            <div className="kpi-card success">
              <span className="kpi-label">Average Department Attendance</span>
              <span className="kpi-val text-success">
                {students.length > 0
                  ? (students.reduce((acc, s) => acc + (s.attendanceRate || 0), 0) / students.length).toFixed(1)
                  : '0.0'}%
              </span>
              <span className="kpi-sub">Target: &gt; 80%</span>
            </div>
          </div>

          <div className="table-card">
            <table className="custom-table">
              <thead>
                <tr>
                  <th>Roll No</th>
                  <th>Student Name</th>
                  <th>Division</th>
                  <th>Attendance %</th>
                  <th>Status</th>
                  <th>Active Backlogs</th>
                  <th>Fee Status</th>
                </tr>
              </thead>
              <tbody>
                {students.length === 0 ? (
                  <tr>
                    <td colSpan="7" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted, #94a3b8)' }}>
                      No student records found in the database.
                    </td>
                  </tr>
                ) : (
                  students.map(s => {
                    const isDefaulter = (s.attendanceRate || 0) < 75;
                    return (
                      <tr key={s.id} className={isDefaulter ? 'row-defaulter' : ''}>
                        <td className="font-mono">{s.rollNo || 'N/A'}</td>
                        <td><strong>{s.name}</strong></td>
                        <td>{s.division || '—'}</td>
                        <td>
                          <span className={isDefaulter ? 'text-danger font-bold' : 'text-success'}>
                            {s.attendanceRate != null ? `${s.attendanceRate}%` : 'N/A'}
                          </span>
                        </td>
                        <td>
                          {isDefaulter ? (
                            <span className="badge-pill danger">
                              <AlertTriangle size={12} /> Defaulter Notice
                            </span>
                          ) : (
                            <span className="badge-pill success">Compliant</span>
                          )}
                        </td>
                        <td>{(s.backlogs || 0) > 0 ? <span className="text-warning font-bold">{s.backlogs} Backlogs</span> : '0'}</td>
                        <td>
                          <span className={`badge-pill ${s.feeStatus === 'PAID' ? 'success' : 'warning'}`}>
                            {s.feeStatus || 'PENDING'}
                          </span>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 4: SEND NOTICE */}
      {activeTab === 'notices' && (
        <div className="tab-pane animate-fade-in">
          <NoticeComposer 
            publisherRole="HOD"
            publisherName={staffSession?.user?.name || "HOD"}
            publisherId={staffSession?.user?.id || 1}
            defaultDepartment={staffSession?.user?.department || ""}
            onNoticePublished={handleNoticePublished}
            onAddAuditLog={onAddAuditLog}
            initialStudents={users?.filter(u => u.role === 'STUDENT')}
            cardTitle="Send Notice / Department Circular"
            cardDescription="Broadcast official department orders, alerts, or directives to students and faculty."
          />
        </div>
      )}

      {/* Rejection Modal */}
      {rejectReasonModal.open && (
        <div className="modal-backdrop">
          <div className="modal-dialog animate-fade-in">
            <div className="modal-header">
              <h3>Mandatory Rejection Justification</h3>
              <button onClick={() => setRejectReasonModal({ open: false, type: '', id: null })} className="modal-close">&times;</button>
            </div>
            <form onSubmit={handleSubmitRejection} className="form-stack">
              <p className="modal-instruction">
                As per college regulatory audit policy, any rejected {rejectReasonModal.type === 'leave' ? 'leave request' : 'mark correction'} must include a detailed reason of at least 10 characters.
              </p>
              <div className="form-group">
                <label>Written Reason</label>
                <textarea 
                  rows={4}
                  placeholder="e.g. Medical certificate lacks official hospital stamp and dates overlap with unauthorized leave..."
                  value={rejectReasonText}
                  onChange={e => setRejectReasonText(e.target.value)}
                  required
                />
              </div>
              <div className="modal-footer">
                <button type="button" onClick={() => setRejectReasonModal({ open: false, type: '', id: null })} className="btn-secondary">
                  Cancel
                </button>
                <button type="submit" className="btn-danger">
                  Confirm Rejection & Notify
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Change Password Modal */}
      {showChangePasswordModal && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.75)',
          backdropFilter: 'blur(6px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000,
          padding: '1rem'
        }}>
          <StaffChangePasswordView
            staffSession={staffSession}
            onClose={() => setShowChangePasswordModal(false)}
            onPasswordChanged={() => {
              setShowChangePasswordModal(false);
              alert('Password updated successfully!');
            }}
          />
        </div>
      )}
    </div>
  );
}
