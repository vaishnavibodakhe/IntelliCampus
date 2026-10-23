import React, { useState } from 'react';
import {
  Users,
  AlertTriangle,
  Clock,
  TrendingDown,
  CheckCircle,
  XCircle,
  MessageSquare,
  Send,
  FileText,
  ShieldAlert,
  ArrowRight,
  Bell
} from 'lucide-react';
import NoticeComposer from './NoticeComposer';
import api from '../api/client';

export default function MentorView({
  riskStudents,
  setRiskStudents,
  leaveRequests,
  setLeaveRequests,
  users,
  onAddAuditLog,
  notices = [],
  setNotices,
  staffSession
}) {
  const [activeTab, setActiveTab] = useState('risk');
  const [riskFilter, setRiskFilter] = useState('ALL');
  const [selectedStudentForAction, setSelectedStudentForAction] = useState(null);
  const [mentorActionNote, setMentorActionNote] = useState('');

  // Mentor recommend or reject leave
  const [recommendModal, setRecommendModal] = useState({ open: false, reqId: null, isRecommend: true });
  const [mentorCommentText, setMentorCommentText] = useState('');

  const currentMentorId = staffSession?.user?.id;
  const currentMentorName = staffSession?.user?.name || 'Faculty Mentor';
  // Mentees assigned to current mentor or all students if unassigned
  const mentees = users.filter(u => u.role === 'STUDENT' && (!currentMentorId || !u.mentorId || u.mentorId === currentMentorId));
  const highRiskStudents = riskStudents.filter(r => r.level === 'HIGH');
  const pendingLeavesForMentor = leaveRequests.filter(l => l.mentorStatus === 'SUBMITTED');
  const filteredRiskStudents = riskStudents.filter(r => {
    if (riskFilter === 'ALL') return true;
    return r.level === riskFilter;
  });

  const handleNoticePublished = (newNotice) => {
    if (setNotices) {
      setNotices([newNotice, ...(notices || [])]);
    }
  };

  // Handle Log Action on Risk Alert
  const handleSaveRiskAction = (e) => {
    e.preventDefault();
    if (!mentorActionNote || mentorActionNote.trim().length < 10) {
      alert('Action note must be at least 10 characters detailing intervention taken.');
      return;
    }

    const updated = riskStudents.map(rs => {
      if (rs.studentId === selectedStudentForAction.studentId) {
        return {
          ...rs,
          actionsTaken: [
            ...rs.actionsTaken,
            {
              id: Date.now(),
              date: new Date().toISOString().split('T')[0],
              mentorName: currentMentorName,
              note: mentorActionNote
            }
          ]
        };
      }
      return rs;
    });

    setRiskStudents(updated);
    onAddAuditLog({
      action: 'MENTOR_ACTION',
      entityType: 'RISK_SCORE',
      entityId: String(selectedStudentForAction.studentId),
      oldValue: '{}',
      newValue: JSON.stringify({ action: mentorActionNote }),
      reason: `Mentor intervention logged for student ${selectedStudentForAction.name} (EWS-05)`
    });

    setMentorActionNote('');
    setSelectedStudentForAction(null);
    alert('Intervention action note saved and logged for academic record.');
  };

  // Handle Mentor Leave Recommendation
  const handleLeaveDecision = async (e) => {
    e.preventDefault();
    const reqId = recommendModal.reqId;
    const isRec = recommendModal.isRecommend;

    try {
      await api.mentorReviewLeave(reqId, {
        decision: isRec ? 'RECOMMEND' : 'REJECT',
        remarks: mentorCommentText
      });
    } catch (err) {
      console.warn('Backend mentor leave review API call failed, updating local state:', err);
    }

    setLeaveRequests(leaveRequests.map(req => {
      if (req.id === reqId) {
        return {
          ...req,
          mentorStatus: isRec ? 'RECOMMENDED' : 'REJECTED',
          mentorComment: mentorCommentText,
          finalStatus: isRec ? 'MENTOR_RECOMMENDED' : 'REJECTED'
        };
      }
      return req;
    }));

    const req = leaveRequests.find(r => r.id === reqId);
    onAddAuditLog({
      action: isRec ? 'RECOMMEND_LEAVE' : 'REJECT_LEAVE',
      entityType: 'LEAVE_REQUEST',
      entityId: String(reqId),
      oldValue: '{"status":"SUBMITTED"}',
      newValue: JSON.stringify({ mentorDecision: isRec ? 'RECOMMENDED' : 'REJECTED', note: mentorCommentText }),
      reason: `Mentor ${isRec ? 'recommended and forwarded to HOD' : 'rejected'} leave for ${req?.studentName}`
    });

    setRecommendModal({ open: false, reqId: null, isRecommend: true });
    setMentorCommentText('');
    alert(isRec ? 'Leave request recommended and forwarded to HOD for final authorization.' : 'Leave request rejected by mentor.');
  };

  return (
    <div className="mentor-container animate-fade-in">
      <div className="section-header">
        <div>
          <h1 className="page-title">Mentor Advisory Center</h1>
          <p className="page-desc">
            Early Warning System (EWS) student risk analytics, explainable factor diagnosis, and student leave review before HOD escalation.
          </p>
        </div>
        <div className="header-meta-badge">
          <span className="dot online" /> {staffSession?.user ? `${staffSession.user.name} (${staffSession.user.department || 'Faculty Mentor'})` : 'Faculty Mentor'}
        </div>
      </div>

      {/* Tabs */}
      <div className="sub-nav-tabs">
        <button
          onClick={() => setActiveTab('risk')}
          className={`tab-btn ${activeTab === 'risk' ? 'active' : ''}`}
        >
          <ShieldAlert size={16} /> Risk Alerts ({highRiskStudents.length} Critical)
        </button>
        <button
          onClick={() => setActiveTab('leaves')}
          className={`tab-btn ${activeTab === 'leaves' ? 'active' : ''}`}
        >
          <Clock size={16} /> Leave Verification ({pendingLeavesForMentor.length})
        </button>
        <button
          onClick={() => setActiveTab('mentees')}
          className={`tab-btn ${activeTab === 'mentees' ? 'active' : ''}`}
        >
          <Users size={16} /> Assigned Mentees ({mentees.length})
        </button>
        <button
          onClick={() => setActiveTab('notices')}
          className={`tab-btn ${activeTab === 'notices' ? 'active' : ''}`}
        >
          <Bell size={16} /> Send Notice
        </button>
      </div>

      {/* TAB 1: RISK ALERTS (EWS) */}
      {activeTab === 'risk' && (
        <div className="tab-pane animate-fade-in">
          <div className="card-header-flex">
            <div>
              <h3>Early Warning System: At-Risk Student Registry</h3>
              <p className="card-desc">
                Machine Learning Random Forest model evaluating attendance delta, internal mark drops, backlog counts, and leave frequency (`EWS-01`).
              </p>
            </div>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <div className="filter-pill-group" style={{ display: 'flex', gap: '6px' }}>
                {['ALL', 'HIGH', 'LOW'].map(lvl => (
                  <button
                    key={lvl}
                    type="button"
                    onClick={() => setRiskFilter(lvl)}
                    className={`btn-xs ${riskFilter === lvl ? 'btn-primary' : 'btn-secondary'}`}
                  >
                    {lvl === 'ALL' ? 'All Risk Levels' : `${lvl} Risk`}
                  </button>
                ))}
              </div>
              <span className="badge-pill warning">Scikit-Learn Random Forest</span>
            </div>
          </div>

          <div className="risk-cards-grid mt-4">
            {filteredRiskStudents.length === 0 ? (
              <div className="glass-card" style={{ padding: '2.5rem', textAlign: 'center', gridColumn: '1 / -1', color: 'var(--text-muted, #94a3b8)' }}>
                <CheckCircle size={38} className="text-success" style={{ margin: '0 auto 0.75rem auto', opacity: 0.8 }} />
                <h4 style={{ color: 'var(--text-main, #f8fafc)', marginBottom: '0.25rem' }}>No Students at Risk</h4>
                <p style={{ fontSize: '0.88rem' }}>No students currently flagged in this risk category by the early warning model.</p>
              </div>
            ) : (
              filteredRiskStudents.map(student => (
                <div
                  key={student.studentId}
                  className={`glass-card risk-item-card ${student.level === 'HIGH' ? 'border-danger' : ''}`}
                >
                  <div className="risk-card-top">
                    <div>
                      <h4 className="student-name">{student.name} ({student.rollNo})</h4>
                      <span className="sub-text">{student.division} • Mentor: {student.mentorName}</span>
                    </div>
                    <div className="risk-score-badge" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '4px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span className="score-num font-mono">{(student.riskScore * 100).toFixed(0)}/100</span>
                        <span className={`badge-pill ${student.level === 'HIGH' ? 'danger' : 'success'}`}>
                          {student.level} RISK
                        </span>
                      </div>
                      {student.trend && (
                        <span className={`badge-pill ${student.trend === 'CRITICAL' || student.trend === 'WORSENING' ? 'danger' : 'info'}`} style={{ fontSize: '0.7rem' }}>
                          Trend: {student.trend}
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="factors-section">
                    <span className="factors-title">Top 3 Contributing Explainability Factors (EWS-04):</span>
                    <ul className="factors-list">
                      {(student.topFactors || []).map((f, idx) => (
                        <li key={idx}>
                          <TrendingDown size={14} className="text-danger flex-shrink-0" />
                          <span>{f}</span>
                        </li>
                      ))}
                    </ul>
                  </div>

                  {/* History of Actions */}
                  <div className="actions-history-box">
                    <span className="actions-title">Documented Mentor Actions ({(student.actionsTaken || []).length}):</span>
                    {(!student.actionsTaken || student.actionsTaken.length === 0) ? (
                      <p className="text-muted text-xs italic">No intervention action recorded yet. Mentor contact required.</p>
                    ) : (
                      student.actionsTaken.map(act => (
                        <div key={act.id} className="history-pill">
                          <span className="history-date font-mono">{act.date}:</span> {act.note}
                        </div>
                      ))
                    )}
                  </div>

                  <div className="risk-card-actions">
                    <button
                      onClick={() => setSelectedStudentForAction(student)}
                      className="btn-primary w-full"
                    >
                      <MessageSquare size={14} /> Log Student Contact &amp; Intervention Action
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* TAB 2: LEAVE REQUESTS */}
      {activeTab === 'leaves' && (
        <div className="tab-pane animate-fade-in">
          <div className="glass-card">
            <div className="card-header-flex">
              <div>
                <h3>Student Leave Applications (First Level Review)</h3>
                <p className="card-desc">
                  Review student reasons, verify documents, and recommend to HOD for final approval (`LVE-02`).
                </p>
              </div>
              <span className="badge-pill info">{pendingLeavesForMentor.length} Pending Mentor Action</span>
            </div>

            {pendingLeavesForMentor.length === 0 ? (
              <div className="empty-state-box">
                <CheckCircle size={36} className="text-success" />
                <p>No unreviewed leave applications from your mentees.</p>
              </div>
            ) : (
              <div className="request-card-grid">
                {pendingLeavesForMentor.map(req => (
                  <div key={req.id} className="approval-card">
                    <div className="approval-card-header">
                      <div>
                        <h4>{req.studentName} ({req.rollNo})</h4>
                        <span className="date-range">📅 {req.fromDate} to {req.toDate} ({req.daysCount} days)</span>
                      </div>
                      <span className="badge-pill warning">Awaiting Mentor</span>
                    </div>

                    <div className="reason-quote">
                      <strong>Reason:</strong> "{req.reason}"
                    </div>

                    {req.attachmentName && (
                      <div className="attachment-chip">
                        <FileText size={14} /> Attachment: <code>{req.attachmentName}</code>
                      </div>
                    )}

                    <div className="approval-card-actions">
                      <button
                        onClick={() => setRecommendModal({ open: true, reqId: req.id, isRecommend: false })}
                        className="btn-danger-outline"
                      >
                        <XCircle size={15} /> Reject
                      </button>
                      <button
                        onClick={() => setRecommendModal({ open: true, reqId: req.id, isRecommend: true })}
                        className="btn-success"
                      >
                        <ArrowRight size={15} /> Recommend &amp; Forward to HOD
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 3: ASSIGNED MENTEES */}
      {activeTab === 'mentees' && (
        <div className="tab-pane animate-fade-in">
          <div className="table-card">
            <table className="custom-table">
              <thead>
                <tr>
                  <th>Roll No</th>
                  <th>Student Name</th>
                  <th>Division</th>
                  <th>Attendance %</th>
                  <th>Backlogs</th>
                  <th>Fees</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {mentees.length === 0 ? (
                  <tr>
                    <td colSpan="7" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted, #94a3b8)' }}>
                      No mentees assigned or found in the database.
                    </td>
                  </tr>
                ) : (
                  mentees.map(m => (
                    <tr key={m.id}>
                      <td className="font-mono">{m.rollNo || 'N/A'}</td>
                      <td><strong>{m.name}</strong></td>
                      <td>{m.division || '—'}</td>
                      <td>
                        <span className={(m.attendanceRate || 0) < 75 ? 'text-danger font-bold' : 'text-success'}>
                          {m.attendanceRate != null ? `${m.attendanceRate}%` : 'N/A'}
                        </span>
                      </td>
                      <td>{m.backlogs || 0}</td>
                      <td>
                        <span className={`badge-pill ${m.feeStatus === 'PAID' ? 'success' : 'warning'}`}>
                          {m.feeStatus || 'PENDING'}
                        </span>
                      </td>
                      <td>
                        <span className="badge-pill success">Active</span>
                      </td>
                    </tr>
                  ))
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
            publisherRole="MENTOR"
            publisherName={staffSession?.user?.name || "Faculty Mentor"}
            publisherId={staffSession?.user?.id || 1}
            defaultDepartment={staffSession?.user?.department || "Academic Affairs"}
            onNoticePublished={handleNoticePublished}
            onAddAuditLog={onAddAuditLog}
            initialStudents={users?.filter(u => u.role === 'STUDENT')}
            cardTitle="Send Notice / Mentee Advisory"
            cardDescription="Broadcast announcements to your mentees, department classes, or specific students."
          />
        </div>
      )}

      {/* Log Action Modal */}
      {selectedStudentForAction && (
        <div className="modal-backdrop">
          <div className="modal-dialog animate-fade-in">
            <div className="modal-header">
              <h3>Log Intervention for {selectedStudentForAction.name}</h3>
              <button onClick={() => setSelectedStudentForAction(null)} className="modal-close">&times;</button>
            </div>
            <form onSubmit={handleSaveRiskAction} className="form-stack">
              <p className="modal-instruction">
                Requirement EWS-05: An alert cannot be marked handled without recording the specific counseling or remediation action taken.
              </p>
              <div className="form-group">
                <label>Intervention / Meeting Notes</label>
                <textarea
                  rows={4}
                  placeholder="e.g. Conducted 1-on-1 counseling session regarding consecutive absences; parent informed; assigned peer tutor for Machine Learning..."
                  value={mentorActionNote}
                  onChange={e => setMentorActionNote(e.target.value)}
                  required
                />
              </div>
              <div className="modal-footer">
                <button type="button" onClick={() => setSelectedStudentForAction(null)} className="btn-secondary">
                  Cancel
                </button>
                <button type="submit" className="btn-primary">
                  Save Action Note
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Recommend/Reject Modal */}
      {recommendModal.open && (
        <div className="modal-backdrop">
          <div className="modal-dialog animate-fade-in">
            <div className="modal-header">
              <h3>{recommendModal.isRecommend ? 'Recommend Leave to HOD' : 'Reject Leave Request'}</h3>
              <button onClick={() => setRecommendModal({ open: false, reqId: null, isRecommend: true })} className="modal-close">&times;</button>
            </div>
            <form onSubmit={handleLeaveDecision} className="form-stack">
              <div className="form-group">
                <label>Mentor Notes &amp; Observations for HOD</label>
                <textarea
                  rows={3}
                  placeholder="e.g. Student has provided verified medical documents. Recommending approval."
                  value={mentorCommentText}
                  onChange={e => setMentorCommentText(e.target.value)}
                  required
                />
              </div>
              <div className="modal-footer">
                <button type="button" onClick={() => setRecommendModal({ open: false, reqId: null, isRecommend: true })} className="btn-secondary">
                  Cancel
                </button>
                <button type="submit" className={recommendModal.isRecommend ? 'btn-success' : 'btn-danger'}>
                  {recommendModal.isRecommend ? 'Forward to HOD' : 'Confirm Rejection'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
