import React, { useState, useEffect } from 'react';
import { 
  Users, 
  Check, 
  X, 
  AlertCircle, 
  CheckCircle2, 
  RefreshCw, 
  ShieldCheck, 
  Clock 
} from 'lucide-react';
import { 
  getPendingApprovalsApi, 
  approveStaffApi, 
  rejectStaffApi 
} from '../../utils/staffAuthService';
import './StaffAuth.css';

export default function StaffPendingApprovalsView({ staffSession, onNavigate }) {
  const [pendingList, setPendingList] = useState([]);
  const [loading, setLoading] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState(null);
  const [apiError, setApiError] = useState('');
  const [apiSuccess, setApiSuccess] = useState('');

  const token = staffSession?.accessToken || staffSession?.token;

  const loadApprovals = async () => {
    setLoading(true);
    setApiError('');
    try {
      const data = await getPendingApprovalsApi(token);
      setPendingList(data);
    } catch (err) {
      setApiError(err.message || 'Failed to load pending staff registrations.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadApprovals();
  }, [token]);

  const handleApprove = async (staffId, name) => {
    setActionLoadingId(staffId);
    setApiError('');
    setApiSuccess('');
    try {
      await approveStaffApi(staffId, token);
      setApiSuccess(`Successfully approved account for ${name}.`);
      loadApprovals();
    } catch (err) {
      setApiError(err.message || 'Approval action failed.');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleReject = async (staffId, name) => {
    const reason = window.prompt(`Please provide a rejection reason for ${name}:`, 'Credentials could not be verified by administration');
    if (reason === null) return; // user cancelled

    setActionLoadingId(staffId);
    setApiError('');
    setApiSuccess('');
    try {
      await rejectStaffApi(staffId, reason, token);
      setApiSuccess(`Staff account for ${name} has been rejected.`);
      loadApprovals();
    } catch (err) {
      setApiError(err.message || 'Rejection action failed.');
    } finally {
      setActionLoadingId(null);
    }
  };

  return (
    <div className="staff-approvals-card">
      <div className="staff-approvals-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <div className="staff-auth-icon-wrap" style={{ width: '42px', height: '42px', marginBottom: 0 }}>
            <ShieldCheck size={22} />
          </div>
          <div>
            <h3 style={{ margin: 0, fontSize: '1.25rem', color: '#f8fafc' }}>
              Pending Staff Approvals
            </h3>
            <p style={{ margin: '0.2rem 0 0 0', fontSize: '0.85rem', color: '#94a3b8' }}>
              Review and activate self-registered HOD and Admin governance accounts
            </p>
          </div>
        </div>

        <button 
          type="button" 
          onClick={loadApprovals} 
          disabled={loading}
          className="btn-reject"
          style={{ background: 'rgba(99, 102, 241, 0.15)', color: '#a5b4fc', borderColor: 'rgba(99, 102, 241, 0.3)' }}
        >
          <RefreshCw size={14} className={loading ? 'spinning' : ''} />
          Refresh
        </button>
      </div>

      {apiError && (
        <div className="staff-alert staff-alert-error" style={{ marginBottom: '1.25rem' }}>
          <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
          <div>{apiError}</div>
        </div>
      )}

      {apiSuccess && (
        <div className="staff-alert staff-alert-success" style={{ marginBottom: '1.25rem' }}>
          <CheckCircle2 size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
          <div>{apiSuccess}</div>
        </div>
      )}

      {loading && pendingList.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#94a3b8' }}>
          Loading pending applications...
        </div>
      ) : pendingList.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#94a3b8', border: '1px dashed #334155', borderRadius: '12px' }}>
          <CheckCircle2 size={36} style={{ color: '#10b981', margin: '0 auto 0.75rem auto', display: 'block' }} />
          <h4 style={{ margin: '0 0 0.25rem 0', color: '#f8fafc' }}>No Pending Applications</h4>
          <p style={{ margin: 0, fontSize: '0.88rem' }}>
            All staff registrations have been reviewed and approved.
          </p>
        </div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table className="staff-table">
            <thead>
              <tr>
                <th>Applicant</th>
                <th>Role</th>
                <th>Department</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {pendingList.map((staff) => (
                <tr key={staff.id}>
                  <td>
                    <div style={{ fontWeight: 600, color: '#f8fafc' }}>{staff.name}</div>
                    <div style={{ fontSize: '0.8rem', color: '#94a3b8' }}>{staff.email}</div>
                  </td>
                  <td>
                    <span className="staff-badge staff-badge-role">
                      {staff.role}
                    </span>
                  </td>
                  <td>{staff.department || 'N/A'}</td>
                  <td>
                    <span className="staff-badge staff-badge-pending">
                      <Clock size={12} />
                      {staff.status}
                    </span>
                  </td>
                  <td>
                    <div className="staff-actions-cell">
                      <button 
                        type="button"
                        onClick={() => handleApprove(staff.id, staff.name)}
                        disabled={actionLoadingId === staff.id}
                        className="btn-approve"
                      >
                        <Check size={14} />
                        Approve
                      </button>
                      <button 
                        type="button"
                        onClick={() => handleReject(staff.id, staff.name)}
                        disabled={actionLoadingId === staff.id}
                        className="btn-reject"
                      >
                        <X size={14} />
                        Reject
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
