import { useEffect, useRef, useState } from 'react';
import { Bot, MessageCircle, Send, Sparkles, X } from "lucide-react";
import { apiFetch } from "../api/axios";

const QUICK_PROMPTS = [
  "What is the status of my loan?",
  "How is credit score calculated?",
  "How does SHG group lending work?",
  "What are the loan eligibility limits?"
];

export default function AiChatbot() {
  const [isOpen, setIsOpen] = useState(false);
  const [prompt, setPrompt] = useState('');
  const [isThinking, setIsThinking] = useState(false);
  const [messages, setMessages] = useState([
    {
      sender: 'ai',
      text: 'Hello! I am your TrustLend AI Assistant. Ask me about micro-loan policies, SHG group lending rules, credit scoring formulas, or check your personal loan status.',
      sources: []
    }
  ]);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isThinking, isOpen]);

  const sendQuery = async (queryText) => {
    const trimmed = queryText.trim();
    if (!trimmed || isThinking) return;

    const newMsgs = [...messages, { sender: 'user', text: trimmed }];
    setMessages(newMsgs);
    setPrompt('');
    setIsThinking(true);

    try {
      const token = localStorage.getItem('trustlend_jwt')
        || sessionStorage.getItem('trustlend_jwt');
      const res = await apiFetch('/api/v1/ai/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({ prompt: trimmed })
      });
      const data = await res.json();
      setMessages([
        ...newMsgs,
        {
          sender: 'ai',
          text: data.reply || 'No response returned from the assistant.',
          sources: Array.isArray(data.sources) ? data.sources : []
        }
      ]);
    } catch {
      setMessages([
        ...newMsgs,
        {
          sender: 'ai',
          text: 'Sorry, I am unable to connect to the assistant service right now.',
          sources: []
        }
      ]);
    } finally {
      setIsThinking(false);
    }
  };

  const handleSend = (e) => {
    e.preventDefault();
    sendQuery(prompt);
  };

  return (
    <>
      {!isOpen && (
        <button
          type="button"
          onClick={() => setIsOpen(true)}
          className="fixed bottom-5 right-5 z-40 inline-flex items-center gap-2 rounded-full border border-emerald-400/30 bg-gradient-to-r from-emerald-500 to-teal-600 px-5 py-3 text-sm font-semibold text-white shadow-xl shadow-emerald-950/50 transition hover:-translate-y-0.5 hover:shadow-emerald-500/20"
        >
          <MessageCircle size={18} /> AI assistant
        </button>
      )}

      {isOpen && (
        <section
          className="fixed bottom-5 right-5 z-40 flex h-[min(620px,calc(100vh-40px))] w-[min(410px,calc(100vw-40px))] flex-col overflow-hidden rounded-2xl border border-slate-700/80 bg-slate-950 shadow-2xl shadow-black/60"
          aria-label="TrustLend AI assistant"
        >
          <div className="flex items-center justify-between border-b border-white/10 bg-gradient-to-r from-emerald-600 to-teal-700 px-5 py-4">
            <span className="flex items-center gap-2 text-sm font-semibold text-white">
              <span className="grid h-8 w-8 place-items-center rounded-full bg-white/15">
                <Bot size={17} />
              </span>
              TrustLend RAG Assistant
            </span>
            <button
              className="grid h-8 w-8 place-items-center rounded-lg text-white/80 transition hover:bg-white/10 hover:text-white"
              type="button"
              onClick={() => setIsOpen(false)}
              aria-label="Close assistant"
            >
              <X size={17} />
            </button>
          </div>

          <div className="flex flex-1 flex-col gap-3 overflow-y-auto bg-slate-950 p-4">
            {messages.map((m, idx) => (
              <div
                key={idx}
                className={`max-w-[90%] whitespace-pre-wrap rounded-2xl px-4 py-3 text-sm leading-relaxed ${
                  m.sender === 'user'
                    ? 'ml-auto rounded-br-md bg-gradient-to-br from-emerald-600 to-teal-700 text-white'
                    : 'mr-auto rounded-bl-md border border-slate-800 bg-slate-900 text-slate-200'
                }`}
              >
                <div>{m.text}</div>
                {m.sources && m.sources.length > 0 && (
                  <div className="mt-2.5 flex flex-wrap items-center gap-1.5 border-t border-slate-800/80 pt-2 text-[11px] text-slate-400">
                    <span className="font-semibold text-slate-400">Citations:</span>
                    {m.sources.map((src, sIdx) => (
                      <span
                        key={sIdx}
                        className="inline-flex items-center rounded-md border border-emerald-500/30 bg-emerald-950/40 px-2 py-0.5 text-[10px] font-medium text-emerald-300"
                      >
                        {src}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            ))}

            {isThinking && (
              <div className="mr-auto flex items-center gap-2 rounded-2xl rounded-bl-md border border-slate-800 bg-slate-900 px-4 py-2.5 text-xs text-emerald-300">
                <Sparkles size={14} className="animate-spin text-emerald-400" />
                <span>Retrieving policy context...</span>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          <div className="border-t border-slate-850 bg-slate-950/80 px-3 pt-2">
            <div className="flex gap-1.5 overflow-x-auto pb-1 text-xs no-scrollbar">
              {QUICK_PROMPTS.map((qp, qIdx) => (
                <button
                  key={qIdx}
                  type="button"
                  onClick={() => sendQuery(qp)}
                  disabled={isThinking}
                  className="whitespace-nowrap rounded-full border border-slate-800 bg-slate-900/90 px-2.5 py-1 text-[11px] text-slate-300 transition hover:border-emerald-500/40 hover:text-emerald-300 disabled:opacity-50"
                >
                  {qp}
                </button>
              ))}
            </div>
          </div>

          <form onSubmit={handleSend} className="flex items-center gap-2 border-t border-slate-800 bg-slate-900 p-3">
            <input
              className="field min-h-10 py-2.5"
              type="text"
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="Ask loan rules, scoring, or status..."
              disabled={isThinking}
            />
            <button
              className="primary-button h-10 min-h-10 w-10 shrink-0 p-0"
              type="submit"
              disabled={isThinking || !prompt.trim()}
              aria-label="Send message"
            >
              <Send size={16} />
            </button>
          </form>
        </section>
      )}
    </>
  );
}