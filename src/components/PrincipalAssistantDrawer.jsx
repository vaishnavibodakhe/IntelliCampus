import React, { useState } from 'react';
import { 
  Sparkles, 
  Send, 
  X, 
  Database, 
  AlertCircle, 
  CheckCircle,
  HelpCircle,
  RefreshCw
} from 'lucide-react';
import { api } from '../api/client';

const SUGGESTED_QUERIES = [
  "How many staff members are awaiting approval?",
  "Summarize staff count by department",
  "What is the average student attendance rate?",
  "List published campus notices",
  "How many verifiable credentials have been issued?",
  "Show academic catalog setup"
];

export default function PrincipalAssistantDrawer({ isOpen, onClose }) {
  const [messages, setMessages] = useState([
    {
      id: 1,
      sender: 'bot',
      text: "Welcome, Principal. I am your Live Institutional Assistant. I answer executive questions directly from real-time database records including staffing, pending approvals, student attendance summaries, verifiable documents, notices, and academic structure.",
      dataSources: ["live institutional database"],
      isRefusal: false
    }
  ]);
  const [inputQuestion, setInputQuestion] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  if (!isOpen) return null;

  const handleSendMessage = async (e, directText) => {
    if (e && e.preventDefault) e.preventDefault();
    const query = (directText || inputQuestion || '').trim();
    if (!query) return;

    if (query.length > 500) {
      setErrorMessage("Question exceeds the 500 character limit.");
      return;
    }

    setErrorMessage('');
    const userMsg = {
      id: Date.now(),
      sender: 'user',
      text: query,
      dataSources: [],
      isRefusal: false
    };

    setMessages(prev => [...prev, userMsg]);
    setInputQuestion('');
    setIsTyping(true);

    try {
      const res = await api.queryPrincipalAssistant(query);
      const botResponse = {
        id: Date.now() + 1,
        sender: 'bot',
        text: res.answer || "No response received from institutional data service.",
        dataSources: res.dataSources || [],
        isRefusal: Boolean(res.refusal)
      };
      setMessages(prev => [...prev, botResponse]);
    } catch (err) {
      const fallbackResponse = {
        id: Date.now() + 1,
        sender: 'bot',
        text: err.message || "Failed to query live assistant service. Please check your session and network connection.",
        dataSources: [],
        isRefusal: true
      };
      setMessages(prev => [...prev, fallbackResponse]);
    } finally {
      setIsTyping(false);
    }
  };

  return (
    <div className="chatbot-drawer-container animate-fade-in" style={{ zIndex: 1100 }}>
      <div className="chatbot-header" style={{ borderBottom: '1px solid rgba(255,255,255,0.1)', padding: '1rem 1.25rem' }}>
        <div className="bot-title-wrap" style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <div className="bot-avatar" style={{ background: 'linear-gradient(135deg, #6366f1 0%, #a855f7 100%)', borderRadius: '10px', padding: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Sparkles size={20} className="text-white" />
          </div>
          <div>
            <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 600, color: 'var(--text-primary)' }}>Principal AI Assistant</h3>
            <span style={{ fontSize: '0.75rem', color: '#10b981', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: '#10b981', display: 'inline-block' }}></span>
              Live Database Integration
            </span>
          </div>
        </div>

        <button onClick={onClose} className="drawer-close-btn" style={{ background: 'transparent', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer', fontSize: '1.5rem', lineHeight: 1 }}>
          &times;
        </button>
      </div>

      <div className="chat-messages-container" style={{ flex: 1, overflowY: 'auto', padding: '1rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        {messages.map(msg => (
          <div key={msg.id} className={`chat-bubble-wrap ${msg.sender === 'user' ? 'user-wrap' : 'bot-wrap'}`}>
            <div className={`chat-bubble ${msg.sender === 'user' ? 'bubble-user' : 'bubble-bot'}`}>
              <p className="bubble-text" style={{ margin: 0, whiteSpace: 'pre-wrap', lineHeight: 1.5 }}>{msg.text}</p>

              {/* Data Sources Chips */}
              {msg.dataSources && msg.dataSources.length > 0 && (
                <div className="citations-box" style={{ marginTop: '0.6rem', paddingTop: '0.5rem', borderTop: '1px solid rgba(255,255,255,0.08)' }}>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem' }}>
                    {msg.dataSources.map((ds, i) => (
                      <span key={i} style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '0.72rem', background: 'rgba(99, 102, 241, 0.15)', color: '#818cf8', padding: '2px 8px', borderRadius: '12px' }}>
                        <Database size={10} />
                        {ds}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Refusal Notice */}
              {msg.isRefusal && (
                <div className="refusal-notice" style={{ marginTop: '0.5rem', fontSize: '0.75rem', color: '#f59e0b', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <AlertCircle size={13} /> Read-only institutional safeguard active
                </div>
              )}
            </div>
          </div>
        ))}

        {isTyping && (
          <div className="chat-bubble-wrap bot-wrap">
            <div className="chat-bubble bubble-bot typing-bubble" style={{ display: 'flex', gap: '4px', alignItems: 'center', padding: '0.75rem 1rem' }}>
              <span className="dot-pulse" />
              <span className="dot-pulse" />
              <span className="dot-pulse" />
            </div>
          </div>
        )}
      </div>

      {/* Suggested Quick Queries */}
      <div className="chat-suggestions-bar" style={{ padding: '0.5rem 1rem', display: 'flex', flexWrap: 'wrap', gap: '0.4rem', borderTop: '1px solid rgba(255,255,255,0.06)' }}>
        {SUGGESTED_QUERIES.map((q, idx) => (
          <button
            key={idx}
            type="button"
            onClick={(e) => handleSendMessage(e, q)}
            className="suggest-chip"
            style={{ fontSize: '0.75rem', padding: '4px 10px', borderRadius: '14px', background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)', color: 'var(--text-secondary)', cursor: 'pointer', transition: 'all 0.15s' }}
          >
            {q}
          </button>
        ))}
      </div>

      {/* Input Form with Char Limit Guard */}
      <form onSubmit={handleSendMessage} className="chat-input-form" style={{ padding: '0.75rem 1rem', borderTop: '1px solid rgba(255,255,255,0.1)', display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
        {errorMessage && (
          <div style={{ color: '#ef4444', fontSize: '0.75rem' }}>{errorMessage}</div>
        )}
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <input 
            type="text" 
            placeholder="Ask about staffing, approvals, attendance, notices..."
            value={inputQuestion}
            maxLength={500}
            onChange={e => setInputQuestion(e.target.value)}
            disabled={isTyping}
            style={{ flex: 1, padding: '8px 12px', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.15)', background: 'rgba(0,0,0,0.2)', color: 'var(--text-primary)', fontSize: '0.875rem' }}
          />
          <button 
            type="submit" 
            className="btn-send"
            disabled={isTyping || !inputQuestion.trim()}
            style={{ background: '#6366f1', color: '#fff', border: 'none', padding: '8px 12px', borderRadius: '8px', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
          >
            <Send size={16} />
          </button>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7rem', color: 'var(--text-muted)' }}>
          <span>Answers strictly from live institutional system data</span>
          <span>{inputQuestion.length}/500</span>
        </div>
      </form>
    </div>
  );
}
