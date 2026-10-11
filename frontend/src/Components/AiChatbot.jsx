import { useState } from 'react';
import { Bot, MessageCircle, Send, X } from "lucide-react";
import { apiFetch } from "../api/axios";

export default function AiChatbot() {
  const [isOpen, setIsOpen] = useState(false);
  const [prompt, setPrompt] = useState('');
  const [messages, setMessages] = useState([
    { sender: 'ai', text: 'Hello! How can I help you with TrustLend loans, group lending, or credit scores today?' }
  ]);

  const handleSend = async (e) => {
    e.preventDefault();
    if (!prompt.trim()) return;

    const newMsgs = [...messages, { sender: 'user', text: prompt }];
    setMessages(newMsgs);
    setPrompt('');

    try {
      const token = localStorage.getItem('trustlend_jwt')
        || sessionStorage.getItem('trustlend_jwt');
      const res = await apiFetch('/api/v1/ai/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({ prompt })
      });
      const data = await res.json();
      setMessages([...newMsgs, { sender: 'ai', text: data.reply }]);
    } catch {
      setMessages([...newMsgs, { sender: 'ai', text: 'Sorry, I am unable to connect right now.' }]);
    }
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
        <section className="fixed bottom-5 right-5 z-40 flex h-[min(610px,calc(100vh-40px))] w-[min(390px,calc(100vw-40px))] flex-col overflow-hidden rounded-2xl border border-slate-700/80 bg-slate-950 shadow-2xl shadow-black/60" aria-label="TrustLend AI assistant">
          <div className="flex items-center justify-between border-b border-white/10 bg-gradient-to-r from-emerald-600 to-teal-700 px-5 py-4">
            <span className="flex items-center gap-2 text-sm font-semibold text-white"><span className="grid h-8 w-8 place-items-center rounded-full bg-white/15"><Bot size={17} /></span> TrustLend assistant</span>
            <button className="grid h-8 w-8 place-items-center rounded-lg text-white/80 transition hover:bg-white/10 hover:text-white" type="button" onClick={() => setIsOpen(false)} aria-label="Close assistant"><X size={17} /></button>
          </div>

          <div className="flex flex-1 flex-col gap-3 overflow-y-auto bg-slate-950 p-4">
            {messages.map((m, idx) => (
              <div key={idx} className={`max-w-[85%] whitespace-pre-wrap rounded-2xl px-4 py-3 text-sm leading-relaxed ${m.sender === 'user' ? 'ml-auto rounded-br-md bg-gradient-to-br from-emerald-600 to-teal-700 text-white' : 'mr-auto rounded-bl-md border border-slate-800 bg-slate-900 text-slate-200'}`}>
                {m.text}
              </div>
            ))}
          </div>

          <form onSubmit={handleSend} className="flex items-center gap-2 border-t border-slate-800 bg-slate-900 p-3">
            <input
              className="field min-h-10 py-2.5"
              type="text"
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="Ask a question..."
            />
            <button className="primary-button h-10 min-h-10 w-10 shrink-0 p-0" type="submit" aria-label="Send message"><Send size={16} /></button>
          </form>
        </section>
      )}
    </>
  );
}