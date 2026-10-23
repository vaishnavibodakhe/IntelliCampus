import React, { useState } from 'react';
import { 
  Sparkles, 
  Send, 
  X, 
  ThumbsUp, 
  ThumbsDown, 
  FileText, 
  AlertCircle, 
  Bot, 
  User, 
  HelpCircle,
  ExternalLink,
  Languages
} from 'lucide-react';

import { api } from '../api/client';

export default function ChatbotDrawer({ isOpen, onClose, knowledgeDocs = [] }) {
  const [messages, setMessages] = useState([
    {
      id: 1,
      sender: 'bot',
      text: "Hello! I am the IntelliCampus Official Knowledge Assistant. I answer questions strictly from approved college circulars, exam regulations, and syllabus handbooks with exact source citations.",
      citations: [],
      isRefusal: false,
      rated: null
    }
  ]);
  const [inputQuestion, setInputQuestion] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [language, setLanguage] = useState('English');

  if (!isOpen) return null;

  // RAG Question Handling using real backend knowledge retrieval
  const handleSendMessage = async (e) => {
    e.preventDefault();
    if (!inputQuestion.trim()) return;

    const query = inputQuestion.trim();
    const userMsg = {
      id: Date.now(),
      sender: 'user',
      text: query,
      citations: [],
      isRefusal: false,
      rated: null
    };

    setMessages(prev => [...prev, userMsg]);
    setInputQuestion('');
    setIsTyping(true);

    try {
      const res = await api.queryKnowledge(query);
      const isRefusal = !res.citations || res.citations.length === 0 || res.confidenceScore < 0.15;
      const formattedCitations = (res.citations || []).map(cit => ({
        docName: cit,
        similarity: res.confidenceScore || 0.85
      }));

      const botResponse = {
        id: Date.now() + 1,
        sender: 'bot',
        text: res.answer || "I could not locate an answer for this question in the official college circulars, ordinances, or handbooks.",
        citations: formattedCitations,
        isRefusal,
        rated: null
      };
      setMessages(prev => [...prev, botResponse]);
    } catch {
      const fallbackResponse = {
        id: Date.now() + 1,
        sender: 'bot',
        text: "I am unable to reach the knowledge vector index at this moment. Please ensure the backend server is reachable.",
        citations: [],
        isRefusal: true,
        rated: null
      };
      setMessages(prev => [...prev, fallbackResponse]);
    } finally {
      setIsTyping(false);
    }
  };

  const handleRate = (msgId, isHelpful) => {
    setMessages(messages.map(m => {
      if (m.id === msgId) {
        return { ...m, rated: isHelpful };
      }
      return m;
    }));
  };

  return (
    <div className="chatbot-drawer-container animate-fade-in">
      <div className="chatbot-header">
        <div className="bot-title-wrap">
          <div className="bot-avatar">
            <Sparkles size={18} className="text-white" />
          </div>
          <div>
            <h3>Campus Document AI</h3>
            <span className="text-xs text-secondary">
              RAG Grounded {knowledgeDocs.length > 0 ? `(${knowledgeDocs.length} Circulars Indexed)` : 'in College Circulars'}
            </span>
          </div>
        </div>

        <div className="chatbot-header-actions">
          {/* Language Selector (BOT-05) */}
          <select 
            value={language} 
            onChange={e => setLanguage(e.target.value)}
            className="lang-select"
          >
            <option value="English">EN</option>
            <option value="Hindi">HI</option>
            <option value="Marathi">MR</option>
          </select>
          <button onClick={onClose} className="drawer-close-btn">&times;</button>
        </div>
      </div>

      <div className="chat-messages-container">
        {messages.map(msg => (
          <div key={msg.id} className={`chat-bubble-wrap ${msg.sender === 'user' ? 'user-wrap' : 'bot-wrap'}`}>
            <div className={`chat-bubble ${msg.sender === 'user' ? 'bubble-user' : 'bubble-bot'}`}>
              <p className="bubble-text">{msg.text}</p>

              {/* Citations / Source Chips */}
              {msg.citations && msg.citations.length > 0 && (
                <div className="citations-box">
                  <span className="citation-title">Grounded Source Citations:</span>
                  {msg.citations.map((c, i) => (
                    <div key={i} className="citation-chip">
                      <FileText size={12} />
                      <span>{c.docName} (Page {c.page})</span>
                      <span className="badge-similarity">{(c.similarity * 100).toFixed(0)}% Match</span>
                    </div>
                  ))}
                </div>
              )}

              {/* Safe Refusal Tag */}
              {msg.isRefusal && (
                <div className="refusal-notice">
                  <AlertCircle size={14} /> Official college citation not found. Safe refusal triggered.
                </div>
              )}

              {/* Rating Feedback (BOT-06) */}
              {msg.sender === 'bot' && msg.id !== 1 && (
                <div className="rating-row">
                  <span className="text-xs text-muted">Was this helpful?</span>
                  <button 
                    onClick={() => handleRate(msg.id, true)} 
                    className={`rate-btn ${msg.rated === true ? 'active-good' : ''}`}
                  >
                    <ThumbsUp size={12} />
                  </button>
                  <button 
                    onClick={() => handleRate(msg.id, false)} 
                    className={`rate-btn ${msg.rated === false ? 'active-bad' : ''}`}
                  >
                    <ThumbsDown size={12} />
                  </button>
                </div>
              )}
            </div>
          </div>
        ))}

        {isTyping && (
          <div className="chat-bubble-wrap bot-wrap">
            <div className="chat-bubble bubble-bot typing-bubble">
              <span className="dot-pulse" />
              <span className="dot-pulse" />
              <span className="dot-pulse" />
            </div>
          </div>
        )}
      </div>

      {/* Suggested Query Buttons */}
      <div className="chat-suggestions-bar">
        <button 
          onClick={() => setInputQuestion("What is the last date for the backlog exam form?")}
          className="suggest-chip"
        >
          Backlog exam date?
        </button>
        <button 
          onClick={() => setInputQuestion("What is the minimum attendance before detention?")}
          className="suggest-chip"
        >
          Minimum attendance?
        </button>
        <button 
          onClick={() => setInputQuestion("What movies are playing on campus?")}
          className="suggest-chip"
        >
          Test out-of-scope refusal
        </button>
      </div>

      {/* Input Form */}
      <form onSubmit={handleSendMessage} className="chat-input-form">
        <input 
          type="text" 
          placeholder="Ask a question from college regulations..."
          value={inputQuestion}
          onChange={e => setInputQuestion(e.target.value)}
        />
        <button type="submit" className="btn-send">
          <Send size={16} />
        </button>
      </form>
    </div>
  );
}
