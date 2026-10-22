import React, { useState, useEffect, useRef } from 'react';
import { 
  Bell, 
  Send, 
  Users, 
  UserCheck, 
  Search, 
  X, 
  Calendar, 
  CheckCircle2, 
  AlertCircle, 
  Loader2,
  Sparkles
} from 'lucide-react';
import { publishNotice, searchStudents, getStoredUser } from '../api/client';
import { INITIAL_USERS } from '../data/mockData';

// Fallback staff users from mock data for demo roles without active session
const DEMO_STAFF_MAP = {
  FACULTY: { id: 3, name: 'Prof. Vikram Verma', department: 'Computer Science' },
  MENTOR: { id: 4, name: 'Dr. Sunita Mehta', department: 'Computer Science' },
  HOD: { id: 2, name: 'Dr. Ananya Sharma', department: 'Computer Science' },
  ADMIN: { id: 1, name: 'Rajesh Patel', department: 'Administration' }
};

const ALLOWED_DEPARTMENTS = [
  'All Departments',
  'Computer Science',
  'Information Technology',
  'Mechanical Engineering',
  'Civil Engineering',
  'Electronics & Telecom'
];

const ALLOWED_YEARS = [
  'All Years',
  'Year 1',
  'Year 2',
  'Year 3',
  'Year 4'
];

const ALLOWED_DIVISIONS = [
  'All Divisions',
  'Div A',
  'Div B',
  'Div C'
];

