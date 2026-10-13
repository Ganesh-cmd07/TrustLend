import { useEffect, useState } from 'react';
import { Award, BadgeCheck, CheckCircle2, Gauge, Info, TrendingUp, Users, Wallet } from "lucide-react";
import { apiFetch } from "../api/axios";
import { formatCurrency } from "../utils/formatCurrency";
import { useAuth } from "../AuthContext";

export default function CreditScoreCard({ onScoreData }) {
  const { token } = useAuth();
  const [scoreData, setScoreData] = useState(null);
  const [history, setHistory] = useState([]);
  const [error, setError] = useState("");
  const [showFactors, setShowFactors] = useState(false);

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

  if (!scoreData) {
    return (
      <div className="glass-panel flex items-center gap-3 text-sm text-slate-400" role={error ? "alert" : "status"}>
        <Gauge className="text-emerald-400 animate-spin" size={19} /> {error || "Loading credit score..."}
      </div>
    );
  }

  const riskTier = String(scoreData.riskTier || "").toLowerCase();
  const creditScore = Number(scoreData.creditScore) || 500;
  const scorePercent = Math.min(100, Math.max(0, ((creditScore - 300) / 550) * 100));

  const tierColor =
    creditScore >= 750
      ? "text-emerald-400"
      : creditScore >= 650
      ? "text-teal-400"
      : creditScore >= 550
      ? "text-amber-400"
      : "text-rose-400";

  return (
    <section className="glass-panel overflow-hidden border-slate-800/90 shadow-xl">
      <div className="mb-5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="icon-tile h-10 w-10">
            <Gauge size={19} />
          </span>
          <div>
            <h3 className="text-base font-semibold">Your credit profile</h3>
            <p className="mt-0.5 text-xs text-slate-500">Transparent rule-based scoring engine</p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => setShowFactors(!showFactors)}
          className="text-xs text-emerald-400 hover:text-emerald-300 flex items-center gap-1 font-medium transition"
        >
          <Info size={13} /> {showFactors ? "Hide formula" : "How it's calculated"}
        </button>
      </div>

      <div className="flex flex-wrap items-center gap-5">
        {/* Animated Conic Score Gauge */}
        <div
          className="relative grid h-32 w-32 shrink-0 place-items-center rounded-full p-[7px] shadow-lg shadow-black/40"
          style={{ background: `conic-gradient(#10b981 ${scorePercent}%, #1e293b ${scorePercent}% 100%)` }}
          aria-label={`Credit score ${creditScore}`}
        >
          <div className="grid h-full w-full place-items-center rounded-full bg-slate-900 text-center">
            <span className={`text-3xl font-extrabold tracking-tight ${tierColor}`}>{creditScore}</span>
            <span className="text-[10px] text-slate-500 -mt-1">out of 850</span>
          </div>
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap gap-2">
            <span className={`status-badge ${riskTier === "low" ? "status-active" : riskTier === "high" ? "status-rejected" : "status-pending"}`}>
              <BadgeCheck size={13} /> {scoreData.riskTier} risk tier
            </span>
            <span className={`status-badge ${scoreData.eligible ? "status-active" : "status-pending"}`}>
              {scoreData.eligible ? "Eligible (550+ threshold met)" : "Score < 550 (₹0 limit)"}
            </span>
          </div>
          <p className="mt-4 flex items-center gap-1.5 text-xs text-slate-400">
            <TrendingUp size={14} className="text-emerald-400" /> Maximum eligible loan limit
          </p>
          <strong className="mt-0.5 block text-2xl font-bold text-slate-100">
            {formatCurrency(scoreData.maxEligibleLoan)}
          </strong>
        </div>
      </div>

      {/* Visual Continuum Progress Meter */}
      <div className="mt-5 rounded-xl border border-slate-800/80 bg-slate-950/40 p-3.5">
        <div className="flex justify-between text-[11px] text-slate-400 font-medium mb-1.5">
          <span className="text-rose-400">300 Ineligible</span>
          <span className="text-amber-400">550 Tier 1 (₹25k)</span>
          <span className="text-teal-400">650 Tier 2 (₹50k)</span>
          <span className="text-emerald-400">750+ Max (₹100k)</span>
        </div>
        <div className="h-2 w-full rounded-full bg-slate-800 overflow-hidden relative">
          <div
            className="h-full rounded-full bg-gradient-to-r from-rose-500 via-amber-400 to-emerald-400 transition-all duration-700"
            style={{ width: `${scorePercent}%` }}
          />
        </div>
      </div>

      {/* Expandable Scoring Formula Breakdown */}
      {showFactors && (
        <div className="mt-5 border-t border-slate-800 pt-4 animate-in fade-in">
          <h4 className="mb-2.5 text-xs font-semibold uppercase tracking-wider text-slate-400">TrustLend Score Factors</h4>
          <div className="grid gap-2 text-xs sm:grid-cols-2">
            <div className="rounded-lg bg-slate-900/80 p-2.5 flex items-center gap-2 border border-slate-800/60">
              <Award size={15} className="text-teal-400 shrink-0" />
              <div>
                <span className="text-slate-200 font-medium">Base Platform Score</span>
                <span className="text-[11px] text-slate-500 block">500 points baseline</span>
              </div>
            </div>
            <div className="rounded-lg bg-slate-900/80 p-2.5 flex items-center gap-2 border border-slate-800/60">
              <Users size={15} className="text-emerald-400 shrink-0" />
              <div>
                <span className="text-slate-200 font-medium">Active SHG Group</span>
                <span className="text-[11px] text-emerald-400 block">+50 points for membership</span>
              </div>
            </div>
            <div className="rounded-lg bg-slate-900/80 p-2.5 flex items-center gap-2 border border-slate-800/60">
              <Wallet size={15} className="text-amber-400 shrink-0" />
              <div>
                <span className="text-slate-200 font-medium">Simulated Wallet Balance</span>
                <span className="text-[11px] text-slate-400 block">+25 pts (₹500+) · +50 pts (₹1,000+)</span>
              </div>
            </div>
            <div className="rounded-lg bg-slate-900/80 p-2.5 flex items-center gap-2 border border-slate-800/60">
              <CheckCircle2 size={15} className="text-teal-400 shrink-0" />
              <div>
                <span className="text-slate-200 font-medium">Repaid Loan History</span>
                <span className="text-[11px] text-teal-400 block">+50 points per fully repaid loan</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {history.length > 0 && (
        <div className="mt-5 border-t border-slate-800 pt-4">
          <h4 className="mb-3 text-sm font-semibold">Recent score history</h4>
          <ol className="grid gap-2">
            {history.slice(0, 4).map((snapshot) => (
              <li className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-slate-950/40 px-3 py-2 text-xs" key={snapshot.id}>
                <span className="font-semibold text-slate-200">{snapshot.creditScore} · {snapshot.riskTier} risk</span>
                <span className="text-slate-400">Limit {formatCurrency(snapshot.maxEligibleLoan)}</span>
                <time className="text-slate-500" dateTime={snapshot.recordedAt}>{new Date(snapshot.recordedAt).toLocaleString()}</time>
              </li>
            ))}
          </ol>
        </div>
      )}
    </section>
  );
}
