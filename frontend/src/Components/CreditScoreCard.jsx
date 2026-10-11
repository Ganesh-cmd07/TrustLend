import { useEffect, useState } from 'react';
import { BadgeCheck, Gauge, TrendingUp } from "lucide-react";
import { apiFetch } from "../api/axios";
import { formatCurrency } from "../utils/formatCurrency";
import { useAuth } from "../AuthContext";

export default function CreditScoreCard({ onScoreData }) {
  const { token } = useAuth();
  const [scoreData, setScoreData] = useState(null);
  const [history, setHistory] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    async function loadScore() {
      try {
        const res = await apiFetch('/api/v1/credit-score/me', {
          headers: { Authorization: `Bearer ${token}` }, signal: controller.signal
        });
        if (!res.ok) throw new Error(`Unable to load credit score (${res.status})`);
        const data = await res.json();
        setScoreData(data);
        onScoreData?.(data);
        const historyResponse = await apiFetch('/api/v1/credit-score/history', {
          headers: { Authorization: `Bearer ${token}` }, signal: controller.signal
        });
        if (historyResponse.ok) setHistory(await historyResponse.json());
      } catch (requestError) {
        if (requestError.name !== "AbortError") setError("Unable to load credit score and loan limit.");
      }
    }
    loadScore();
    return () => controller.abort();
  }, [token, onScoreData]);

  if (!scoreData) return <div className="glass-panel flex items-center gap-3 text-sm text-slate-400" role={error ? "alert" : "status"}><Gauge className="text-emerald-400" size={19} /> {error || "Loading credit score..."}</div>;
  const riskTier = String(scoreData.riskTier || "").toLowerCase();
  const creditScore = scoreData.creditScore;
  const scorePercent = Math.min(100, Math.max(0, ((Number(creditScore) - 300) / 550) * 100));

  return (
    <section className="glass-panel overflow-hidden">
      <div className="mb-5 flex items-center gap-3"><span className="icon-tile h-10 w-10"><Gauge size={19} /></span><div><h3 className="text-base font-semibold">Your credit score</h3><p className="mt-1 text-xs text-slate-500">A snapshot of your credit profile</p></div></div>
      <div className="flex flex-wrap items-center gap-5">
        <div className="relative grid h-32 w-32 shrink-0 place-items-center rounded-full p-[7px]" style={{ background: `conic-gradient(#10b981 ${scorePercent}%, #1e293b ${scorePercent}% 100%)` }} aria-label={`Credit score ${creditScore}`}>
          <div className="grid h-full w-full place-items-center rounded-full bg-slate-900 text-center"><span className="text-3xl font-bold tracking-tight">{creditScore}</span><span className="text-[10px] text-slate-500">out of 850</span></div>
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap gap-2">
            <span className={`status-badge ${riskTier === "low" ? "status-active" : riskTier === "high" ? "status-rejected" : "status-pending"}`}><BadgeCheck size={13} /> {scoreData.riskTier} risk tier</span>
            <span className={`status-badge ${scoreData.eligible ? "status-active" : "status-pending"}`}>
              {scoreData.eligible ? "Eligible to apply" : "Below eligibility threshold"}
            </span>
          </div>
          <p className="mt-4 flex items-center gap-2 text-xs text-slate-400"><TrendingUp size={15} className="text-emerald-400" /> Maximum eligible loan amount</p>
          <strong className="mt-1 block text-xl font-semibold text-slate-100">{formatCurrency(scoreData.maxEligibleLoan)}</strong>
        </div>
      </div>
      {history.length > 0 && <div className="mt-6 border-t border-slate-800 pt-4">
        <h4 className="mb-3 text-sm font-semibold">Recent score history</h4>
        <ol className="grid gap-2">{history.slice(0, 5).map((snapshot) => <li className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-slate-950/40 px-3 py-2 text-xs" key={snapshot.id}>
          <span className="font-semibold text-slate-200">{snapshot.creditScore} · {snapshot.riskTier} risk</span>
          <span className="text-slate-400">Limit {formatCurrency(snapshot.maxEligibleLoan)}</span>
          <time className="text-slate-500" dateTime={snapshot.recordedAt}>{new Date(snapshot.recordedAt).toLocaleString()}</time>
        </li>)}</ol>
      </div>}
    </section>
  );
}