export default function NoticeComposer({
  publisherRole = 'FACULTY',
  publisherName,
  publisherId,
  defaultDepartment,
  onNoticePublished,
  onAddAuditLog,
  initialStudents,
  cardTitle = 'Send Notice',
  cardDescription = 'Broadcast official announcements to classes or targeted students.'
}) {
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [recipientMode, setRecipientMode] = useState('GROUP'); // 'GROUP' | 'SPECIFIC'
  const [targetDepartment, setTargetDepartment] = useState(defaultDepartment || 'All Departments');
  const [targetYear, setTargetYear] = useState('All Years');
  const [targetDivision, setTargetDivision] = useState('All Divisions');
  
  // Specific student targeting state
  const [selectedStudents, setSelectedStudents] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [isSearching, setIsSearching] = useState(false);
  const [isSearchFocused, setIsSearchFocused] = useState(false);
  
  const [deadline, setDeadline] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [feedback, setFeedback] = useState(null); // { type: 'success' | 'error', text: string }

  const debounceRef = useRef(null);
  const searchContainerRef = useRef(null);

  // Resolve effective publisher
  const storedUser = getStoredUser();
  const effectiveRole = (publisherRole || storedUser?.role || 'FACULTY').toUpperCase();
  const fallback = DEMO_STAFF_MAP[effectiveRole] || DEMO_STAFF_MAP.FACULTY;
  const effectiveId = publisherId || storedUser?.id || fallback.id;
  const effectiveName = publisherName || storedUser?.name || fallback.name;

  // Sync defaultDepartment if it changes
  useEffect(() => {
    if (defaultDepartment) {
      setTargetDepartment(defaultDepartment);
    }
  }, [defaultDepartment]);

  // Handle outside clicks to close search dropdown
  useEffect(() => {
    function handleClickOutside(event) {
      if (searchContainerRef.current && !searchContainerRef.current.contains(event.target)) {
        setIsSearchFocused(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Debounced student search
  useEffect(() => {
    if (recipientMode !== 'SPECIFIC') return;

    const trimmed = searchQuery.trim();
    if (!trimmed) {
      setSearchResults([]);
      setIsSearching(false);
      return;
    }

    setIsSearching(true);
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    debounceRef.current = setTimeout(async () => {
      try {
        const results = await searchStudents(trimmed);
        const list = Array.isArray(results) ? results : [];
        // Filter out already selected students
        const filtered = list.filter(s => !selectedStudents.some(sel => sel.id === s.id));
        setSearchResults(filtered);
      } catch (err) {
        // Fallback to searching mock users / initialStudents
        const q = trimmed.toLowerCase();
        const baseList = initialStudents || INITIAL_USERS.filter(u => u.role === 'STUDENT');
        const fallbackList = baseList.filter(s => {
          const matchName = s.name && s.name.toLowerCase().includes(q);
          const matchRoll = s.rollNo && s.rollNo.toLowerCase().includes(q);
          const matchCode = s.userCode && s.userCode.toLowerCase().includes(q);
          return (matchName || matchRoll || matchCode) && !selectedStudents.some(sel => sel.id === s.id);
        });
        setSearchResults(fallbackList);
      } finally {
        setIsSearching(false);
      }
    }, 250);

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [searchQuery, recipientMode, selectedStudents, initialStudents]);

  const handleSelectStudent = (student) => {
    if (!selectedStudents.some(s => s.id === student.id)) {
      setSelectedStudents(prev => [...prev, student]);
    }
    setSearchQuery('');
    setSearchResults([]);
    setIsSearchFocused(false);
  };

  const handleRemoveStudent = (studentId) => {
    setSelectedStudents(prev => prev.filter(s => s.id !== studentId));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFeedback(null);

    // Validation
    if (!title.trim()) {
      setFeedback({ type: 'error', text: 'Notice title is required.' });
      return;
    }

    if (!message.trim()) {
      setFeedback({ type: 'error', text: 'Notice message is required.' });
      return;
    }

    if (recipientMode === 'SPECIFIC' && selectedStudents.length === 0) {
      setFeedback({ type: 'error', text: 'Please select at least one specific student recipient.' });
      return;
    }

    setIsSubmitting(true);

    const isSpecific = recipientMode === 'SPECIFIC';
    const payload = {
      title: title.trim(),
      body: message.trim(),
      publisherRole: effectiveRole,
      publisherId: Number(effectiveId),
      targetDepartment: isSpecific ? 'All Departments' : targetDepartment,
      targetYear: isSpecific ? 'All Years' : targetYear,
      targetDivision: isSpecific ? 'All Divisions' : targetDivision,
      targetStudentIds: isSpecific ? selectedStudents.map(s => Number(s.id)) : [],
      deadline: deadline || null,
      hasAttachment: false
    };

    try {
      const response = await publishNotice(payload);
      const createdNotice = (response && response.id) ? response : {
        id: Date.now(),
        ...payload,
        publisherRole: effectiveRole,
        publisherName: effectiveName,
        publishedAt: new Date().toISOString(),
        readsCount: 0,
        totalTargeted: isSpecific ? selectedStudents.length : 65
      };

      const recipientSummary = isSpecific
        ? `${selectedStudents.length} student${selectedStudents.length > 1 ? 's' : ''}`
        : `${targetDepartment} (${targetYear}, ${targetDivision})`;

      setFeedback({
        type: 'success',
        text: `Notice published successfully to ${recipientSummary}!`
      });

      // Reset form
      setTitle('');
      setMessage('');
      setDeadline('');
      setSelectedStudents([]);
      setSearchQuery('');
      setSearchResults([]);

      if (onNoticePublished) {
        onNoticePublished(createdNotice);
      }

      if (onAddAuditLog) {
        onAddAuditLog({
          action: 'PUBLISH',
          entityType: 'NOTICE',
          entityId: String(createdNotice.id),
          oldValue: '{}',
          newValue: JSON.stringify({
            title: createdNotice.title,
            publisherRole: effectiveRole,
            publisherId: effectiveId,
            mode: recipientMode,
            targets: isSpecific ? payload.targetStudentIds : recipientSummary
          }),
          reason: `${effectiveRole} official notice published by ${effectiveName}`
        });
      }
    } catch (err) {
      setFeedback({
        type: 'error',
        text: err?.message || 'Failed to publish notice. Please verify details and try again.'
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="glass-card notice-composer-card animate-fade-in" style={{ padding: '24px' }}>
      <div className="notice-composer-header" style={{ marginBottom: '18px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px' }}>
          <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '8px', fontSize: '1.2rem' }}>
            <Bell size={20} className="text-primary" /> {cardTitle}
          </h3>
          <span className="badge-pill accent" style={{ fontSize: '0.75rem' }}>
            Publishing as: <strong>{effectiveName}</strong> ({effectiveRole})
          </span>
        </div>
        <p className="card-desc" style={{ marginTop: '4px', color: 'var(--text-secondary)' }}>
          {cardDescription}
        </p>
      </div>

      {feedback && (
        <div 
          className={`notice-feedback-banner ${feedback.type === 'success' ? 'badge-pill success' : 'badge-pill danger'}`}
          style={{ 
            width: '100%', 
            padding: '10px 14px', 
            borderRadius: '8px', 
            marginBottom: '16px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            fontSize: '0.85rem'
          }}
        >
          {feedback.type === 'success' ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
          <span>{feedback.text}</span>
          <button 
            type="button" 
            onClick={() => setFeedback(null)} 
            style={{ marginLeft: 'auto', background: 'none', border: 'none', color: 'inherit', cursor: 'pointer', padding: 0 }}
          >
            <X size={14} />
          </button>
        </div>
      )}

      <form onSubmit={handleSubmit} className="form-stack">
        {/* Title */}
        <div className="form-group">
          <label htmlFor="notice-title" style={{ fontWeight: 600 }}>Notice Title *</label>
          <input
            id="notice-title"
            type="text"
            placeholder="e.g. Mid-Term Lab Exam Timetable & Submission Guidelines"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            disabled={isSubmitting}
          />
        </div>

        {/* Recipients Mode Selection */}
        <div className="form-group">
          <label style={{ fontWeight: 600 }}>Target Recipients *</label>
          <div 
            className="recipient-mode-selector" 
            style={{ 
              display: 'grid', 
              gridTemplateColumns: 'repeat(2, 1fr)', 
              gap: '10px',
              marginTop: '4px'
            }}
          >
            <button
              type="button"
              className={`btn-secondary ${recipientMode === 'GROUP' ? 'active' : ''}`}
              onClick={() => setRecipientMode('GROUP')}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                padding: '10px 14px',
                background: recipientMode === 'GROUP' ? 'rgba(99, 102, 241, 0.25)' : 'var(--bg-surface)',
                borderColor: recipientMode === 'GROUP' ? 'var(--primary)' : 'var(--border-subtle)',
                color: recipientMode === 'GROUP' ? 'var(--text-primary)' : 'var(--text-secondary)',
                borderRadius: '8px'
              }}
            >
              <Users size={16} /> Whole group (Dept / Year / Div)
            </button>

            <button
              type="button"
              className={`btn-secondary ${recipientMode === 'SPECIFIC' ? 'active' : ''}`}
              onClick={() => setRecipientMode('SPECIFIC')}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                padding: '10px 14px',
                background: recipientMode === 'SPECIFIC' ? 'rgba(99, 102, 241, 0.25)' : 'var(--bg-surface)',
                borderColor: recipientMode === 'SPECIFIC' ? 'var(--primary)' : 'var(--border-subtle)',
                color: recipientMode === 'SPECIFIC' ? 'var(--text-primary)' : 'var(--text-secondary)',
                borderRadius: '8px'
              }}
            >
              <UserCheck size={16} /> Specific students (Direct)
            </button>
          </div>
        </div>

        {/* Mode A: Whole Group Selectors */}
        {recipientMode === 'GROUP' && (
          <div className="form-group animate-fade-in">
            <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Institutional Taxonomy Filter (Select target department, year, and division):
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '10px', marginTop: '4px' }}>
              <div>
                <label style={{ fontSize: '0.75rem', marginBottom: '2px', display: 'block' }}>Department</label>
                <select 
                  id="target-department-select"
                  value={targetDepartment} 
                  onChange={(e) => setTargetDepartment(e.target.value)}
                  style={{ width: '100%' }}
                  disabled={isSubmitting}
                >
                  {ALLOWED_DEPARTMENTS.map(d => (
                    <option key={d} value={d}>{d}</option>
                  ))}
                </select>
              </div>

              <div>
                <label style={{ fontSize: '0.75rem', marginBottom: '2px', display: 'block' }}>Academic Year</label>
                <select 
                  id="target-year-select"
                  value={targetYear} 
                  onChange={(e) => setTargetYear(e.target.value)}
                  style={{ width: '100%' }}
                  disabled={isSubmitting}
                >
                  {ALLOWED_YEARS.map(y => (
                    <option key={y} value={y}>{y}</option>
                  ))}
                </select>
              </div>

              <div>
                <label style={{ fontSize: '0.75rem', marginBottom: '2px', display: 'block' }}>Division</label>
                <select 
                  id="target-division-select"
                  value={targetDivision} 
                  onChange={(e) => setTargetDivision(e.target.value)}
                  style={{ width: '100%' }}
                  disabled={isSubmitting}
                >
                  {ALLOWED_DIVISIONS.map(div => (
                    <option key={div} value={div}>{div}</option>
                  ))}
                </select>
              </div>
            </div>
          </div>
        )}

        {/* Mode B: Specific Students Search & Chips */}
        {recipientMode === 'SPECIFIC' && (
          <div className="form-group animate-fade-in" ref={searchContainerRef}>
            <label htmlFor="student-search-input" style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Search Students by Name or Roll Number:
            </label>
            
            <div style={{ position: 'relative', marginTop: '4px' }}>
              <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                <Search size={16} style={{ position: 'absolute', left: '12px', color: 'var(--text-muted)', pointerEvents: 'none' }} />
                <input
                  id="student-search-input"
                  type="text"
                  placeholder="Type student name or roll number (e.g. Aarav, CS-104)..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  onFocus={() => setIsSearchFocused(true)}
                  style={{ paddingLeft: '36px', paddingRight: isSearching ? '36px' : '12px', width: '100%' }}
                  disabled={isSubmitting}
                />
                {isSearching && (
                  <Loader2 size={16} className="animate-spin text-primary" style={{ position: 'absolute', right: '12px' }} />
                )}
              </div>

              {/* Autocomplete Results Dropdown */}
              {isSearchFocused && searchResults.length > 0 && (
                <div 
                  className="search-results-dropdown glass-card"
                  style={{
                    position: 'absolute',
                    top: '100%',
                    left: 0,
                    right: 0,
                    marginTop: '6px',
                    zIndex: 50,
                    maxHeight: '220px',
                    overflowY: 'auto',
                    border: '1px solid var(--border-medium)',
                    borderRadius: '8px',
                    boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
                    background: 'var(--bg-surface-elevated)'
                  }}
                >
                  {searchResults.map(student => (
                    <div
                      key={student.id}
                      onClick={() => handleSelectStudent(student)}
                      style={{
                        padding: '10px 14px',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        borderBottom: '1px solid var(--border-subtle)',
                        transition: 'background 0.15s ease'
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.background = 'var(--bg-glass-hover)'}
                      onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                    >
                      <div>
                        <div style={{ fontWeight: 600, fontSize: '0.9rem', color: 'var(--text-primary)' }}>
                          {student.name}
                        </div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                          {student.rollNo ? `Roll: ${student.rollNo}` : student.userCode} • {student.department || 'CS'} • {student.division || 'Div A'}
                        </div>
                      </div>
                      <span className="badge-pill info" style={{ fontSize: '0.7rem' }}>+ Add</span>
                    </div>
                  ))}
                </div>
              )}

              {isSearchFocused && searchQuery.trim() && !isSearching && searchResults.length === 0 && (
                <div 
                  style={{
                    position: 'absolute',
                    top: '100%',
                    left: 0,
                    right: 0,
                    marginTop: '6px',
                    zIndex: 50,
                    padding: '12px',
                    textAlign: 'center',
                    fontSize: '0.82rem',
                    color: 'var(--text-muted)',
                    background: 'var(--bg-surface-elevated)',
                    borderRadius: '8px',
                    border: '1px solid var(--border-subtle)'
                  }}
                >
                  No matching students found for "{searchQuery}".
                </div>
              )}
            </div>

            {/* Selected Students Chips */}
            <div style={{ marginTop: '10px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
                <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                  Selected Students ({selectedStudents.length}):
                </span>
                {selectedStudents.length > 0 && (
                  <button
                    type="button"
                    onClick={() => setSelectedStudents([])}
                    style={{ fontSize: '0.72rem', color: 'var(--danger-text)', background: 'none', border: 'none', cursor: 'pointer' }}
                  >
                    Clear All
                  </button>
                )}
              </div>

              {selectedStudents.length === 0 ? (
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontStyle: 'italic', padding: '6px 0' }}>
                  No students selected yet. Use the search bar above to select individual students.
                </div>
              ) : (
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                  {selectedStudents.map(student => (
                    <span 
                      key={student.id} 
                      className="badge-pill accent"
                      style={{ 
                        padding: '5px 10px', 
                        display: 'inline-flex', 
                        alignItems: 'center', 
                        gap: '6px',
                        fontSize: '0.8rem'
                      }}
                    >
                      <span>{student.name}</span>
                      {student.rollNo && (
                        <span style={{ opacity: 0.75, fontSize: '0.72rem' }}>({student.rollNo})</span>
                      )}
                      <button
                        type="button"
                        onClick={() => handleRemoveStudent(student.id)}
                        title={`Remove ${student.name}`}
                        style={{
                          background: 'none',
                          border: 'none',
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          padding: '1px',
                          color: 'inherit',
                          opacity: 0.8
                        }}
                      >
                        <X size={12} />
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {/* Message Body */}
        <div className="form-group">
          <label htmlFor="notice-message" style={{ fontWeight: 600 }}>Message Content *</label>
          <textarea
            id="notice-message"
            rows={4}
            placeholder="Write the full content of the circular or notification..."
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            required
            disabled={isSubmitting}
          />
        </div>

        {/* Optional Deadline */}
        <div className="form-group">
          <label htmlFor="notice-deadline" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Calendar size={14} /> Optional Action Deadline
          </label>
          <input
            id="notice-deadline"
            type="date"
            value={deadline}
            onChange={(e) => setDeadline(e.target.value)}
            disabled={isSubmitting}
            style={{ maxWidth: '240px' }}
          />
        </div>

        {/* Action Button */}
        <div style={{ marginTop: '6px' }}>
          <button
            type="submit"
            className="btn-primary w-full"
            disabled={isSubmitting}
            style={{ padding: '12px' }}
          >
            {isSubmitting ? (
              <>
                <Loader2 size={16} className="animate-spin" /> Publishing Notice...
              </>
            ) : (
              <>
                <Send size={16} /> Publish &amp; Send Notice
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
