import React, { useState, useEffect, useCallback } from 'react';
import { 
  Users, 
  Layers, 
  Bell, 
  FileCheck2, 
  BrainCircuit, 
  Plus, 
  Upload, 
  Search, 
  CheckCircle2, 
  AlertCircle,
  Building2,
  Trash2,
  KeyRound,
  Eye,
  EyeOff,
  ExternalLink,
  MapPin,
  Edit3,
  FileSpreadsheet,
  ShieldCheck,
  LogOut,
  RefreshCw,
  Clock,
  Check,
  X,
  Sparkles,
  Download,
  AlertTriangle
} from 'lucide-react';
import { api } from '../api/client';
import { 
  getPendingApprovalsApi, 
  approveStaffApi, 
  rejectStaffApi,
  changePasswordApi
} from '../utils/staffAuthService';
import StaffChangePasswordView from './staff/StaffChangePasswordView';
import PrincipalAssistantDrawer from './PrincipalAssistantDrawer';

export default function PrincipalView({
  staffSession,
  onLogout,
  onOpenAuditModal,
  onOpenChatbot
}) {
  const token = staffSession?.accessToken || staffSession?.token;
  const [activeTab, setActiveTab] = useState('users'); // 'users' | 'academic' | 'notices' | 'documents' | 'approvals' | 'audit'
  const [showChangePasswordModal, setShowChangePasswordModal] = useState(false);
  const [isAssistantOpen, setIsAssistantOpen] = useState(false);
  const [apiError, setApiError] = useState('');
  const [apiSuccess, setApiSuccess] = useState('');

  // -------------------------------------------------------------
  // Real State Stores (Loaded from Backend DB)
  // -------------------------------------------------------------
  const [users, setUsers] = useState([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [userSearch, setUserSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [deptFilter, setDeptFilter] = useState('ALL');

  const [departments, setDepartments] = useState([]);
  const [divisions, setDivisions] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [loadingAcademic, setLoadingAcademic] = useState(false);
  const [academicSubTab, setAcademicSubTab] = useState('departments'); // 'departments' | 'divisions' | 'subjects' | 'rooms'

  const [notices, setNotices] = useState([]);
  const [loadingNotices, setLoadingNotices] = useState(false);

  const [credentials, setCredentials] = useState([]);
  const [loadingCredentials, setLoadingCredentials] = useState(false);

  const [pendingApprovals, setPendingApprovals] = useState([]);
  const [loadingApprovals, setLoadingApprovals] = useState(false);

  const [auditChain, setAuditChain] = useState([]);
  const [loadingAudit, setLoadingAudit] = useState(false);
  const [chainValid, setChainValid] = useState(null);

  // -------------------------------------------------------------
  // Modals
  // -------------------------------------------------------------
  // Add User Modal
  const [showAddUserModal, setShowAddUserModal] = useState(false);
  const [newUser, setNewUser] = useState({
    name: '',
    email: '',
    password: '',
    role: 'FACULTY',
    department: 'Computer Science'
  });
  const [showNewUserPassword, setShowNewUserPassword] = useState(false);
  const [addUserLoading, setAddUserLoading] = useState(false);

  // Staff Password Reset Modal
  const [resetModal, setResetModal] = useState({
    open: false,
    user: null,
    password: '',
    showPassword: false,
    loading: false
  });

  const generateStrongPassword = () => {
    const upper = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
    const lower = 'abcdefghijkmnopqrstuvwxyz';
    const digits = '23456789';
    const special = '!@#$%&*+-_=';
    const all = upper + lower + digits + special;
    let pwd = upper[Math.floor(Math.random() * upper.length)] +
              lower[Math.floor(Math.random() * lower.length)] +
              digits[Math.floor(Math.random() * digits.length)] +
              special[Math.floor(Math.random() * special.length)];
    for (let i = 0; i < 8; i++) {
      pwd += all[Math.floor(Math.random() * all.length)];
    }
    return pwd.split('').sort(() => 0.5 - Math.random()).join('');
  };

  // CSV Import Modal
  const [csvModal, setCsvModal] = useState({
    open: false,
    rawText: '',
    fileName: '',
    parsedRows: [],
    errors: [],
    isSubmitting: false,
    resultSummary: null
  });

  // Notice Modal
  const [noticeModal, setNoticeModal] = useState({
    open: false,
    isEditing: false,
    id: null,
    title: '',
    body: '',
    targetRole: 'ALL',
    targetDepartment: 'ALL',
    targetDivision: 'ALL',
    priority: 'NORMAL',
    deadline: ''
  });

  // Issue Credential Modal
  const [credentialModal, setCredentialModal] = useState({
    open: false,
    studentId: '',
    studentName: '',
    rollNo: '',
    credentialType: 'PROVISIONAL_DEGREE',
    department: 'Computer Science',
    academicYear: '2025-2026',
    gradeOrCgpa: '8.5'
  });
  const [issuedResult, setIssuedResult] = useState(null);

  // Academic Modals
  const [deptModal, setDeptModal] = useState({ open: false, isEditing: false, id: null, code: '', name: '', hodName: '' });
  const [divisionModal, setDivisionModal] = useState({ open: false, isEditing: false, id: null, departmentId: '', name: '', academicYear: '2025-2026' });
  const [subjectModal, setSubjectModal] = useState({ open: false, isEditing: false, id: null, departmentId: '', code: '', name: '', credits: 4, semester: 1 });
  const [roomModal, setRoomModal] = useState({ open: false, isEditing: false, id: null, name: '', latitude: 18.52043, longitude: 73.85674, radiusM: 50, capacity: 60 });

  // -------------------------------------------------------------
  // Data Loaders
  // -------------------------------------------------------------
  const loadUsers = useCallback(async () => {
    setLoadingUsers(true);
    try {
      const data = await api.getUsers();
      setUsers(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Error fetching users:', err);
    } finally {
      setLoadingUsers(false);
    }
  }, []);

  const loadAcademicData = useCallback(async () => {
    setLoadingAcademic(true);
    try {
      const [depts, divs, subs, rms] = await Promise.all([
        api.getDepartments().catch(() => []),
        api.getDivisions().catch(() => []),
        api.getSubjects().catch(() => []),
        api.getRooms().catch(() => [])
      ]);
      setDepartments(Array.isArray(depts) ? depts : []);
      setDivisions(Array.isArray(divs) ? divs : []);
      setSubjects(Array.isArray(subs) ? subs : []);
      setRooms(Array.isArray(rms) ? rms : []);
    } catch (err) {
      console.error('Error loading academic data:', err);
    } finally {
      setLoadingAcademic(false);
    }
  }, []);

  const loadNotices = useCallback(async () => {
    setLoadingNotices(true);
    try {
      const data = await api.getNotices();
      setNotices(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Error loading notices:', err);
    } finally {
      setLoadingNotices(false);
    }
  }, []);

  const loadCredentials = useCallback(async () => {
    setLoadingCredentials(true);
    try {
      const data = await api.getCredentials();
      setCredentials(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Error loading credentials:', err);
    } finally {
      setLoadingCredentials(false);
    }
  }, []);


  const loadApprovals = useCallback(async () => {
    if (!token) return;
    setLoadingApprovals(true);
    try {
      const data = await getPendingApprovalsApi(token);
      setPendingApprovals(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Error loading approvals:', err);
    } finally {
      setLoadingApprovals(false);
    }
  }, [token]);

  const loadAudit = useCallback(async () => {
    setLoadingAudit(true);
    try {
      const [chain, verify] = await Promise.all([
        api.getAuditChain().catch(() => []),
        api.verifyAuditChain().catch(() => ({ valid: true }))
      ]);
      setAuditChain(Array.isArray(chain) ? chain : []);
      setChainValid(verify?.valid !== undefined ? verify.valid : true);
    } catch (err) {
      console.error('Error loading audit chain:', err);
    } finally {
      setLoadingAudit(false);
    }
  }, []);

  useEffect(() => {
    loadUsers();
    loadAcademicData();
    loadNotices();
    loadCredentials();
    loadApprovals();
    loadAudit();
  }, [loadUsers, loadAcademicData, loadNotices, loadCredentials, loadApprovals, loadAudit]);

  // Alert dismiss auto-timer
  useEffect(() => {
    if (apiSuccess) {
      const t = setTimeout(() => setApiSuccess(''), 4000);
      return () => clearTimeout(t);
    }
  }, [apiSuccess]);

  // -------------------------------------------------------------
  // User Management Actions
  // -------------------------------------------------------------
  const handleCreateUser = async (e) => {
    e.preventDefault();
    if (!newUser.name || !newUser.email || !newUser.password || !newUser.department) {
      setApiError('Please fill in all required fields (Full Name, Email, Password, Role, Department).');
      return;
    }
    if (newUser.role === 'STUDENT' || newUser.role === 'PRINCIPAL') {
      setApiError('Only HOD, Faculty, and Mentor accounts can be registered from this console.');
      return;
    }
    const p = newUser.password;
    if (p.length < 8 || !/[A-Z]/.test(p) || !/[a-z]/.test(p) || !/[0-9]/.test(p) || !/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(p)) {
      setApiError('Password must be at least 8 characters long and include uppercase, lowercase, number, and special character.');
      return;
    }

    setAddUserLoading(true);
    setApiError('');
    try {
      await api.createStaff({
        name: newUser.name.trim(),
        email: newUser.email.trim(),
        password: newUser.password,
        role: newUser.role,
        department: newUser.department
      });
      setApiSuccess(`Staff account for ${newUser.name} created successfully.`);
      setShowAddUserModal(false);
      setNewUser({
        name: '',
        email: '',
        password: '',
        role: 'FACULTY',
        department: departments[0]?.name || 'Computer Science'
      });
      loadUsers();
    } catch (err) {
      setApiError(err.message || 'Failed to create staff account.');
    } finally {
      setAddUserLoading(false);
    }
  };

  const handleToggleUserStatus = async (user) => {
    if (user.role === 'PRINCIPAL') {
      alert('The Principal account status cannot be modified.');
      return;
    }
    if (user.role === 'STUDENT') {
      alert('Principals cannot modify student status.');
      return;
    }
    const nextStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      await api.updateUserStatus(user.id, nextStatus);
      setApiSuccess(`Status for ${user.name} updated to ${nextStatus}.`);
      loadUsers();
    } catch (err) {
      setApiError(err.message || 'Failed to update user status.');
    }
  };

  const handleOpenResetPassword = (user) => {
    if (user.role === 'STUDENT') {
      setApiError('Principals cannot modify student passwords.');
      return;
    }
    if (user.role === 'PRINCIPAL') {
      setApiError('Cannot reset Principal password from this panel.');
      return;
    }
    setResetModal({
      open: true,
      user,
      password: generateStrongPassword(),
      showPassword: false,
      loading: false
    });
  };

  const handleConfirmResetPassword = async (e) => {
    e.preventDefault();
    if (!resetModal.user) return;
    const p = resetModal.password;
    if (p && (p.length < 8 || !/[A-Z]/.test(p) || !/[a-z]/.test(p) || !/[0-9]/.test(p) || !/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(p))) {
      setApiError('Password must be at least 8 characters long and contain uppercase, lowercase, number, and special character.');
      return;
    }
    setResetModal(prev => ({ ...prev, loading: true }));
    try {
      await api.resetUserPassword(resetModal.user.id, resetModal.password);
      setApiSuccess(`Password for ${resetModal.user.name} has been updated successfully.`);
      setResetModal({ open: false, user: null, password: '', showPassword: false, loading: false });
    } catch (err) {
      setApiError(err.message || 'Failed to reset staff password.');
      setResetModal(prev => ({ ...prev, loading: false }));
    }
  };

  const handleDeleteUser = async (user) => {
    if (user.role === 'PRINCIPAL') {
      alert('The Principal account cannot be deleted.');
      return;
    }
    if (user.role === 'STUDENT') {
      alert('Principals cannot delete student accounts.');
      return;
    }
    if (!window.confirm(`Are you sure you want to delete staff account ${user.name} (${user.userCode})? This action is permanent.`)) return;
    try {
      await api.deleteUser(user.id);
      setApiSuccess(`Staff user ${user.name} deleted.`);
      loadUsers();
    } catch (err) {
      setApiError(err.message || 'Failed to delete user.');
    }
  };

  // CSV Template Generator
  const downloadCsvTemplate = () => {
    const csvContent = "data:text/csv;charset=utf-8," + 
      "full_name,email,password,role,department\n" +
      "Dr. Alan Turing,turing@campus.edu,Turing#2026Pass,HOD,Computer Science\n" +
      "Prof. Ada Lovelace,lovelace@campus.edu,Ada#Lovelace2026,FACULTY,Computer Science\n" +
      "Dr. Claude Shannon,shannon@campus.edu,Shannon#2026Code,MENTOR,Information Technology\n";
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", "intellicampus_staff_template.csv");
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // CSV Parser & Submitter
  const handleCsvFileChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      const text = event.target.result;
      const lines = text.split(/\r?\n/).filter(line => line.trim().length > 0);
      const errors = [];
      const parsedRows = [];
      const seenEmails = new Set();

      lines.forEach((line, idx) => {
        if (idx === 0) return; // skip header
        const parts = line.split(',').map(s => s.trim());
        if (parts.length < 5) {
          errors.push(`Row ${idx + 1}: Incomplete columns (expected full_name, email, password, role, department)`);
          return;
        }

        // Support both new staff format (full_name, email, password, role, department)
        // and legacy format (userCode, name, email, role, department, ...)
        let fullName, email, password, role, dept;
        if (parts[2].includes('@')) {
          // legacy format
          fullName = parts[1];
          email = parts[2];
          password = '';
          role = parts[3]?.toUpperCase();
          dept = parts[4];
        } else {
          // new staff format
          fullName = parts[0];
          email = parts[1];
          password = parts[2];
          role = parts[3]?.toUpperCase();
          dept = parts[4];
        }

        const emailLower = email.toLowerCase();
        if (seenEmails.has(emailLower)) {
          errors.push(`Row ${idx + 1} (${email}): Duplicate email in CSV file.`);
          return;
        }
        seenEmails.add(emailLower);

        if (role === 'STUDENT') {
          errors.push(`Row ${idx + 1} (${fullName}): Students register themselves on the Student portal.`);
          return;
        }
        if (role === 'PRINCIPAL') {
          errors.push(`Row ${idx + 1} (${fullName}): Principal cannot be imported.`);
          return;
        }
        if (role !== 'HOD' && role !== 'FACULTY' && role !== 'MENTOR') {
          errors.push(`Row ${idx + 1} (${fullName}): Invalid role "${role}". Only HOD, FACULTY, and MENTOR are allowed.`);
          return;
        }

        if (password) {
          if (password.length < 8 || !/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password) || !/[^a-zA-Z0-9]/.test(password)) {
            errors.push(`Row ${idx + 1} (${fullName}): Weak password. Must have 8+ chars with uppercase, lowercase, digit, and special char.`);
            return;
          }
        }

        parsedRows.push({ name: fullName, email, role, department: dept });
      });

      setCsvModal(prev => ({
        ...prev,
        rawText: text,
        fileName: file.name,
        parsedRows,
        errors,
        resultSummary: null
      }));
    };
    reader.readAsText(file);
  };

  const handleExecuteCsvImport = async () => {
    if (!csvModal.rawText) return;
    const lines = csvModal.rawText.split(/\r?\n/).filter(line => line.trim().length > 0);
    setCsvModal(prev => ({ ...prev, isSubmitting: true }));
    try {
      const result = await api.importUsersCsv(lines);
      setCsvModal(prev => ({ ...prev, resultSummary: result, isSubmitting: false }));
      setApiSuccess(`CSV Import completed: ${result.importedCount} users imported successfully.`);
      loadUsers();
    } catch (err) {
      setApiError(err.message || 'CSV Import failed.');
      setCsvModal(prev => ({ ...prev, isSubmitting: false }));
    }
  };

  // -------------------------------------------------------------
  // Targeted Notices Actions
  // -------------------------------------------------------------
  const handleSaveNotice = async (e) => {
    e.preventDefault();
    if (!noticeModal.title || !noticeModal.body) return;
    try {
      if (noticeModal.isEditing) {
        await api.updateNotice(noticeModal.id, {
          title: noticeModal.title,
          body: noticeModal.body,
          targetRole: noticeModal.targetRole,
          targetDepartment: noticeModal.targetDepartment,
          targetDivision: noticeModal.targetDivision,
          priority: noticeModal.priority,
          deadline: noticeModal.deadline || null
        });
        setApiSuccess('Notice updated successfully.');
      } else {
        await api.createNotice({
          title: noticeModal.title,
          body: noticeModal.body,
          targetRole: noticeModal.targetRole,
          targetDepartment: noticeModal.targetDepartment,
          targetDivision: noticeModal.targetDivision,
          priority: noticeModal.priority,
          deadline: noticeModal.deadline || null
        });
        setApiSuccess('Notice published successfully.');
      }
      setNoticeModal({ open: false, isEditing: false, id: null, title: '', body: '', targetRole: 'ALL', targetDepartment: 'ALL', targetDivision: 'ALL', priority: 'NORMAL', deadline: '' });
      loadNotices();
    } catch (err) {
      setApiError(err.message || 'Failed to save notice.');
    }
  };

  const handleDeleteNotice = async (id) => {
    if (!window.confirm('Are you sure you want to delete this notice?')) return;
    try {
      await api.deleteNotice(id);
      setApiSuccess('Notice removed.');
      loadNotices();
    } catch (err) {
      setApiError(err.message || 'Failed to delete notice.');
    }
  };

  // -------------------------------------------------------------
  // Verifiable Credentials Actions
  // -------------------------------------------------------------
  const handleIssueCredential = async (e) => {
    e.preventDefault();
    if (!credentialModal.studentName || !credentialModal.rollNo) return;
    try {
      const res = await api.issueCredential({
        studentId: credentialModal.studentId ? parseInt(credentialModal.studentId, 10) : null,
        studentName: credentialModal.studentName,
        rollNo: credentialModal.rollNo,
        type: credentialModal.credentialType,
        credentialType: credentialModal.credentialType,
        department: credentialModal.department,
        academicYear: credentialModal.academicYear,
        gpa: credentialModal.gradeOrCgpa,
        gradeOrCgpa: credentialModal.gradeOrCgpa
      });
      setIssuedResult(res);
      setApiSuccess(`Credential issued! Verification Code: ${res.verificationCode}`);
      setCredentialModal({
        open: false,
        studentId: '',
        studentName: '',
        rollNo: '',
        credentialType: 'PROVISIONAL_DEGREE',
        department: 'Computer Science',
        academicYear: '2025-2026',
        gradeOrCgpa: '8.5'
      });
      loadCredentials();
    } catch (err) {
      setApiError(err.message || 'Failed to issue credential.');
    }
  };

  const handleRevokeCredential = async (code) => {
    const reason = window.prompt('Enter reason for credential revocation:');
    if (!reason) return;
    try {
      await api.revokeCredential(code, reason);
      setApiSuccess(`Credential ${code} revoked.`);
      loadCredentials();
    } catch (err) {
      setApiError(err.message || 'Failed to revoke credential.');
    }
  };


  // -------------------------------------------------------------
  // Staff Approvals Actions
  // -------------------------------------------------------------
  const handleApproveStaff = async (staffId, name) => {
    try {
      await approveStaffApi(staffId, token);
      setApiSuccess(`Staff account for ${name} approved.`);
      loadApprovals();
    } catch (err) {
      setApiError(err.message || 'Failed to approve staff.');
    }
  };

  const handleRejectStaff = async (staffId, name) => {
    const reason = window.prompt(`Provide reason for rejecting registration of ${name}:`, 'Incomplete institutional documentation');
    if (reason === null) return;
    try {
      await rejectStaffApi(staffId, reason, token);
      setApiSuccess(`Staff account for ${name} rejected.`);
      loadApprovals();
    } catch (err) {
      setApiError(err.message || 'Failed to reject staff.');
    }
  };

  // -------------------------------------------------------------
  // Academic Setup Actions
  // -------------------------------------------------------------
  const handleSaveDepartment = async (e) => {
    e.preventDefault();
    try {
      if (deptModal.isEditing) {
        await api.updateDepartment(deptModal.id, { code: deptModal.code, name: deptModal.name, hodName: deptModal.hodName });
        setApiSuccess('Department updated.');
      } else {
        await api.createDepartment({ code: deptModal.code, name: deptModal.name, hodName: deptModal.hodName });
        setApiSuccess('Department created.');
      }
      setDeptModal({ open: false, isEditing: false, id: null, code: '', name: '', hodName: '' });
      loadAcademicData();
    } catch (err) {
      setApiError(err.message || 'Failed to save department.');
    }
  };

  const handleDeleteDepartment = async (id) => {
    if (!window.confirm('Delete this department?')) return;
    try {
      await api.deleteDepartment(id);
      setApiSuccess('Department deleted.');
      loadAcademicData();
    } catch (err) {
      setApiError(err.message || 'Failed to delete department.');
    }
  };

  const handleSaveDivision = async (e) => {
    e.preventDefault();
    try {
      if (divisionModal.isEditing) {
        await api.updateDivision(divisionModal.id, {
          departmentId: divisionModal.departmentId ? parseInt(divisionModal.departmentId, 10) : null,
          name: divisionModal.name,
          academicYear: divisionModal.academicYear
        });
        setApiSuccess('Division updated.');
      } else {
        await api.createDivision({
          departmentId: divisionModal.departmentId ? parseInt(divisionModal.departmentId, 10) : null,
          name: divisionModal.name,
          academicYear: divisionModal.academicYear
        });
        setApiSuccess('Division created.');
      }
      setDivisionModal({ open: false, isEditing: false, id: null, departmentId: '', name: '', academicYear: '2025-2026' });
      loadAcademicData();
    } catch (err) {
      setApiError(err.message || 'Failed to save division.');
    }
  };

  const handleDeleteDivision = async (id) => {
    if (!window.confirm('Delete this division?')) return;
    try {
      await api.deleteDivision(id);
      setApiSuccess('Division deleted.');
      loadAcademicData();
    } catch (err) {
      setApiError(err.message || 'Failed to delete division.');
    }
  };

  const handleSaveSubject = async (e) => {
    e.preventDefault();
    try {
      const payload = {
        departmentId: subjectModal.departmentId ? parseInt(subjectModal.departmentId, 10) : null,
        code: subjectModal.code,
        name: subjectModal.name,
        credits: parseInt(subjectModal.credits, 10),
        semester: parseInt(subjectModal.semester, 10)
      };
      if (subjectModal.isEditing) {
        await api.updateSubject(subjectModal.id, payload);
        setApiSuccess('Subject updated.');
      } else {
        await api.createSubject(payload);
        setApiSuccess('Subject created.');
      }
      setSubjectModal({ open: false, isEditing: false, id: null, departmentId: '', code: '', name: '', credits: 4, semester: 1 });
      loadAcademicData();
    } catch (err) {
      setApiError(err.message || 'Failed to save subject.');
    }
  };

  const handleDeleteSubject = async (id) => {
    if (!window.confirm('Delete this subject?')) return;
    try {
      await api.deleteSubject(id);
      setApiSuccess('Subject deleted.');
      loadAcademicData();
    } catch (err) {
      setApiError(err.message || 'Failed to delete subject.');
    }
  };

  const handleSaveRoom = async (e) => {
    e.preventDefault();
    try {
      const payload = {
        name: roomModal.name,
        latitude: parseFloat(roomModal.latitude),
        longitude: parseFloat(roomModal.longitude),
        radiusM: parseInt(roomModal.radiusM, 10),
        capacity: parseInt(roomModal.capacity, 10)
      };
      if (roomModal.isEditing) {
        await api.updateRoom(roomModal.id, payload);
        setApiSuccess('Geofenced room updated.');
      } else {
        await api.createRoom(payload);
        setApiSuccess('Geofenced room created.');
      }
      setRoomModal({ open: false, isEditing: false, id: null, name: '', latitude: 18.52043, longitude: 73.85674, radiusM: 50, capacity: 60 });
      loadAcademicData();
    } catch (err) {
      setApiError(err.message || 'Failed to save room.');
    }
  };

  const handleDeleteRoom = async (id) => {
    if (!window.confirm('Delete this classroom?')) return;
    try {
      await api.deleteRoom(id);
      setApiSuccess('Room deleted.');
      loadAcademicData();
    } catch (err) {
      setApiError(err.message || 'Failed to delete room.');
    }
  };

  // Filtered users for table
  const filteredUsers = users.filter(u => {
    const q = userSearch.toLowerCase();
    const matchSearch = !userSearch || 
      (u.name && u.name.toLowerCase().includes(q)) ||
      (u.userCode && u.userCode.toLowerCase().includes(q)) ||
      (u.email && u.email.toLowerCase().includes(q)) ||
      (u.rollNo && u.rollNo.toLowerCase().includes(q));
    const matchRole = roleFilter === 'ALL' || u.role === roleFilter;
    const matchDept = deptFilter === 'ALL' || u.department === deptFilter;
    return matchSearch && matchRole && matchDept;
  });

  return (
    <div className="admin-container animate-fade-in" style={{ padding: '1.5rem', maxWidth: '1440px', margin: '0 auto' }}>
      {/* Principal Console Header */}
      <div className="section-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 className="page-title" style={{ fontSize: '1.75rem', fontWeight: 700, color: 'var(--text-primary)', margin: 0, display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <ShieldCheck size={28} color="#6366f1" /> Principal Operations Console
          </h1>
          <p className="page-desc" style={{ color: 'var(--text-secondary)', fontSize: '0.88rem', margin: '0.35rem 0 0' }}>
            Institutional governance for user provisioning, academic hierarchy, verifiable records, and AI knowledge.
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <div className="header-meta-badge" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', background: 'rgba(99, 102, 241, 0.1)', border: '1px solid rgba(99, 102, 241, 0.3)', padding: '5px 12px', borderRadius: '20px', fontSize: '0.82rem', color: '#a5b4fc' }}>
            <span className="dot online" style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#10b981' }} />
            Connected: {staffSession?.user?.name || 'College Principal'} (Principal)
          </div>

          <button
            type="button"
            id="principal-header-ask-ai-btn"
            onClick={() => onOpenChatbot ? onOpenChatbot() : setIsAssistantOpen(true)}
            className="action-icon-btn"
            style={{ padding: '6px 14px', fontSize: '0.82rem', display: 'flex', alignItems: 'center', gap: '6px', borderRadius: '8px', background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.25) 0%, rgba(168, 85, 247, 0.25) 100%)', border: '1px solid rgba(99, 102, 241, 0.4)', color: '#c7d2fe', cursor: 'pointer', fontWeight: 600 }}
          >
            <Sparkles size={15} color="#818cf8" />
            <span>Ask AI</span>
          </button>

          <button
            type="button"
            onClick={() => setShowChangePasswordModal(true)}
            className="action-icon-btn"
            style={{ padding: '6px 14px', fontSize: '0.82rem', display: 'flex', alignItems: 'center', gap: '6px', borderRadius: '8px', background: 'var(--bg-surface)', border: '1px solid var(--border-medium)', color: 'var(--text-primary)', cursor: 'pointer' }}
          >
            <KeyRound size={15} />
            <span>Change Password</span>
          </button>

          {onLogout && (
            <button
              type="button"
              onClick={onLogout}
              className="action-icon-btn"
              style={{ padding: '6px 14px', fontSize: '0.82rem', color: '#ef4444', border: '1px solid rgba(239, 68, 68, 0.4)', borderRadius: '8px', background: 'rgba(239, 68, 68, 0.08)', display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer' }}
            >
              <LogOut size={15} />
              <span>Logout</span>
            </button>
          )}
        </div>
      </div>

      {/* Global Alert Toasts */}
      {apiSuccess && (
        <div className="staff-alert staff-alert-success animate-fade-in" style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', padding: '0.75rem 1rem', background: 'rgba(16, 185, 129, 0.15)', border: '1px solid rgba(16, 185, 129, 0.4)', color: '#34d399', borderRadius: '8px', marginBottom: '1.25rem', fontSize: '0.88rem' }}>
          <CheckCircle2 size={18} />
          <span>{apiSuccess}</span>
        </div>
      )}
      {apiError && (
        <div className="staff-alert staff-alert-error animate-fade-in" style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', padding: '0.75rem 1rem', background: 'rgba(239, 68, 68, 0.15)', border: '1px solid rgba(239, 68, 68, 0.4)', color: '#f87171', borderRadius: '8px', marginBottom: '1.25rem', fontSize: '0.88rem' }}>
          <AlertCircle size={18} />
          <span>{apiError}</span>
        </div>
      )}

      {/* Dynamic Tab Navigation with REAL DB-derived counts */}
      <div className="sub-nav-tabs" style={{ display: 'flex', gap: '0.5rem', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '0.5rem', marginBottom: '1.5rem', overflowX: 'auto' }}>
        <button 
          onClick={() => setActiveTab('users')} 
          className={`tab-btn ${activeTab === 'users' ? 'active' : ''}`}
        >
          <Users size={16} /> User Management ({users.length})
        </button>
        <button 
          onClick={() => setActiveTab('academic')} 
          className={`tab-btn ${activeTab === 'academic' ? 'active' : ''}`}
        >
          <Layers size={16} /> Academic Setup & Rooms ({departments.length + rooms.length})
        </button>
        <button 
          onClick={() => setActiveTab('notices')} 
          className={`tab-btn ${activeTab === 'notices' ? 'active' : ''}`}
        >
          <Bell size={16} /> Targeted Notices ({notices.length})
        </button>
        <button 
          onClick={() => setActiveTab('documents')} 
          className={`tab-btn ${activeTab === 'documents' ? 'active' : ''}`}
        >
          <FileCheck2 size={16} /> Verifiable Documents ({credentials.length})
        </button>
        <button 
          onClick={() => setActiveTab('approvals')} 
          className={`tab-btn ${activeTab === 'approvals' ? 'active' : ''}`}
        >
          <Clock size={16} /> Staff Approvals ({pendingApprovals.length})
        </button>
        <button 
          onClick={() => setActiveTab('audit')} 
          className={`tab-btn ${activeTab === 'audit' ? 'active' : ''}`}
        >
          <ShieldCheck size={16} /> Audit Chain ({auditChain.length})
        </button>
      </div>

      {/* =========================================================================
          TAB 1: USER MANAGEMENT
          ========================================================================= */}
      {activeTab === 'users' && (
        <div className="tab-pane animate-fade-in">
          <div className="pane-toolbar" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '12px' }}>
            <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flex: 1, minWidth: '300px' }}>
              <div className="search-box" style={{ display: 'flex', alignItems: 'center', gap: '8px', background: 'var(--bg-surface)', padding: '8px 12px', borderRadius: '8px', border: '1px solid var(--border-medium)', flex: 1 }}>
                <Search size={16} color="var(--text-secondary)" />
                <input 
                  type="text" 
                  placeholder="Search by name, roll no, or code..." 
                  value={userSearch}
                  onChange={e => setUserSearch(e.target.value)}
                  style={{ background: 'transparent', border: 'none', color: 'inherit', outline: 'none', width: '100%', fontSize: '0.88rem' }}
                />
              </div>

              <select 
                value={roleFilter} 
                onChange={e => setRoleFilter(e.target.value)}
                style={{ padding: '8px 12px', borderRadius: '8px', background: 'var(--bg-surface)', border: '1px solid var(--border-medium)', color: 'inherit', fontSize: '0.85rem' }}
              >
                <option value="ALL">All Roles</option>
                <option value="STUDENT">Students</option>
                <option value="FACULTY">Faculty</option>
                <option value="HOD">HOD</option>
                <option value="MENTOR">Mentors</option>
                <option value="PRINCIPAL">Principal</option>
              </select>

              <select 
                value={deptFilter} 
                onChange={e => setDeptFilter(e.target.value)}
                style={{ padding: '8px 12px', borderRadius: '8px', background: 'var(--bg-surface)', border: '1px solid var(--border-medium)', color: 'inherit', fontSize: '0.85rem' }}
              >
                <option value="ALL">All Departments</option>
                {departments.map(d => (
                  <option key={d.id} value={d.name}>{d.name}</option>
                ))}
              </select>
            </div>

            <div style={{ display: 'flex', gap: '8px' }}>
              <button 
                onClick={loadUsers} 
                className="btn-secondary"
                title="Refresh user list from database"
                style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 14px', borderRadius: '8px', cursor: 'pointer', background: 'var(--bg-surface)', border: '1px solid var(--border-medium)', color: 'inherit' }}
              >
                <RefreshCw size={15} className={loadingUsers ? 'animate-spin' : ''} />
                <span>Refresh</span>
              </button>
              <button 
                onClick={() => setCsvModal(prev => ({ ...prev, open: true, rawText: '', parsedRows: [], errors: [], resultSummary: null }))} 
                className="btn-secondary"
                style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 14px', borderRadius: '8px', cursor: 'pointer', background: 'var(--bg-surface)', border: '1px solid var(--border-medium)', color: 'inherit' }}
              >
                <FileSpreadsheet size={15} />
                <span>Bulk CSV Import</span>
              </button>
              <button 
                onClick={() => setShowAddUserModal(true)} 
                className="btn-primary"
                style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 16px', borderRadius: '8px', cursor: 'pointer', background: '#6366f1', color: '#fff', border: 'none', fontWeight: 600 }}
              >
                <Plus size={16} />
                <span>Add User</span>
              </button>
            </div>
          </div>

          {/* User Table with DB Values */}
          <div className="table-responsive" style={{ background: 'var(--bg-surface)', borderRadius: '12px', border: '1px solid var(--border-subtle)', overflow: 'hidden' }}>
            <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-subtle)', background: 'rgba(255, 255, 255, 0.02)', color: 'var(--text-secondary)' }}>
                  <th style={{ padding: '12px 16px' }}>User Code / ID</th>
                  <th style={{ padding: '12px 16px' }}>Full Name & Email</th>
                  <th style={{ padding: '12px 16px' }}>Role</th>
                  <th style={{ padding: '12px 16px' }}>Department</th>
                  <th style={{ padding: '12px 16px' }}>Roll / Class</th>
                  <th style={{ padding: '12px 16px' }}>Biometric Face Consent</th>
                  <th style={{ padding: '12px 16px' }}>Attendance</th>
                  <th style={{ padding: '12px 16px' }}>Status</th>
                  <th style={{ padding: '12px 16px', textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {loadingUsers ? (
                  <tr>
                    <td colSpan="9" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-secondary)' }}>
                      <RefreshCw size={24} className="animate-spin" style={{ margin: '0 auto 8px' }} />
                      <p>Loading database records...</p>
                    </td>
                  </tr>
                ) : filteredUsers.length === 0 ? (
                  <tr>
                    <td colSpan="9" style={{ textAlign: 'center', padding: '3.5rem', color: 'var(--text-secondary)' }}>
                      <Users size={36} style={{ margin: '0 auto 12px', opacity: 0.4 }} />
                      <p style={{ fontWeight: 600, fontSize: '1rem', color: 'var(--text-primary)' }}>No users found</p>
                      <p style={{ fontSize: '0.82rem' }}>No user records match the selected filter criteria in the database.</p>
                    </td>
                  </tr>
                ) : (
                  filteredUsers.map(u => (
                    <tr key={u.id} style={{ borderBottom: '1px solid var(--border-subtle)', transition: 'background 0.2s' }}>
                      <td style={{ padding: '12px 16px', fontWeight: 600, color: 'var(--text-primary)' }}>
                        {u.userCode || `USR-${u.id}`}
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{u.name}</div>
                        <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>{u.email}</div>
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <span className={`badge-role badge-${u.role?.toLowerCase()}`} style={{ padding: '3px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600, background: u.role === 'PRINCIPAL' ? '#6366f120' : 'rgba(255,255,255,0.08)', color: u.role === 'PRINCIPAL' ? '#818cf8' : 'inherit', border: u.role === 'PRINCIPAL' ? '1px solid #6366f140' : 'none' }}>
                          {u.role}
                        </span>
                      </td>
                      <td style={{ padding: '12px 16px', color: 'var(--text-secondary)' }}>
                        {u.department || 'General'}
                      </td>
                      <td style={{ padding: '12px 16px', color: 'var(--text-secondary)' }}>
                        {u.role === 'STUDENT' ? (u.rollNo ? `${u.rollNo} (${u.division || 'Div A'})` : 'N/A') : '—'}
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        {u.role === 'STUDENT' ? (
                          u.faceEnrolled ? (
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', color: '#10b981', fontSize: '0.78rem', fontWeight: 600 }}>
                              <CheckCircle2 size={14} /> Enrolled
                            </span>
                          ) : (
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', color: '#f59e0b', fontSize: '0.78rem' }}>
                              <Clock size={14} /> Not Enrolled
                            </span>
                          )
                        ) : (
                          <span style={{ color: 'var(--text-secondary)' }}>N/A</span>
                        )}
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        {u.role === 'STUDENT' ? (
                          u.attendanceRate != null ? `${u.attendanceRate}%` : 'N/A'
                        ) : (
                          <span style={{ color: 'var(--text-secondary)' }}>N/A</span>
                        )}
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <span style={{
                          padding: '3px 8px',
                          borderRadius: '4px',
                          fontSize: '0.75rem',
                          fontWeight: 600,
                          background: u.status === 'ACTIVE' ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
                          color: u.status === 'ACTIVE' ? '#34d399' : '#f87171'
                        }}>
                          {u.status || 'ACTIVE'}
                        </span>
                      </td>
                      <td style={{ padding: '12px 16px', textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', gap: '6px' }}>
                          {u.role === 'STUDENT' && (
                            <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', fontStyle: 'italic', padding: '3px 8px', borderRadius: '4px', background: 'rgba(255,255,255,0.03)', border: '1px solid var(--border-subtle)' }}>
                              Student Self-Managed
                            </span>
                          )}
                          {u.role !== 'PRINCIPAL' && u.role !== 'STUDENT' && (
                            <>
                              <button
                                onClick={() => handleToggleUserStatus(u)}
                                className="action-btn"
                                title={u.status === 'ACTIVE' ? 'Deactivate Account' : 'Activate Account'}
                                style={{ padding: '4px 8px', background: 'transparent', border: '1px solid var(--border-medium)', borderRadius: '4px', color: u.status === 'ACTIVE' ? '#f87171' : '#34d399', cursor: 'pointer', fontSize: '0.75rem' }}
                              >
                                {u.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                              </button>
                              <button
                                onClick={() => handleOpenResetPassword(u)}
                                className="action-btn"
                                title="Reset Staff Password"
                                style={{ padding: '4px 8px', background: 'transparent', border: '1px solid var(--border-medium)', borderRadius: '4px', color: 'var(--text-primary)', cursor: 'pointer', fontSize: '0.75rem' }}
                              >
                                Reset Pass
                              </button>
                              <button
                                onClick={() => handleDeleteUser(u)}
                                className="action-btn"
                                title="Delete Staff Account"
                                style={{ padding: '4px 6px', background: 'transparent', border: '1px solid rgba(239, 68, 68, 0.3)', borderRadius: '4px', color: '#ef4444', cursor: 'pointer' }}
                              >
                                <Trash2 size={13} />
                              </button>
                            </>
                          )}
                          {u.role === 'PRINCIPAL' && (
                            <span style={{ fontSize: '0.75rem', color: '#818cf8', fontStyle: 'italic' }}>Protected Account</span>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* =========================================================================
          TAB 2: ACADEMIC SETUP & ROOMS
          ========================================================================= */}
      {activeTab === 'academic' && (
        <div className="tab-pane animate-fade-in">
          {/* Sub Navigation */}
          <div style={{ display: 'flex', gap: '8px', marginBottom: '1.25rem', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '0.5rem' }}>
            <button 
              onClick={() => setAcademicSubTab('departments')} 
              className={`btn-subtab ${academicSubTab === 'departments' ? 'active' : ''}`}
              style={{ padding: '6px 14px', borderRadius: '6px', background: academicSubTab === 'departments' ? '#6366f1' : 'var(--bg-surface)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '0.85rem' }}
            >
              Departments ({departments.length})
            </button>
            <button 
              onClick={() => setAcademicSubTab('divisions')} 
              className={`btn-subtab ${academicSubTab === 'divisions' ? 'active' : ''}`}
              style={{ padding: '6px 14px', borderRadius: '6px', background: academicSubTab === 'divisions' ? '#6366f1' : 'var(--bg-surface)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '0.85rem' }}
            >
              Classes & Divisions ({divisions.length})
            </button>
            <button 
              onClick={() => setAcademicSubTab('subjects')} 
              className={`btn-subtab ${academicSubTab === 'subjects' ? 'active' : ''}`}
              style={{ padding: '6px 14px', borderRadius: '6px', background: academicSubTab === 'subjects' ? '#6366f1' : 'var(--bg-surface)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '0.85rem' }}
            >
              Subjects ({subjects.length})
            </button>
            <button 
              onClick={() => setAcademicSubTab('rooms')} 
              className={`btn-subtab ${academicSubTab === 'rooms' ? 'active' : ''}`}
              style={{ padding: '6px 14px', borderRadius: '6px', background: academicSubTab === 'rooms' ? '#6366f1' : 'var(--bg-surface)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '0.85rem' }}
            >
              Rooms & Geofences ({rooms.length})
            </button>
          </div>

          {/* Sub-tab 1: Departments */}
          {academicSubTab === 'departments' && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem', alignItems: 'center' }}>
                <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: '0.88rem' }}>Institutional departments and assigned heads of department.</p>
                <button 
                  onClick={() => setDeptModal({ open: true, isEditing: false, id: null, code: '', name: '', hodName: '' })}
                  className="btn-primary"
                  style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '6px 14px', borderRadius: '6px', background: '#6366f1', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '0.85rem' }}
                >
                  <Plus size={15} /> Add Department
                </button>
              </div>
              <div className="table-responsive" style={{ background: 'var(--bg-surface)', borderRadius: '10px', border: '1px solid var(--border-subtle)', overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.02)' }}>
                      <th style={{ padding: '10px 14px' }}>Code</th>
                      <th style={{ padding: '10px 14px' }}>Department Name</th>
                      <th style={{ padding: '10px 14px' }}>Assigned HOD</th>
                      <th style={{ padding: '10px 14px', textAlign: 'right' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {departments.length === 0 ? (
                      <tr><td colSpan="4" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-secondary)' }}>No departments defined in database yet.</td></tr>
                    ) : (
                      departments.map(d => (
                        <tr key={d.id} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                          <td style={{ padding: '10px 14px', fontWeight: 600 }}>{d.code}</td>
                          <td style={{ padding: '10px 14px' }}>{d.name}</td>
                          <td style={{ padding: '10px 14px', color: 'var(--text-secondary)' }}>{d.hodName || 'Unassigned'}</td>
                          <td style={{ padding: '10px 14px', textAlign: 'right' }}>
                            <button onClick={() => setDeptModal({ open: true, isEditing: true, id: d.id, code: d.code, name: d.name, hodName: d.hodName || '' })} style={{ background: 'transparent', border: 'none', color: '#a5b4fc', cursor: 'pointer', marginRight: '8px' }}><Edit3 size={15} /></button>
                            <button onClick={() => handleDeleteDepartment(d.id)} style={{ background: 'transparent', border: 'none', color: '#ef4444', cursor: 'pointer' }}><Trash2 size={15} /></button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Sub-tab 2: Divisions */}
          {academicSubTab === 'divisions' && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem', alignItems: 'center' }}>
                <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: '0.88rem' }}>Academic divisions and cohorts mapped to departments.</p>
                <button 
                  onClick={() => setDivisionModal({ open: true, isEditing: false, id: null, departmentId: departments[0]?.id || '', name: '', academicYear: '2025-2026' })}
                  className="btn-primary"
                  style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '6px 14px', borderRadius: '6px', background: '#6366f1', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '0.85rem' }}
                >
                  <Plus size={15} /> Add Division
                </button>
              </div>
              <div className="table-responsive" style={{ background: 'var(--bg-surface)', borderRadius: '10px', border: '1px solid var(--border-subtle)', overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.02)' }}>
                      <th style={{ padding: '10px 14px' }}>Division Name</th>
                      <th style={{ padding: '10px 14px' }}>Department</th>
                      <th style={{ padding: '10px 14px' }}>Academic Year</th>
                      <th style={{ padding: '10px 14px', textAlign: 'right' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {divisions.length === 0 ? (
                      <tr><td colSpan="4" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-secondary)' }}>No divisions created yet.</td></tr>
                    ) : (
                      divisions.map(div => (
                        <tr key={div.id} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                          <td style={{ padding: '10px 14px', fontWeight: 600 }}>{div.name}</td>
                          <td style={{ padding: '10px 14px' }}>{div.departmentName || 'General'}</td>
                          <td style={{ padding: '10px 14px', color: 'var(--text-secondary)' }}>{div.academicYear}</td>
                          <td style={{ padding: '10px 14px', textAlign: 'right' }}>
                            <button onClick={() => setDivisionModal({ open: true, isEditing: true, id: div.id, departmentId: div.departmentId || '', name: div.name, academicYear: div.academicYear })} style={{ background: 'transparent', border: 'none', color: '#a5b4fc', cursor: 'pointer', marginRight: '8px' }}><Edit3 size={15} /></button>
                            <button onClick={() => handleDeleteDivision(div.id)} style={{ background: 'transparent', border: 'none', color: '#ef4444', cursor: 'pointer' }}><Trash2 size={15} /></button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Sub-tab 3: Subjects */}
          {academicSubTab === 'subjects' && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem', alignItems: 'center' }}>
                <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: '0.88rem' }}>Course curriculum and academic credit structure.</p>
                <button 
                  onClick={() => setSubjectModal({ open: true, isEditing: false, id: null, departmentId: departments[0]?.id || '', code: '', name: '', credits: 4, semester: 1 })}
                  className="btn-primary"
                  style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '6px 14px', borderRadius: '6px', background: '#6366f1', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '0.85rem' }}
                >
                  <Plus size={15} /> Add Subject
                </button>
              </div>
              <div className="table-responsive" style={{ background: 'var(--bg-surface)', borderRadius: '10px', border: '1px solid var(--border-subtle)', overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.02)' }}>
                      <th style={{ padding: '10px 14px' }}>Course Code</th>
                      <th style={{ padding: '10px 14px' }}>Subject Title</th>
                      <th style={{ padding: '10px 14px' }}>Department</th>
                      <th style={{ padding: '10px 14px' }}>Credits</th>
                      <th style={{ padding: '10px 14px' }}>Semester</th>
                      <th style={{ padding: '10px 14px', textAlign: 'right' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {subjects.length === 0 ? (
                      <tr><td colSpan="6" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-secondary)' }}>No subjects registered in catalog.</td></tr>
                    ) : (
                      subjects.map(s => (
                        <tr key={s.id} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                          <td style={{ padding: '10px 14px', fontWeight: 600 }}>{s.code}</td>
                          <td style={{ padding: '10px 14px' }}>{s.name}</td>
                          <td style={{ padding: '10px 14px', color: 'var(--text-secondary)' }}>{s.departmentName || 'General'}</td>
                          <td style={{ padding: '10px 14px' }}>{s.credits} Credits</td>
                          <td style={{ padding: '10px 14px' }}>Sem {s.semester}</td>
                          <td style={{ padding: '10px 14px', textAlign: 'right' }}>
                            <button onClick={() => setSubjectModal({ open: true, isEditing: true, id: s.id, departmentId: s.departmentId || '', code: s.code, name: s.name, credits: s.credits, semester: s.semester })} style={{ background: 'transparent', border: 'none', color: '#a5b4fc', cursor: 'pointer', marginRight: '8px' }}><Edit3 size={15} /></button>
                            <button onClick={() => handleDeleteSubject(s.id)} style={{ background: 'transparent', border: 'none', color: '#ef4444', cursor: 'pointer' }}><Trash2 size={15} /></button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Sub-tab 4: Rooms & Geofences */}
          {academicSubTab === 'rooms' && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem', alignItems: 'center' }}>
                <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: '0.88rem' }}>Classrooms configured with GPS coordinates and Haversine geofence perimeter.</p>
                <button 
                  onClick={() => setRoomModal({ open: true, isEditing: false, id: null, name: '', latitude: 18.52043, longitude: 73.85674, radiusM: 50, capacity: 60 })}
                  className="btn-primary"
                  style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '6px 14px', borderRadius: '6px', background: '#6366f1', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '0.85rem' }}
                >
                  <Plus size={15} /> Add Classroom
                </button>
              </div>
              <div className="table-responsive" style={{ background: 'var(--bg-surface)', borderRadius: '10px', border: '1px solid var(--border-subtle)', overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.02)' }}>
                      <th style={{ padding: '10px 14px' }}>Room Name</th>
                      <th style={{ padding: '10px 14px' }}>GPS Latitude</th>
                      <th style={{ padding: '10px 14px' }}>GPS Longitude</th>
                      <th style={{ padding: '10px 14px' }}>Geofence Radius</th>
                      <th style={{ padding: '10px 14px' }}>Seating Capacity</th>
                      <th style={{ padding: '10px 14px', textAlign: 'right' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rooms.length === 0 ? (
                      <tr><td colSpan="6" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-secondary)' }}>No classroom geofences configured.</td></tr>
                    ) : (
                      rooms.map(r => (
                        <tr key={r.id} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                          <td style={{ padding: '10px 14px', fontWeight: 600 }}>{r.name}</td>
                          <td style={{ padding: '10px 14px', color: 'var(--text-secondary)' }}>{r.latitude}</td>
                          <td style={{ padding: '10px 14px', color: 'var(--text-secondary)' }}>{r.longitude}</td>
                          <td style={{ padding: '10px 14px' }}>
                            <span style={{ padding: '2px 8px', borderRadius: '4px', background: 'rgba(99,102,241,0.1)', color: '#a5b4fc', fontSize: '0.8rem' }}>
                              &plusmn;{r.radiusM} meters
                            </span>
                          </td>
                          <td style={{ padding: '10px 14px' }}>{r.capacity} Seats</td>
                          <td style={{ padding: '10px 14px', textAlign: 'right' }}>
                            <button onClick={() => setRoomModal({ open: true, isEditing: true, id: r.id, name: r.name, latitude: r.latitude, longitude: r.longitude, radiusM: r.radiusM, capacity: r.capacity })} style={{ background: 'transparent', border: 'none', color: '#a5b4fc', cursor: 'pointer', marginRight: '8px' }}><Edit3 size={15} /></button>
                            <button onClick={() => handleDeleteRoom(r.id)} style={{ background: 'transparent', border: 'none', color: '#ef4444', cursor: 'pointer' }}><Trash2 size={15} /></button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* =========================================================================
          TAB 3: TARGETED NOTICES
          ========================================================================= */}
      {activeTab === 'notices' && (
        <div className="tab-pane animate-fade-in">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
            <div>
              <h3 style={{ margin: 0, fontSize: '1.1rem', color: 'var(--text-primary)' }}>Institutional Circulars & Broadcasts</h3>
              <p style={{ margin: '4px 0 0', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>Publish notices targeted by role, department, and class cohorts.</p>
            </div>
            <button 
              onClick={() => setNoticeModal({ open: true, isEditing: false, id: null, title: '', body: '', targetRole: 'ALL', targetDepartment: 'ALL', targetDivision: 'ALL', priority: 'NORMAL', deadline: '' })}
              className="btn-primary"
              style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 16px', borderRadius: '8px', background: '#6366f1', color: '#fff', border: 'none', fontWeight: 600, cursor: 'pointer' }}
            >
              <Plus size={16} /> Publish Notice
            </button>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(380px, 1fr))', gap: '1rem' }}>
            {loadingNotices ? (
              <div style={{ gridColumn: '1/-1', textAlign: 'center', padding: '3rem', color: 'var(--text-secondary)' }}>
                <RefreshCw size={24} className="animate-spin" style={{ margin: '0 auto 8px' }} />
                <p>Loading notices from database...</p>
              </div>
            ) : notices.length === 0 ? (
              <div style={{ gridColumn: '1/-1', textAlign: 'center', padding: '3.5rem', background: 'var(--bg-surface)', borderRadius: '12px', border: '1px solid var(--border-subtle)', color: 'var(--text-secondary)' }}>
                <Bell size={36} style={{ margin: '0 auto 12px', opacity: 0.4 }} />
                <p style={{ fontWeight: 600, fontSize: '1rem', color: 'var(--text-primary)' }}>No notices published yet</p>
                <p style={{ fontSize: '0.82rem' }}>Broadcast circulars published here will target specific student and staff dashboards.</p>
              </div>
            ) : (
              notices.map(n => (
                <div key={n.id} style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-subtle)', borderRadius: '12px', padding: '1.25rem', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.5rem' }}>
                      <span style={{
                        padding: '2px 8px',
                        borderRadius: '4px',
                        fontSize: '0.72rem',
                        fontWeight: 700,
                        background: n.priority === 'HIGH' ? 'rgba(239, 68, 68, 0.15)' : 'rgba(99, 102, 241, 0.15)',
                        color: n.priority === 'HIGH' ? '#f87171' : '#818cf8'
                      }}>
                        {n.priority || 'NORMAL'}
                      </span>
                      <div style={{ display: 'flex', gap: '6px' }}>
                        <button onClick={() => setNoticeModal({ open: true, isEditing: true, id: n.id, title: n.title, body: n.body, targetRole: n.targetRole || 'ALL', targetDepartment: n.targetDepartment || 'ALL', targetDivision: n.targetDivision || 'ALL', priority: n.priority || 'NORMAL', deadline: n.deadline || '' })} style={{ background: 'transparent', border: 'none', color: '#a5b4fc', cursor: 'pointer' }}><Edit3 size={15} /></button>
                        <button onClick={() => handleDeleteNotice(n.id)} style={{ background: 'transparent', border: 'none', color: '#ef4444', cursor: 'pointer' }}><Trash2 size={15} /></button>
                      </div>
                    </div>
                    <h4 style={{ margin: '0 0 0.5rem', fontSize: '1.05rem', color: 'var(--text-primary)', fontWeight: 600 }}>{n.title}</h4>
                    <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: '1.5' }}>{n.body}</p>
                  </div>
                  <div style={{ marginTop: '1rem', paddingTop: '0.75rem', borderTop: '1px solid var(--border-subtle)', display: 'flex', flexWrap: 'wrap', gap: '6px', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                    <span style={{ background: 'rgba(255,255,255,0.05)', padding: '2px 6px', borderRadius: '4px' }}>Target: {n.targetRole || 'All Roles'}</span>
                    <span style={{ background: 'rgba(255,255,255,0.05)', padding: '2px 6px', borderRadius: '4px' }}>Dept: {n.targetDepartment || 'All'}</span>
                    {n.targetDivision && n.targetDivision !== 'ALL' && (
                      <span style={{ background: 'rgba(255,255,255,0.05)', padding: '2px 6px', borderRadius: '4px' }}>Class: {n.targetDivision}</span>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* =========================================================================
          TAB 4: VERIFIABLE DOCUMENTS & PUBLIC VERIFIER
          ========================================================================= */}
      {activeTab === 'documents' && (
        <div className="tab-pane animate-fade-in">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '10px' }}>
            <div>
              <h3 style={{ margin: 0, fontSize: '1.1rem', color: 'var(--text-primary)' }}>Cryptographic Verifiable Document Issuance</h3>
              <p style={{ margin: '4px 0 0', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>Issue SHA-256 sealed degree certificates, transcripts, and bonafide records verified via Public Verifier.</p>
            </div>
            <button 
              onClick={() => setCredentialModal(prev => ({ ...prev, open: true }))}
              className="btn-primary"
              style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 16px', borderRadius: '8px', background: '#6366f1', color: '#fff', border: 'none', fontWeight: 600, cursor: 'pointer' }}
            >
              <FileCheck2 size={16} /> Issue Document
            </button>
          </div>

          {/* Issued Result Banner if newly created */}
          {issuedResult && (
            <div style={{ background: 'rgba(99, 102, 241, 0.12)', border: '1px solid rgba(99, 102, 241, 0.35)', borderRadius: '10px', padding: '1rem 1.25rem', marginBottom: '1.25rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontWeight: 700, color: '#a5b4fc', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <CheckCircle2 size={18} color="#10b981" /> Document Sealed & Recorded in Database
                </span>
                <button onClick={() => setIssuedResult(null)} style={{ background: 'transparent', border: 'none', color: '#fff', cursor: 'pointer' }}>✕</button>
              </div>
              <p style={{ margin: '8px 0 4px', fontSize: '0.85rem', color: 'var(--text-primary)' }}>
                <strong>Verification Code:</strong> <code>{issuedResult.verificationCode}</code>
              </p>
              <p style={{ margin: '0 0 8px', fontSize: '0.78rem', color: 'var(--text-secondary)', wordBreak: 'break-all' }}>
                <strong>SHA-256 Hash:</strong> <code>{issuedResult.documentHash}</code>
              </p>
              <a 
                href={issuedResult.verificationUrl || `/verify/${issuedResult.verificationCode}`} 
                target="_blank" 
                rel="noreferrer"
                style={{ color: '#818cf8', fontSize: '0.82rem', textDecoration: 'underline', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
              >
                Open Public Verifier View <ExternalLink size={13} />
              </a>
            </div>
          )}

          <div className="table-responsive" style={{ background: 'var(--bg-surface)', borderRadius: '12px', border: '1px solid var(--border-subtle)', overflow: 'hidden' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.02)' }}>
                  <th style={{ padding: '12px 16px' }}>Verification Code</th>
                  <th style={{ padding: '12px 16px' }}>Student & Roll</th>
                  <th style={{ padding: '12px 16px' }}>Document Type</th>
                  <th style={{ padding: '12px 16px' }}>Department</th>
                  <th style={{ padding: '12px 16px' }}>Grade / CGPA</th>
                  <th style={{ padding: '12px 16px' }}>Status</th>
                  <th style={{ padding: '12px 16px', textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {loadingCredentials ? (
                  <tr><td colSpan="7" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-secondary)' }}>Loading credential records...</td></tr>
                ) : credentials.length === 0 ? (
                  <tr><td colSpan="7" style={{ textAlign: 'center', padding: '3.5rem', color: 'var(--text-secondary)' }}>No documents or credentials issued yet in database.</td></tr>
                ) : (
                  credentials.map(c => (
                    <tr key={c.id} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                      <td style={{ padding: '12px 16px', fontFamily: 'monospace', fontWeight: 600, color: '#a5b4fc' }}>
                        {c.verificationCode}
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <div style={{ fontWeight: 600 }}>{c.studentName}</div>
                        <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>{c.rollNo}</div>
                      </td>
                      <td style={{ padding: '12px 16px' }}>{c.credentialType?.replace(/_/g, ' ')}</td>
                      <td style={{ padding: '12px 16px', color: 'var(--text-secondary)' }}>{c.department}</td>
                      <td style={{ padding: '12px 16px' }}>{c.gradeOrCgpa || '—'}</td>
                      <td style={{ padding: '12px 16px' }}>
                        <span style={{
                          padding: '3px 8px',
                          borderRadius: '4px',
                          fontSize: '0.75rem',
                          fontWeight: 600,
                          background: c.revoked ? 'rgba(239, 68, 68, 0.15)' : 'rgba(16, 185, 129, 0.15)',
                          color: c.revoked ? '#f87171' : '#34d399'
                        }}>
                          {c.revoked ? 'REVOKED' : 'VERIFIED / ACTIVE'}
                        </span>
                      </td>
                      <td style={{ padding: '12px 16px', textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', gap: '8px' }}>
                          <button
                            onClick={() => window.open(`/verify/${c.verificationCode}`, '_blank')}
                            title="Verify Online"
                            style={{ background: 'transparent', border: '1px solid var(--border-medium)', color: '#818cf8', borderRadius: '4px', padding: '4px 8px', fontSize: '0.75rem', cursor: 'pointer' }}
                          >
                            Verify Online
                          </button>
                          {!c.revoked && (
                            <button
                              onClick={() => handleRevokeCredential(c.verificationCode)}
                              title="Revoke Credential"
                              style={{ background: 'transparent', border: '1px solid rgba(239, 68, 68, 0.3)', color: '#ef4444', borderRadius: '4px', padding: '4px 8px', fontSize: '0.75rem', cursor: 'pointer' }}
                            >
                              Revoke
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}


      {/* =========================================================================
          TAB 6: STAFF APPROVALS
          ========================================================================= */}
      {activeTab === 'approvals' && (
        <div className="tab-pane animate-fade-in">
          <div style={{ marginBottom: '1.25rem' }}>
            <h3 style={{ margin: 0, fontSize: '1.1rem', color: 'var(--text-primary)' }}>Pending Staff Registrations</h3>
            <p style={{ margin: '4px 0 0', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>Review and approve staff account requests. Only approved staff can log in to IntelliCampus.</p>
          </div>

          <div className="table-responsive" style={{ background: 'var(--bg-surface)', borderRadius: '12px', border: '1px solid var(--border-subtle)', overflow: 'hidden' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.02)' }}>
                  <th style={{ padding: '12px 16px' }}>Staff ID / Code</th>
                  <th style={{ padding: '12px 16px' }}>Name & Email</th>
                  <th style={{ padding: '12px 16px' }}>Requested Role</th>
                  <th style={{ padding: '12px 16px' }}>Department</th>
                  <th style={{ padding: '12px 16px' }}>Status</th>
                  <th style={{ padding: '12px 16px', textAlign: 'right' }}>Decision</th>
                </tr>
              </thead>
              <tbody>
                {loadingApprovals ? (
                  <tr><td colSpan="6" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-secondary)' }}>Loading pending approvals...</td></tr>
                ) : pendingApprovals.length === 0 ? (
                  <tr>
                    <td colSpan="6" style={{ textAlign: 'center', padding: '3.5rem', color: 'var(--text-secondary)' }}>
                      <CheckCircle2 size={36} style={{ margin: '0 auto 12px', color: '#10b981', opacity: 0.6 }} />
                      <p style={{ fontWeight: 600, fontSize: '1rem', color: 'var(--text-primary)' }}>No pending staff approval requests</p>
                      <p style={{ fontSize: '0.82rem' }}>All staff registrations have been reviewed.</p>
                    </td>
                  </tr>
                ) : (
                  pendingApprovals.map(s => (
                    <tr key={s.id} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                      <td style={{ padding: '12px 16px', fontWeight: 600 }}>{s.userCode}</td>
                      <td style={{ padding: '12px 16px' }}>
                        <div style={{ fontWeight: 600 }}>{s.name}</div>
                        <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>{s.email}</div>
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <span style={{ padding: '2px 8px', borderRadius: '4px', background: 'rgba(99,102,241,0.1)', color: '#a5b4fc', fontSize: '0.78rem', fontWeight: 600 }}>
                          {s.role}
                        </span>
                      </td>
                      <td style={{ padding: '12px 16px', color: 'var(--text-secondary)' }}>{s.department || 'General'}</td>
                      <td style={{ padding: '12px 16px' }}>
                        <span style={{ padding: '2px 8px', borderRadius: '4px', background: 'rgba(245, 158, 11, 0.15)', color: '#fbbf24', fontSize: '0.75rem', fontWeight: 600 }}>
                          PENDING APPROVAL
                        </span>
                      </td>
                      <td style={{ padding: '12px 16px', textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', gap: '8px' }}>
                          <button
                            onClick={() => handleApproveStaff(s.id, s.name)}
                            style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', padding: '5px 10px', background: '#10b981', color: '#fff', border: 'none', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer' }}
                          >
                            <Check size={13} /> Approve
                          </button>
                          <button
                            onClick={() => handleRejectStaff(s.id, s.name)}
                            style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', padding: '5px 10px', background: 'transparent', color: '#ef4444', border: '1px solid rgba(239,68,68,0.4)', borderRadius: '4px', fontSize: '0.75rem', cursor: 'pointer' }}
                          >
                            <X size={13} /> Reject
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* =========================================================================
          TAB 7: AUDIT CHAIN
          ========================================================================= */}
      {activeTab === 'audit' && (
        <div className="tab-pane animate-fade-in">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '10px' }}>
            <div>
              <h3 style={{ margin: 0, fontSize: '1.1rem', color: 'var(--text-primary)' }}>Cryptographic SHA-256 Audit Trail</h3>
              <p style={{ margin: '4px 0 0', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>Immutable tamper-evident ledger tracking all administrative actions.</p>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{
                padding: '4px 10px',
                borderRadius: '20px',
                fontSize: '0.8rem',
                fontWeight: 600,
                background: chainValid ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
                color: chainValid ? '#34d399' : '#f87171',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '5px'
              }}>
                <CheckCircle2 size={14} /> {chainValid ? 'Chain Verified (SHA-256 Valid)' : 'Integrity Mismatch'}
              </span>
              <button onClick={loadAudit} className="btn-secondary" style={{ padding: '6px 12px', borderRadius: '6px', background: 'var(--bg-surface)', border: '1px solid var(--border-medium)', color: 'inherit', cursor: 'pointer' }}>
                <RefreshCw size={14} className={loadingAudit ? 'animate-spin' : ''} />
              </button>
            </div>
          </div>

          <div className="table-responsive" style={{ background: 'var(--bg-surface)', borderRadius: '12px', border: '1px solid var(--border-subtle)', overflow: 'hidden' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.02)' }}>
                  <th style={{ padding: '10px 14px' }}>Block #</th>
                  <th style={{ padding: '10px 14px' }}>Actor</th>
                  <th style={{ padding: '10px 14px' }}>Action</th>
                  <th style={{ padding: '10px 14px' }}>Entity Type & ID</th>
                  <th style={{ padding: '10px 14px' }}>Reason / Details</th>
                  <th style={{ padding: '10px 14px' }}>SHA-256 Entry Hash</th>
                </tr>
              </thead>
              <tbody>
                {loadingAudit ? (
                  <tr><td colSpan="6" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-secondary)' }}>Verifying and loading audit blocks...</td></tr>
                ) : auditChain.length === 0 ? (
                  <tr><td colSpan="6" style={{ textAlign: 'center', padding: '3.5rem', color: 'var(--text-secondary)' }}>No audit entries recorded yet in database.</td></tr>
                ) : (
                  auditChain.map((entry, idx) => (
                    <tr key={entry.id || idx} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                      <td style={{ padding: '10px 14px', fontWeight: 700, color: '#a5b4fc' }}>#{entry.id || idx + 1}</td>
                      <td style={{ padding: '10px 14px' }}>{entry.actorName || 'System'}</td>
                      <td style={{ padding: '10px 14px' }}>
                        <span style={{ padding: '2px 6px', borderRadius: '4px', background: 'rgba(255,255,255,0.06)', fontSize: '0.75rem', fontWeight: 600 }}>
                          {entry.action}
                        </span>
                      </td>
                      <td style={{ padding: '10px 14px', color: 'var(--text-secondary)' }}>{entry.entityType} ({entry.entityId})</td>
                      <td style={{ padding: '10px 14px', maxWidth: '280px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{entry.reason || '—'}</td>
                      <td style={{ padding: '10px 14px', fontFamily: 'monospace', fontSize: '0.72rem', color: '#10b981' }}>
                        {entry.entryHash ? `${entry.entryHash.substring(0, 16)}...` : '0000000000000000...'}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* =========================================================================
          MODALS
          ========================================================================= */}
      {/* 1. Add User Modal */}
      {showAddUserModal && (
        <div className="modal-backdrop" style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(5px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
          <div className="modal-content animate-fade-in" style={{ background: '#1e2030', border: '1px solid var(--border-medium)', borderRadius: '12px', padding: '1.75rem', width: '100%', maxWidth: '520px', maxHeight: '90vh', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
              <h3 style={{ margin: 0, fontSize: '1.2rem', color: 'var(--text-primary)' }}>Add New User</h3>
              <button onClick={() => setShowAddUserModal(false)} style={{ background: 'transparent', border: 'none', color: '#94a3b8', fontSize: '1.2rem', cursor: 'pointer' }}>✕</button>
            </div>
            <form onSubmit={handleCreateUser} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Full Name *</label>
                <input 
                  type="text" 
                  required 
                  placeholder="e.g. Ramesh Deshmukh" 
                  value={newUser.name}
                  onChange={e => setNewUser({ ...newUser, name: e.target.value })}
                  style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }}
                />
              </div>

              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Email Address *</label>
                <input 
                  type="email" 
                  required 
                  placeholder="staff@intellicampus.edu" 
                  value={newUser.email}
                  onChange={e => setNewUser({ ...newUser, email: e.target.value })}
                  style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }}
                />
              </div>

              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                  <label style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Password *</label>
                  <button 
                    type="button" 
                    onClick={() => setNewUser({ ...newUser, password: generateStrongPassword() })}
                    style={{ background: 'rgba(99, 102, 241, 0.15)', border: '1px solid rgba(99, 102, 241, 0.4)', color: '#818cf8', borderRadius: '4px', padding: '2px 8px', fontSize: '0.72rem', cursor: 'pointer', fontWeight: 600 }}
                  >
                    Generate strong password
                  </button>
                </div>
                <div style={{ position: 'relative' }}>
                  <input 
                    type={showNewUserPassword ? 'text' : 'password'} 
                    required 
                    placeholder="Min 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special char" 
                    value={newUser.password}
                    onChange={e => setNewUser({ ...newUser, password: e.target.value })}
                    style={{ width: '100%', padding: '8px 40px 8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }}
                  />
                  <button 
                    type="button" 
                    onClick={() => setShowNewUserPassword(!showNewUserPassword)}
                    style={{ position: 'absolute', right: '10px', top: '50%', transform: 'translateY(-50%)', background: 'transparent', border: 'none', color: '#94a3b8', cursor: 'pointer', padding: 0, display: 'flex', alignItems: 'center' }}
                  >
                    {showNewUserPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
                {newUser.password && (
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '4px', marginTop: '6px', fontSize: '0.72rem' }}>
                    <span style={{ color: newUser.password.length >= 8 ? '#10b981' : '#f87171' }}>
                      {newUser.password.length >= 8 ? '✓' : '✗'} 8+ characters
                    </span>
                    <span style={{ color: /[A-Z]/.test(newUser.password) ? '#10b981' : '#f87171' }}>
                      {/[A-Z]/.test(newUser.password) ? '✓' : '✗'} Uppercase letter
                    </span>
                    <span style={{ color: /[a-z]/.test(newUser.password) ? '#10b981' : '#f87171' }}>
                      {/[a-z]/.test(newUser.password) ? '✓' : '✗'} Lowercase letter
                    </span>
                    <span style={{ color: /[0-9]/.test(newUser.password) && /[^a-zA-Z0-9]/.test(newUser.password) ? '#10b981' : '#f87171' }}>
                      {/[0-9]/.test(newUser.password) && /[^a-zA-Z0-9]/.test(newUser.password) ? '✓' : '✗'} Number & Special char
                    </span>
                  </div>
                )}
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Role *</label>
                  <select 
                    value={newUser.role}
                    onChange={e => setNewUser({ ...newUser, role: e.target.value })}
                    style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: '#25283d', border: '1px solid var(--border-medium)', color: '#fff' }}
                  >
                    <option value="HOD">HOD</option>
                    <option value="FACULTY">Faculty</option>
                    <option value="MENTOR">Mentor</option>
                  </select>
                </div>

                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Department *</label>
                  <select 
                    value={newUser.department}
                    onChange={e => setNewUser({ ...newUser, department: e.target.value })}
                    style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: '#25283d', border: '1px solid var(--border-medium)', color: '#fff' }}
                  >
                    {departments.map(d => (
                      <option key={d.id} value={d.name}>{d.name}</option>
                    ))}
                    {departments.length === 0 && <option value="Computer Science">Computer Science</option>}
                  </select>
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '0.5rem' }}>
                <button type="button" onClick={() => setShowAddUserModal(false)} className="btn-secondary" style={{ padding: '8px 16px', borderRadius: '6px', background: 'transparent', border: '1px solid var(--border-medium)', color: '#fff', cursor: 'pointer' }}>Cancel</button>
                <button type="submit" disabled={addUserLoading} className="btn-primary" style={{ padding: '8px 18px', borderRadius: '6px', background: '#6366f1', color: '#fff', border: 'none', fontWeight: 600, cursor: 'pointer' }}>
                  {addUserLoading ? 'Creating User...' : 'Create User'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 2. Bulk CSV Import Modal */}
      {csvModal.open && (
        <div className="modal-backdrop" style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(5px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
          <div className="modal-content animate-fade-in" style={{ background: '#1e2030', border: '1px solid var(--border-medium)', borderRadius: '12px', padding: '1.75rem', width: '100%', maxWidth: '640px', maxHeight: '90vh', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
              <h3 style={{ margin: 0, fontSize: '1.2rem', color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <FileSpreadsheet size={20} color="#6366f1" /> Bulk CSV Staff Ingestion
              </h3>
              <button onClick={() => setCsvModal(prev => ({ ...prev, open: false }))} style={{ background: 'transparent', border: 'none', color: '#94a3b8', fontSize: '1.2rem', cursor: 'pointer' }}>✕</button>
            </div>

            <div style={{ marginBottom: '1.25rem', background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.25)', borderRadius: '8px', padding: '1rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                <span style={{ fontWeight: 600, fontSize: '0.85rem', color: '#a5b4fc' }}>Standard Staff CSV Format</span>
                <button 
                  type="button" 
                  onClick={downloadCsvTemplate} 
                  style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', background: '#6366f125', color: '#818cf8', border: '1px solid #6366f150', padding: '4px 10px', borderRadius: '4px', fontSize: '0.78rem', cursor: 'pointer', fontWeight: 600 }}
                >
                  <Download size={13} /> Download CSV Template
                </button>
              </div>
              <code style={{ fontSize: '0.75rem', color: '#e0e7ff', display: 'block', wordBreak: 'break-all' }}>
                full_name,email,password,role,department (Staff Only: HOD, Faculty, Mentor)
              </code>
            </div>

            <div style={{ marginBottom: '1.25rem' }}>
              <label style={{ display: 'block', marginBottom: '6px', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Select CSV File</label>
              <input 
                type="file" 
                accept=".csv" 
                onChange={handleCsvFileChange}
                style={{ width: '100%', padding: '10px', background: 'rgba(255,255,255,0.04)', border: '1px solid var(--border-medium)', borderRadius: '8px', color: '#fff' }}
              />
            </div>

            {csvModal.errors.length > 0 && (
              <div style={{ background: 'rgba(239, 68, 68, 0.12)', border: '1px solid rgba(239, 68, 68, 0.3)', borderRadius: '8px', padding: '0.75rem', marginBottom: '1rem', maxHeight: '120px', overflowY: 'auto' }}>
                <span style={{ color: '#f87171', fontWeight: 600, fontSize: '0.82rem', display: 'block', marginBottom: '4px' }}>Validation Warnings ({csvModal.errors.length}):</span>
                {csvModal.errors.map((err, i) => (
                  <div key={i} style={{ color: '#fca5a5', fontSize: '0.75rem' }}>• {err}</div>
                ))}
              </div>
            )}

            {csvModal.parsedRows.length > 0 && (
              <div style={{ marginBottom: '1rem' }}>
                <span style={{ fontSize: '0.82rem', color: '#10b981', fontWeight: 600 }}>
                  Ready to import: {csvModal.parsedRows.length} valid user records
                </span>
              </div>
            )}

            {csvModal.resultSummary && (
              <div style={{ background: 'rgba(16,185,129,0.12)', border: '1px solid rgba(16,185,129,0.35)', borderRadius: '8px', padding: '0.85rem', marginBottom: '1rem' }}>
                <div style={{ fontWeight: 600, color: '#34d399', fontSize: '0.88rem' }}>Import Execution Finished</div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-primary)', marginTop: '4px' }}>
                  Total Processed: {csvModal.resultSummary.totalRows || csvModal.resultSummary.totalCount} | Imported: {csvModal.resultSummary.importedCount} | Failed: {csvModal.resultSummary.failedCount}
                </div>
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
              <button type="button" onClick={() => setCsvModal(prev => ({ ...prev, open: false }))} className="btn-secondary" style={{ padding: '8px 16px', borderRadius: '6px', background: 'transparent', border: '1px solid var(--border-medium)', color: '#fff', cursor: 'pointer' }}>Close</button>
              <button 
                type="button" 
                disabled={csvModal.isSubmitting || csvModal.parsedRows.length === 0} 
                onClick={handleExecuteCsvImport}
                className="btn-primary" 
                style={{ padding: '8px 18px', borderRadius: '6px', background: '#6366f1', color: '#fff', border: 'none', fontWeight: 600, cursor: 'pointer' }}
              >
                {csvModal.isSubmitting ? 'Importing Users...' : 'Execute Import'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 2b. Reset Staff Password Modal */}
      {resetModal.open && (
        <div className="modal-backdrop" style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(5px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
          <div className="modal-content animate-fade-in" style={{ background: '#1e2030', border: '1px solid var(--border-medium)', borderRadius: '12px', padding: '1.75rem', width: '100%', maxWidth: '480px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
              <h3 style={{ margin: 0, fontSize: '1.2rem', color: 'var(--text-primary)' }}>Reset Staff Password</h3>
              <button onClick={() => setResetModal({ open: false, user: null, password: '', showPassword: false, loading: false })} style={{ background: 'transparent', border: 'none', color: '#94a3b8', fontSize: '1.2rem', cursor: 'pointer' }}>✕</button>
            </div>
            <div style={{ marginBottom: '1rem', padding: '0.75rem', background: 'rgba(255,255,255,0.03)', borderRadius: '6px', fontSize: '0.85rem' }}>
              <div><strong style={{ color: '#fff' }}>User:</strong> {resetModal.user?.name}</div>
              <div style={{ color: '#94a3b8', marginTop: '2px' }}><strong>Email:</strong> {resetModal.user?.email} ({resetModal.user?.role})</div>
            </div>
            <div style={{ marginBottom: '1.25rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                <label style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>New Password *</label>
                <button 
                  type="button" 
                  onClick={() => setResetModal(prev => ({ ...prev, password: generateStrongPassword() }))}
                  style={{ background: 'rgba(99, 102, 241, 0.15)', border: '1px solid rgba(99, 102, 241, 0.4)', color: '#818cf8', borderRadius: '4px', padding: '2px 8px', fontSize: '0.72rem', cursor: 'pointer', fontWeight: 600 }}
                >
                  Generate strong password
                </button>
              </div>
              <div style={{ position: 'relative' }}>
                <input 
                  type={resetModal.showPassword ? 'text' : 'password'} 
                  required 
                  placeholder="Min 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special char" 
                  value={resetModal.password}
                  onChange={e => setResetModal(prev => ({ ...prev, password: e.target.value }))}
                  style={{ width: '100%', padding: '8px 40px 8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }}
                />
                <button 
                  type="button" 
                  onClick={() => setResetModal(prev => ({ ...prev, showPassword: !prev.showPassword }))}
                  style={{ position: 'absolute', right: '10px', top: '50%', transform: 'translateY(-50%)', background: 'transparent', border: 'none', color: '#94a3b8', cursor: 'pointer', padding: 0, display: 'flex', alignItems: 'center' }}
                >
                  {resetModal.showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
              <button 
                type="button" 
                onClick={() => setResetModal({ open: false, user: null, password: '', showPassword: false, loading: false })} 
                className="btn-secondary" 
                style={{ padding: '8px 16px', borderRadius: '6px', background: 'transparent', border: '1px solid var(--border-medium)', color: '#fff', cursor: 'pointer' }}
              >
                Cancel
              </button>
              <button 
                type="button" 
                disabled={resetModal.loading || !resetModal.password} 
                onClick={handleConfirmResetPassword}
                className="btn-primary" 
                style={{ padding: '8px 18px', borderRadius: '6px', background: '#6366f1', color: '#fff', border: 'none', fontWeight: 600, cursor: 'pointer' }}
              >
                {resetModal.loading ? 'Updating Password...' : 'Save New Password'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 3. Notice Modal */}
      {noticeModal.open && (
        <div className="modal-backdrop" style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(5px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
          <div className="modal-content animate-fade-in" style={{ background: '#1e2030', border: '1px solid var(--border-medium)', borderRadius: '12px', padding: '1.75rem', width: '100%', maxWidth: '520px' }}>
            <h3 style={{ margin: '0 0 1rem', fontSize: '1.2rem', color: 'var(--text-primary)' }}>
              {noticeModal.isEditing ? 'Edit Notice' : 'Publish Institutional Notice'}
            </h3>
            <form onSubmit={handleSaveNotice} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Notice Title *</label>
                <input 
                  type="text" 
                  required 
                  placeholder="e.g. End Semester Exam Registration Deadline" 
                  value={noticeModal.title}
                  onChange={e => setNoticeModal({ ...noticeModal, title: e.target.value })}
                  style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }}
                />
              </div>

              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Notice Body / Content *</label>
                <textarea 
                  required 
                  rows={4}
                  placeholder="Detailed circular contents and guidelines for students and faculty..." 
                  value={noticeModal.body}
                  onChange={e => setNoticeModal({ ...noticeModal, body: e.target.value })}
                  style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff', resize: 'vertical' }}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Target Role</label>
                  <select 
                    value={noticeModal.targetRole}
                    onChange={e => setNoticeModal({ ...noticeModal, targetRole: e.target.value })}
                    style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: '#25283d', border: '1px solid var(--border-medium)', color: '#fff' }}
                  >
                    <option value="ALL">All Roles</option>
                    <option value="STUDENT">Students Only</option>
                    <option value="FACULTY">Faculty Only</option>
                    <option value="HOD">HOD Only</option>
                  </select>
                </div>

                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Target Department</label>
                  <select 
                    value={noticeModal.targetDepartment}
                    onChange={e => setNoticeModal({ ...noticeModal, targetDepartment: e.target.value })}
                    style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: '#25283d', border: '1px solid var(--border-medium)', color: '#fff' }}
                  >
                    <option value="ALL">All Departments</option>
                    {departments.map(d => (
                      <option key={d.id} value={d.name}>{d.name}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                <button type="button" onClick={() => setNoticeModal(prev => ({ ...prev, open: false }))} className="btn-secondary" style={{ padding: '8px 16px', borderRadius: '6px', background: 'transparent', border: '1px solid var(--border-medium)', color: '#fff', cursor: 'pointer' }}>Cancel</button>
                <button type="submit" className="btn-primary" style={{ padding: '8px 18px', borderRadius: '6px', background: '#6366f1', color: '#fff', border: 'none', fontWeight: 600, cursor: 'pointer' }}>
                  {noticeModal.isEditing ? 'Update Notice' : 'Broadcast Notice'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 4. Issue Credential Modal */}
      {credentialModal.open && (
        <div className="modal-backdrop" style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(5px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
          <div className="modal-content animate-fade-in" style={{ background: '#1e2030', border: '1px solid var(--border-medium)', borderRadius: '12px', padding: '1.75rem', width: '100%', maxWidth: '520px' }}>
            <h3 style={{ margin: '0 0 1rem', fontSize: '1.2rem', color: 'var(--text-primary)' }}>Issue Verifiable Credential</h3>
            <form onSubmit={handleIssueCredential} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Student Name *</label>
                <input 
                  type="text" 
                  required 
                  placeholder="e.g. Diya Sharma" 
                  value={credentialModal.studentName}
                  onChange={e => setCredentialModal({ ...credentialModal, studentName: e.target.value })}
                  style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Roll No *</label>
                  <input 
                    type="text" 
                    required 
                    placeholder="e.g. CS-102" 
                    value={credentialModal.rollNo}
                    onChange={e => setCredentialModal({ ...credentialModal, rollNo: e.target.value })}
                    style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Document Type *</label>
                  <select 
                    value={credentialModal.credentialType}
                    onChange={e => setCredentialModal({ ...credentialModal, credentialType: e.target.value })}
                    style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: '#25283d', border: '1px solid var(--border-medium)', color: '#fff' }}
                  >
                    <option value="PROVISIONAL_DEGREE">Provisional Degree</option>
                    <option value="BONAFIDE_CERTIFICATE">Bonafide Certificate</option>
                    <option value="TRANSCRIPT">Official Transcript</option>
                  </select>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Department</label>
                  <select 
                    value={credentialModal.department}
                    onChange={e => setCredentialModal({ ...credentialModal, department: e.target.value })}
                    style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: '#25283d', border: '1px solid var(--border-medium)', color: '#fff' }}
                  >
                    {departments.map(d => (
                      <option key={d.id} value={d.name}>{d.name}</option>
                    ))}
                    {departments.length === 0 && <option value="Computer Science">Computer Science</option>}
                  </select>
                </div>
                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Grade / CGPA</label>
                  <input 
                    type="text" 
                    placeholder="e.g. 8.75 / 10" 
                    value={credentialModal.gradeOrCgpa}
                    onChange={e => setCredentialModal({ ...credentialModal, gradeOrCgpa: e.target.value })}
                    style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }}
                  />
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                <button type="button" onClick={() => setCredentialModal(prev => ({ ...prev, open: false }))} className="btn-secondary" style={{ padding: '8px 16px', borderRadius: '6px', background: 'transparent', border: '1px solid var(--border-medium)', color: '#fff', cursor: 'pointer' }}>Cancel</button>
                <button type="submit" className="btn-primary" style={{ padding: '8px 18px', borderRadius: '6px', background: '#6366f1', color: '#fff', border: 'none', fontWeight: 600, cursor: 'pointer' }}>
                  Issue & Seal Credential
                </button>
              </div>
            </form>
          </div>
        </div>
      )}


      {/* 6. Academic Setup Modals (Department, Division, Subject, Room) */}
      {deptModal.open && (
        <div className="modal-backdrop" style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(5px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
          <div className="modal-content animate-fade-in" style={{ background: '#1e2030', border: '1px solid var(--border-medium)', borderRadius: '12px', padding: '1.75rem', width: '100%', maxWidth: '440px' }}>
            <h3 style={{ margin: '0 0 1rem', fontSize: '1.15rem' }}>{deptModal.isEditing ? 'Edit Department' : 'New Department'}</h3>
            <form onSubmit={handleSaveDepartment} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Department Code *</label>
                <input required placeholder="e.g. CSE" value={deptModal.code} onChange={e => setDeptModal({ ...deptModal, code: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }} />
              </div>
              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Department Name *</label>
                <input required placeholder="e.g. Computer Science and Engineering" value={deptModal.name} onChange={e => setDeptModal({ ...deptModal, name: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }} />
              </div>
              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Assigned HOD Name</label>
                <input placeholder="e.g. Dr. Ramesh Kulkarni" value={deptModal.hodName} onChange={e => setDeptModal({ ...deptModal, hodName: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }} />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                <button type="button" onClick={() => setDeptModal(prev => ({ ...prev, open: false }))} className="btn-secondary" style={{ padding: '8px 16px', borderRadius: '6px', background: 'transparent', border: '1px solid var(--border-medium)', color: '#fff', cursor: 'pointer' }}>Cancel</button>
                <button type="submit" className="btn-primary" style={{ padding: '8px 18px', borderRadius: '6px', background: '#6366f1', color: '#fff', border: 'none', fontWeight: 600, cursor: 'pointer' }}>Save</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {divisionModal.open && (
        <div className="modal-backdrop" style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(5px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
          <div className="modal-content animate-fade-in" style={{ background: '#1e2030', border: '1px solid var(--border-medium)', borderRadius: '12px', padding: '1.75rem', width: '100%', maxWidth: '440px' }}>
            <h3 style={{ margin: '0 0 1rem', fontSize: '1.15rem' }}>{divisionModal.isEditing ? 'Edit Division' : 'New Division'}</h3>
            <form onSubmit={handleSaveDivision} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Division Name *</label>
                <input required placeholder="e.g. Div A" value={divisionModal.name} onChange={e => setDivisionModal({ ...divisionModal, name: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }} />
              </div>
              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Department *</label>
                <select value={divisionModal.departmentId} onChange={e => setDivisionModal({ ...divisionModal, departmentId: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: '#25283d', border: '1px solid var(--border-medium)', color: '#fff' }}>
                  {departments.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                </select>
              </div>
              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Academic Year</label>
                <input placeholder="2025-2026" value={divisionModal.academicYear} onChange={e => setDivisionModal({ ...divisionModal, academicYear: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }} />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                <button type="button" onClick={() => setDivisionModal(prev => ({ ...prev, open: false }))} className="btn-secondary" style={{ padding: '8px 16px', borderRadius: '6px', background: 'transparent', border: '1px solid var(--border-medium)', color: '#fff', cursor: 'pointer' }}>Cancel</button>
                <button type="submit" className="btn-primary" style={{ padding: '8px 18px', borderRadius: '6px', background: '#6366f1', color: '#fff', border: 'none', fontWeight: 600, cursor: 'pointer' }}>Save</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {subjectModal.open && (
        <div className="modal-backdrop" style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(5px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
          <div className="modal-content animate-fade-in" style={{ background: '#1e2030', border: '1px solid var(--border-medium)', borderRadius: '12px', padding: '1.75rem', width: '100%', maxWidth: '440px' }}>
            <h3 style={{ margin: '0 0 1rem', fontSize: '1.15rem' }}>{subjectModal.isEditing ? 'Edit Subject' : 'New Subject'}</h3>
            <form onSubmit={handleSaveSubject} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Subject Code *</label>
                <input required placeholder="e.g. CS-301" value={subjectModal.code} onChange={e => setSubjectModal({ ...subjectModal, code: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }} />
              </div>
              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Subject Title *</label>
                <input required placeholder="e.g. Distributed Operating Systems" value={subjectModal.name} onChange={e => setSubjectModal({ ...subjectModal, name: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }} />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Department</label>
                  <select value={subjectModal.departmentId} onChange={e => setSubjectModal({ ...subjectModal, departmentId: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: '#25283d', border: '1px solid var(--border-medium)', color: '#fff' }}>
                    {departments.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                  </select>
                </div>
                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Credits</label>
                  <input type="number" min="1" max="10" value={subjectModal.credits} onChange={e => setSubjectModal({ ...subjectModal, credits: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }} />
                </div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                <button type="button" onClick={() => setSubjectModal(prev => ({ ...prev, open: false }))} className="btn-secondary" style={{ padding: '8px 16px', borderRadius: '6px', background: 'transparent', border: '1px solid var(--border-medium)', color: '#fff', cursor: 'pointer' }}>Cancel</button>
                <button type="submit" className="btn-primary" style={{ padding: '8px 18px', borderRadius: '6px', background: '#6366f1', color: '#fff', border: 'none', fontWeight: 600, cursor: 'pointer' }}>Save</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {roomModal.open && (
        <div className="modal-backdrop" style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(5px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
          <div className="modal-content animate-fade-in" style={{ background: '#1e2030', border: '1px solid var(--border-medium)', borderRadius: '12px', padding: '1.75rem', width: '100%', maxWidth: '480px' }}>
            <h3 style={{ margin: '0 0 1rem', fontSize: '1.15rem' }}>{roomModal.isEditing ? 'Edit Classroom Geofence' : 'New Geofenced Classroom'}</h3>
            <form onSubmit={handleSaveRoom} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Room Name *</label>
                <input required placeholder="e.g. Hall 101" value={roomModal.name} onChange={e => setRoomModal({ ...roomModal, name: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }} />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>GPS Latitude</label>
                  <input type="number" step="0.000001" required value={roomModal.latitude} onChange={e => setRoomModal({ ...roomModal, latitude: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }} />
                </div>
                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>GPS Longitude</label>
                  <input type="number" step="0.000001" required value={roomModal.longitude} onChange={e => setRoomModal({ ...roomModal, longitude: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }} />
                </div>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Geofence Radius (m)</label>
                  <input type="number" min="5" max="500" value={roomModal.radiusM} onChange={e => setRoomModal({ ...roomModal, radiusM: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }} />
                </div>
                <div>
                  <label style={{ display: 'block', marginBottom: '4px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Seating Capacity</label>
                  <input type="number" min="1" value={roomModal.capacity} onChange={e => setRoomModal({ ...roomModal, capacity: e.target.value })} style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-medium)', color: '#fff' }} />
                </div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                <button type="button" onClick={() => setRoomModal(prev => ({ ...prev, open: false }))} className="btn-secondary" style={{ padding: '8px 16px', borderRadius: '6px', background: 'transparent', border: '1px solid var(--border-medium)', color: '#fff', cursor: 'pointer' }}>Cancel</button>
                <button type="submit" className="btn-primary" style={{ padding: '8px 18px', borderRadius: '6px', background: '#6366f1', color: '#fff', border: 'none', fontWeight: 600, cursor: 'pointer' }}>Save Geofence</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 7. Change Password Modal */}
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
              setApiSuccess('Principal password updated successfully!');
            }}
          />
        </div>
      )}

      {/* Principal Live Institutional Data Assistant Drawer */}
      <PrincipalAssistantDrawer
        isOpen={isAssistantOpen}
        onClose={() => setIsAssistantOpen(false)}
      />
    </div>
  );
}
