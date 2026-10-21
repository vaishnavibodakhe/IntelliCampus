import React, { useState, useEffect, useCallback } from 'react';
import { 
  Users, 
  UserPlus, 
  Search, 
  Filter, 
  KeyRound, 
  ShieldCheck, 
  ShieldAlert, 
  Lock, 
  CheckCircle2, 
  AlertCircle, 
  Trash2, 
  ToggleLeft, 
  ToggleRight, 
  RefreshCw, 
  Copy, 
  Check, 
  X, 
  Building2, 
  Mail, 
  BadgeCheck, 
  AlertTriangle 
} from 'lucide-react';
import { api } from '../../api/client';

export default function AdminStaffManagementView({ staffSession }) {
  const [staffList, setStaffList] = useState([]);
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState('');
  const [apiSuccess, setApiSuccess] = useState('');

  // Filters
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [deptFilter, setDeptFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // Modals
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [tempPasswordModal, setTempPasswordModal] = useState({ open: false, password: '', email: '', name: '' });
  const [actionLoadingId, setActionLoadingId] = useState(null);
  const [copied, setCopied] = useState(false);

  // New staff form state
  const [newStaffForm, setNewStaffForm] = useState({
    name: '',
    email: '',
    employeeCode: '',
    role: 'FACULTY',
    department: 'Computer Science',
    specialization: ''
  });
  const [formErrors, setFormErrors] = useState({});

  const departments = [
    'Computer Science',
    'Information Technology',
    'Mechanical Engineering',
    'Civil Engineering',
    'Electronics & Telecommunication'
  ];

  const fetchStaff = useCallback(async () => {
    setLoading(true);
    setApiError('');
    try {
      const filters = {};
      if (roleFilter !== 'ALL') filters.role = roleFilter;
      if (deptFilter !== 'ALL') filters.department = deptFilter;
      if (statusFilter !== 'ALL') filters.status = statusFilter;
      if (searchQuery.trim()) filters.search = searchQuery.trim();

      const data = await api.getAdminStaff(filters);
      setStaffList(Array.isArray(data) ? data : []);
    } catch (err) {
      setApiError(err.message || 'Failed to load staff list.');
    } finally {
      setLoading(false);
    }
  }, [roleFilter, deptFilter, statusFilter, searchQuery]);

  useEffect(() => {
    fetchStaff();
  }, [fetchStaff]);

  const handleCopyPassword = () => {
    if (tempPasswordModal.password) {
      navigator.clipboard.writeText(tempPasswordModal.password);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const validateCreateForm = () => {
    const errors = {};
    if (!newStaffForm.name.trim()) errors.name = 'Full name is required';
    if (!newStaffForm.email.trim() || !newStaffForm.email.includes('@')) errors.email = 'Valid institutional email is required';
    if (!newStaffForm.employeeCode.trim()) errors.employeeCode = 'Employee code is required';
    if (!newStaffForm.role) errors.role = 'Role is required';
    if (!newStaffForm.department) errors.department = 'Department is required';
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    if (!validateCreateForm()) return;

    setLoading(true);
    setApiError('');
    setApiSuccess('');

    try {
      const res = await api.createAdminStaff(newStaffForm);
      setShowCreateModal(false);
      setNewStaffForm({
        name: '',
        email: '',
        employeeCode: '',
        role: 'FACULTY',
        department: 'Computer Science',
        specialization: ''
      });

      if (res && res.temporaryPassword) {
        setTempPasswordModal({
          open: true,
          password: res.temporaryPassword,
          email: res.email,
          name: res.name
        });
      } else {
        setApiSuccess(`Staff account for ${res.name} created successfully! Welcome email sent.`);
        setTimeout(() => setApiSuccess(''), 4000);
      }
      fetchStaff();
    } catch (err) {
      setApiError(err.message || 'Failed to create staff account.');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleStatus = async (user) => {
    const newStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    setActionLoadingId(user.id);
    setApiError('');
    setApiSuccess('');

    try {
      await api.updateAdminStaffStatus(user.id, { status: newStatus });
      setApiSuccess(`Staff account for ${user.name} is now ${newStatus}.`);
      setTimeout(() => setApiSuccess(''), 3000);
      fetchStaff();
    } catch (err) {
      setApiError(err.message || 'Failed to update staff status.');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleResetPassword = async (user) => {
    if (!window.confirm(`Are you sure you want to reset the password for ${user.name} (${user.email})? A new temporary password will be generated.`)) {
      return;
    }

    setActionLoadingId(user.id);
    setApiError('');
    setApiSuccess('');

    try {
      const res = await api.resetAdminStaffPassword(user.id);
      if (res && res.temporaryPassword) {
        setTempPasswordModal({
          open: true,
          password: res.temporaryPassword,
          email: user.email,
          name: user.name
        });
      } else {
        setApiSuccess(`Password reset email dispatched to ${user.email}.`);
        setTimeout(() => setApiSuccess(''), 4000);
      }
      fetchStaff();
    } catch (err) {
      setApiError(err.message || 'Failed to reset password.');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleDeleteStaff = async (user) => {
    if (!window.confirm(`Permanently soft-delete staff account for ${user.name} (${user.email})? The account will be marked as DISABLED and all active sessions will be revoked.`)) {
      return;
    }

    setActionLoadingId(user.id);
    setApiError('');
    setApiSuccess('');

    try {
      await api.deleteAdminStaff(user.id);
      setApiSuccess(`Staff account for ${user.name} successfully deactivated and archived.`);
      setTimeout(() => setApiSuccess(''), 3000);
      fetchStaff();
    } catch (err) {
      setApiError(err.message || 'Failed to delete staff account.');
    } finally {
      setActionLoadingId(null);
    }
  };

  return (
    <div className="tab-pane animate-fade-in" style={{ padding: '1rem 0' }}>
      {/* Pane Top Toolbar */}
      <div className="pane-toolbar" style={{ flexWrap: 'wrap', gap: '12px', marginBottom: '1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap', flex: 1 }}>
          <div className="search-box" style={{ minWidth: '240px', flex: 1 }}>
            <Search size={16} />
            <input 
              type="text" 
              placeholder="Search by name, email, or code..." 
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>

          <select 
            value={roleFilter} 
            onChange={(e) => setRoleFilter(e.target.value)}
            className="filter-select"
            style={{ padding: '0.5rem 0.8rem', borderRadius: '8px', background: 'var(--card-bg, #1e293b)', color: '#fff', border: '1px solid #334155' }}
          >
            <option value="ALL">All Roles</option>
            <option value="HOD">HOD</option>
            <option value="FACULTY">Faculty</option>
            <option value="MENTOR">Mentor</option>
          </select>

          <select 
            value={deptFilter} 
            onChange={(e) => setDeptFilter(e.target.value)}
            className="filter-select"
            style={{ padding: '0.5rem 0.8rem', borderRadius: '8px', background: 'var(--card-bg, #1e293b)', color: '#fff', border: '1px solid #334155' }}
          >
            <option value="ALL">All Departments</option>
            {departments.map((dept) => (
              <option key={dept} value={dept}>{dept}</option>
            ))}
          </select>

          <select 
            value={statusFilter} 
            onChange={(e) => setStatusFilter(e.target.value)}
            className="filter-select"
            style={{ padding: '0.5rem 0.8rem', borderRadius: '8px', background: 'var(--card-bg, #1e293b)', color: '#fff', border: '1px solid #334155' }}
          >
            <option value="ALL">All Statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="DISABLED">Disabled</option>
            <option value="LOCKED">Locked</option>
          </select>

          <button 
            type="button" 
            onClick={fetchStaff} 
            className="btn-secondary" 
            style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '0.5rem 0.8rem', borderRadius: '8px', cursor: 'pointer' }}
            title="Refresh staff list"
          >
            <RefreshCw size={15} className={loading ? 'spin' : ''} />
            <span>Refresh</span>
          </button>
        </div>

        <div>
          <button 
            type="button" 
            onClick={() => setShowCreateModal(true)}
            className="btn-primary"
            style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '0.6rem 1.2rem', borderRadius: '8px', fontWeight: 600, background: '#6366f1', color: '#fff', border: 'none', cursor: 'pointer' }}
          >
            <UserPlus size={18} />
            <span>Create Staff Account</span>
          </button>
        </div>
      </div>

      {/* Global Alerts */}
      {apiError && (
        <div className="staff-alert staff-alert-error" style={{ marginBottom: '1.25rem', display: 'flex', alignItems: 'center', gap: '10px', background: 'rgba(239, 68, 68, 0.15)', border: '1px solid #ef4444', color: '#fca5a5', padding: '0.75rem 1rem', borderRadius: '8px' }}>
          <AlertCircle size={18} style={{ flexShrink: 0 }} />
          <div>{apiError}</div>
        </div>
      )}

      {apiSuccess && (
        <div className="staff-alert staff-alert-success" style={{ marginBottom: '1.25rem', display: 'flex', alignItems: 'center', gap: '10px', background: 'rgba(16, 185, 129, 0.15)', border: '1px solid #10b981', color: '#6ee7b7', padding: '0.75rem 1rem', borderRadius: '8px' }}>
          <CheckCircle2 size={18} style={{ flexShrink: 0 }} />
          <div>{apiSuccess}</div>
        </div>
      )}

      {/* Staff List Table */}
      <div className="data-table-card" style={{ background: 'var(--card-bg, #1e293b)', borderRadius: '12px', border: '1px solid #334155', overflow: 'hidden' }}>
        <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid #334155', background: 'rgba(0,0,0,0.2)' }}>
              <th style={{ padding: '1rem' }}>Employee Code</th>
              <th style={{ padding: '1rem' }}>Staff Member</th>
              <th style={{ padding: '1rem' }}>Role</th>
              <th style={{ padding: '1rem' }}>Department</th>
              <th style={{ padding: '1rem' }}>Status</th>
              <th style={{ padding: '1rem' }}>Must Change Pass</th>
              <th style={{ padding: '1rem', textAlign: 'right' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading && staffList.length === 0 ? (
              <tr>
                <td colSpan="7" style={{ textAlign: 'center', padding: '3rem', color: '#94a3b8' }}>
                  <div className="loading-spinner" style={{ margin: '0 auto 1rem' }} />
                  Loading staff accounts...
                </td>
              </tr>
            ) : staffList.length === 0 ? (
              <tr>
                <td colSpan="7" style={{ textAlign: 'center', padding: '3rem', color: '#94a3b8' }}>
                  <Users size={36} style={{ margin: '0 auto 0.75rem', opacity: 0.5 }} />
                  <div>No staff accounts found matching criteria.</div>
                </td>
              </tr>
            ) : (
              staffList.map((user) => {
                const isActionLoading = actionLoadingId === user.id;
                const roleBadgeColor = user.role === 'HOD' ? '#8b5cf6' : user.role === 'FACULTY' ? '#3b82f6' : '#10b981';
                return (
                  <tr key={user.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                    <td style={{ padding: '1rem', fontWeight: 600, fontFamily: 'monospace' }}>
                      {user.employeeCode || user.userCode || '—'}
                    </td>
                    <td style={{ padding: '1rem' }}>
                      <div style={{ fontWeight: 600, color: '#f8fafc' }}>{user.name}</div>
                      <div style={{ fontSize: '0.82rem', color: '#94a3b8' }}>{user.email}</div>
                    </td>
                    <td style={{ padding: '1rem' }}>
                      <span style={{ 
                        display: 'inline-block',
                        padding: '3px 8px', 
                        borderRadius: '6px', 
                        fontSize: '0.75rem', 
                        fontWeight: 700, 
                        background: `${roleBadgeColor}25`, 
                        color: roleBadgeColor,
                        border: `1px solid ${roleBadgeColor}50`
                      }}>
                        {user.role}
                      </span>
                    </td>
                    <td style={{ padding: '1rem', color: '#cbd5e1' }}>
                      {user.department || '—'}
                    </td>
                    <td style={{ padding: '1rem' }}>
                      <span style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '5px',
                        padding: '3px 8px',
                        borderRadius: '6px',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        background: user.status === 'ACTIVE' ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
                        color: user.status === 'ACTIVE' ? '#10b981' : '#ef4444',
                        border: `1px solid ${user.status === 'ACTIVE' ? '#10b98140' : '#ef444440'}`
                      }}>
                        {user.status === 'ACTIVE' ? <CheckCircle2 size={12} /> : <AlertTriangle size={12} />}
                        {user.status}
                      </span>
                    </td>
                    <td style={{ padding: '1rem' }}>
                      {user.mustChangePassword ? (
                        <span style={{ color: '#fbbf24', fontSize: '0.82rem', fontWeight: 600 }}>YES (Required)</span>
                      ) : (
                        <span style={{ color: '#94a3b8', fontSize: '0.82rem' }}>No</span>
                      )}
                    </td>
                    <td style={{ padding: '1rem', textAlign: 'right' }}>
                      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', alignItems: 'center' }}>
                        <button
                          type="button"
                          onClick={() => handleToggleStatus(user)}
                          disabled={isActionLoading}
                          title={user.status === 'ACTIVE' ? 'Deactivate Account' : 'Activate Account'}
                          style={{
                            background: 'transparent',
                            border: '1px solid #475569',
                            color: user.status === 'ACTIVE' ? '#ef4444' : '#10b981',
                            padding: '6px 10px',
                            borderRadius: '6px',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                            fontSize: '0.8rem'
                          }}
                        >
                          {user.status === 'ACTIVE' ? <ToggleRight size={16} /> : <ToggleLeft size={16} />}
                          <span>{user.status === 'ACTIVE' ? 'Disable' : 'Enable'}</span>
                        </button>

                        <button
                          type="button"
                          onClick={() => handleResetPassword(user)}
                          disabled={isActionLoading}
                          title="Generate New Temporary Password"
                          style={{
                            background: 'transparent',
                            border: '1px solid #475569',
                            color: '#fbbf24',
                            padding: '6px 10px',
                            borderRadius: '6px',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                            fontSize: '0.8rem'
                          }}
                        >
                          <KeyRound size={15} />
                          <span>Reset</span>
                        </button>

                        <button
                          type="button"
                          onClick={() => handleDeleteStaff(user)}
                          disabled={isActionLoading}
                          title="Soft Delete Account"
                          style={{
                            background: 'transparent',
                            border: '1px solid #ef444440',
                            color: '#f87171',
                            padding: '6px',
                            borderRadius: '6px',
                            cursor: 'pointer'
                          }}
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* CREATE STAFF MODAL */}
      {showCreateModal && (
        <div className="modal-backdrop" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 10000, padding: '1rem' }}>
          <div className="modal-card" style={{ background: '#1e293b', border: '1px solid #475569', borderRadius: '16px', maxWidth: '540px', width: '100%', padding: '2rem', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div style={{ width: '40px', height: '40px', borderRadius: '10px', background: 'rgba(99, 102, 241, 0.2)', border: '1px solid #6366f1', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#818cf8' }}>
                  <UserPlus size={22} />
                </div>
                <div>
                  <h3 style={{ margin: 0, fontSize: '1.25rem', color: '#f8fafc', fontWeight: 700 }}>Provision Staff Account</h3>
                  <p style={{ margin: 0, fontSize: '0.82rem', color: '#94a3b8' }}>Create an authorized HOD, Faculty, or Mentor profile</p>
                </div>
              </div>
              <button 
                type="button" 
                onClick={() => setShowCreateModal(false)}
                style={{ background: 'transparent', border: 'none', color: '#94a3b8', cursor: 'pointer' }}
              >
                <X size={20} />
              </button>
            </div>

            <form onSubmit={handleCreateSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', color: '#cbd5e1', marginBottom: '4px', fontWeight: 500 }}>
                  Role <span style={{ color: '#ef4444' }}>*</span>
                </label>
                <select
                  value={newStaffForm.role}
                  onChange={(e) => setNewStaffForm(prev => ({ ...prev, role: e.target.value }))}
                  style={{ width: '100%', padding: '0.65rem 0.8rem', borderRadius: '8px', background: '#0f172a', border: '1px solid #334155', color: '#f8fafc' }}
                >
                  <option value="HOD">HOD (Head of Department)</option>
                  <option value="FACULTY">Faculty</option>
                  <option value="MENTOR">Mentor</option>
                </select>
                <p style={{ fontSize: '0.75rem', color: '#94a3b8', margin: '4px 0 0 0' }}>
                  {newStaffForm.role === 'HOD' ? 'Note: Only one active HOD is permitted per department.' : 'Account will receive a secure temporary password requiring change on first login.'}
                </p>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', color: '#cbd5e1', marginBottom: '4px', fontWeight: 500 }}>
                  Full Name <span style={{ color: '#ef4444' }}>*</span>
                </label>
                <input
                  type="text"
                  placeholder="e.g. Dr. Alan Turing"
                  value={newStaffForm.name}
                  onChange={(e) => {
                    setNewStaffForm(prev => ({ ...prev, name: e.target.value }));
                    if (formErrors.name) setFormErrors(prev => ({ ...prev, name: '' }));
                  }}
                  style={{ width: '100%', padding: '0.65rem 0.8rem', borderRadius: '8px', background: '#0f172a', border: formErrors.name ? '1px solid #ef4444' : '1px solid #334155', color: '#f8fafc' }}
                />
                {formErrors.name && <span style={{ color: '#ef4444', fontSize: '0.78rem' }}>{formErrors.name}</span>}
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', color: '#cbd5e1', marginBottom: '4px', fontWeight: 500 }}>
                  Institutional Email <span style={{ color: '#ef4444' }}>*</span>
                </label>
                <input
                  type="email"
                  placeholder="e.g. turing@intellicampus.edu"
                  value={newStaffForm.email}
                  onChange={(e) => {
                    setNewStaffForm(prev => ({ ...prev, email: e.target.value }));
                    if (formErrors.email) setFormErrors(prev => ({ ...prev, email: '' }));
                  }}
                  style={{ width: '100%', padding: '0.65rem 0.8rem', borderRadius: '8px', background: '#0f172a', border: formErrors.email ? '1px solid #ef4444' : '1px solid #334155', color: '#f8fafc' }}
                />
                {formErrors.email && <span style={{ color: '#ef4444', fontSize: '0.78rem' }}>{formErrors.email}</span>}
              </div>

              <div style={{ display: 'flex', gap: '1rem' }}>
                <div style={{ flex: 1 }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', color: '#cbd5e1', marginBottom: '4px', fontWeight: 500 }}>
                    Employee Code <span style={{ color: '#ef4444' }}>*</span>
                  </label>
                  <input
                    type="text"
                    placeholder="e.g. FAC-2026-001"
                    value={newStaffForm.employeeCode}
                    onChange={(e) => {
                      setNewStaffForm(prev => ({ ...prev, employeeCode: e.target.value }));
                      if (formErrors.employeeCode) setFormErrors(prev => ({ ...prev, employeeCode: '' }));
                    }}
                    style={{ width: '100%', padding: '0.65rem 0.8rem', borderRadius: '8px', background: '#0f172a', border: formErrors.employeeCode ? '1px solid #ef4444' : '1px solid #334155', color: '#f8fafc' }}
                  />
                  {formErrors.employeeCode && <span style={{ color: '#ef4444', fontSize: '0.78rem' }}>{formErrors.employeeCode}</span>}
                </div>

                <div style={{ flex: 1 }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', color: '#cbd5e1', marginBottom: '4px', fontWeight: 500 }}>
                    Department <span style={{ color: '#ef4444' }}>*</span>
                  </label>
                  <select
                    value={newStaffForm.department}
                    onChange={(e) => setNewStaffForm(prev => ({ ...prev, department: e.target.value }))}
                    style={{ width: '100%', padding: '0.65rem 0.8rem', borderRadius: '8px', background: '#0f172a', border: '1px solid #334155', color: '#f8fafc' }}
                  >
                    {departments.map((d) => (
                      <option key={d} value={d}>{d}</option>
                    ))}
                  </select>
                </div>
              </div>

              {newStaffForm.role === 'MENTOR' && (
                <div>
                  <label style={{ display: 'block', fontSize: '0.85rem', color: '#cbd5e1', marginBottom: '4px', fontWeight: 500 }}>
                    Specialization (Optional)
                  </label>
                  <input
                    type="text"
                    placeholder="e.g. Academic Performance & Career Counseling"
                    value={newStaffForm.specialization}
                    onChange={(e) => setNewStaffForm(prev => ({ ...prev, specialization: e.target.value }))}
                    style={{ width: '100%', padding: '0.65rem 0.8rem', borderRadius: '8px', background: '#0f172a', border: '1px solid #334155', color: '#f8fafc' }}
                  />
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '1rem' }}>
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  style={{ padding: '0.65rem 1.2rem', borderRadius: '8px', background: 'transparent', border: '1px solid #475569', color: '#cbd5e1', cursor: 'pointer' }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={loading}
                  style={{ padding: '0.65rem 1.4rem', borderRadius: '8px', background: '#6366f1', border: 'none', color: '#ffffff', fontWeight: 600, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '6px' }}
                >
                  {loading ? 'Provisioning...' : 'Provision Account'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* TEMPORARY PASSWORD DISPLAY MODAL */}
      {tempPasswordModal.open && (
        <div className="modal-backdrop" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.8)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 10001, padding: '1rem' }}>
          <div className="modal-card" style={{ background: '#0f172a', border: '2px solid #6366f1', borderRadius: '16px', maxWidth: '500px', width: '100%', padding: '2rem', textAlign: 'center', boxShadow: '0 25px 50px -12px rgba(99,102,241,0.25)' }}>
            <div style={{ width: '60px', height: '60px', borderRadius: '16px', background: 'rgba(99, 102, 241, 0.2)', color: '#818cf8', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1.25rem' }}>
              <KeyRound size={32} />
            </div>

            <h3 style={{ fontSize: '1.4rem', fontWeight: 700, color: '#f8fafc', marginBottom: '0.5rem' }}>
              Temporary Password Generated
            </h3>

            <p style={{ color: '#94a3b8', fontSize: '0.9rem', lineHeight: 1.5, marginBottom: '1.5rem' }}>
              Account for <strong>{tempPasswordModal.name}</strong> ({tempPasswordModal.email}) has been configured with <span style={{ color: '#fbbf24', fontWeight: 600 }}>MUST_CHANGE_PASSWORD</span>.
            </p>

            <div style={{ background: '#1e293b', border: '1px dashed #6366f1', borderRadius: '10px', padding: '1rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
              <span style={{ fontFamily: 'monospace', fontSize: '1.25rem', letterSpacing: '0.08em', color: '#a5b4fc', fontWeight: 700 }}>
                {tempPasswordModal.password}
              </span>
              <button
                type="button"
                onClick={handleCopyPassword}
                style={{
                  background: copied ? '#10b981' : '#6366f1',
                  border: 'none',
                  color: '#ffffff',
                  padding: '6px 12px',
                  borderRadius: '6px',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  transition: 'all 0.2s'
                }}
              >
                {copied ? <Check size={16} /> : <Copy size={16} />}
                <span>{copied ? 'Copied' : 'Copy'}</span>
              </button>
            </div>

            <div style={{ background: 'rgba(245, 158, 11, 0.1)', border: '1px solid rgba(245, 158, 11, 0.3)', borderRadius: '8px', padding: '0.75rem 1rem', color: '#fbbf24', fontSize: '0.82rem', textAlign: 'left', display: 'flex', gap: '8px', alignItems: 'flex-start', marginBottom: '1.5rem' }}>
              <AlertTriangle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
              <div>
                <strong>IMPORTANT:</strong> This password is shown only once and cannot be retrieved later. Securely provide this credential to the user.
              </div>
            </div>

            <button
              type="button"
              onClick={() => setTempPasswordModal({ open: false, password: '', email: '', name: '' })}
              style={{
                width: '100%',
                padding: '0.75rem',
                borderRadius: '8px',
                background: '#6366f1',
                border: 'none',
                color: '#ffffff',
                fontWeight: 600,
                fontSize: '0.95rem',
                cursor: 'pointer'
              }}
            >
              Done & Dismiss
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
