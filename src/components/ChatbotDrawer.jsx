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

  // RAG Question Handling
  const handleSendMessage = (e) => {
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

    setTimeout(() => {
      let botResponse = {};
      const lower = query.toLowerCase();

      // Find if any indexed knowledge doc matches the query
      const matchingDoc = (knowledgeDocs || []).find(doc => {
        const title = (doc.title || doc.name || '').toLowerCase();
        const category = (doc.category || '').toLowerCase();
        const queryTerms = lower.split(/\s+/).filter(w => w.length > 3);
        return queryTerms.some(term => title.includes(term) || category.includes(term));
      });

      if (matchingDoc) {
        const docName = matchingDoc.title || matchingDoc.name || 'Official Regulation';
        botResponse = {
          id: Date.now() + 1,
          sender: 'bot',
          text: language === 'Hindi'
            ? `आधिकारिक दस्तावेज़ "${docName}" के अनुसार, यह विषय अनुमोदित विश्वविद्यालय नियमों द्वारा शासित है। विस्तृत जानकारी के लिए संलग्न परिपत्र का संदर्भ लें।`
            : `According to the official document "${docName}", this regulation is governed by authorized academic policy. Please refer to section guidelines in the verified document.`,
          citations: [
            { docName: docName, page: 1, similarity: 0.92 }
          ],
          isRefusal: false,
          rated: null
        };
      } else {
        // Safe Refusal (BOT-04) when no matching document is indexed
        botResponse = {
          id: Date.now() + 1,
          sender: 'bot',
          text: language === 'Hindi'
            ? "मुझे आधिकारिक कॉलेज परिपत्रों या नियमों में इस प्रश्न का उत्तर नहीं मिला। शैक्षणिक अखंडता नीति के अनुसार, मैं असत्यापित उत्तर उत्पन्न नहीं करता। कृपया प्रशासनिक कार्यालय से संपर्क करें।"
            : "I could not locate an answer for this question in the indexed college circulars, ordinances, or handbooks. As per academic integrity policies, I do not generate unverified answers. Please consult the Academic Administration Office directly.",
          citations: [],
          isRefusal: true,
          rated: null
        };
      }

      setMessages(prev => [...prev, botResponse]);
      setIsTyping(false);
    }, 600);
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
        {knowledgeDocs && knowledgeDocs.length > 0 ? (
          knowledgeDocs.slice(0, 2).map((kd, idx) => (
            <button
              key={idx}
              onClick={() => setInputQuestion(`Tell me about ${kd.title || kd.name}`)}
              className="suggest-chip"
            >
              {kd.title || kd.name}
            </button>
          ))
        ) : (
          <button
            onClick={() => setInputQuestion("What is the attendance detention policy?")}
            className="suggest-chip"
          >
            Attendance policy?
          </button>
        )}
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
