import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../AuthContext";
import { apiFetch } from "../api/axios";
import LoanCard from "./LoanCard";
import { ArrowUpRight, CalendarClock, CreditCard, Plus, WalletCards } from "lucide-react";
import CreditScoreCard from "../Components/CreditScoreCard";
import { formatCurrency } from "../utils/formatCurrency";

const LOANS_URL = "/api/v1/loans";

async function fetchMyLoans(token, signal) {
  const response = await apiFetch(`${LOANS_URL}/my-loans`, {
    headers: { Authorization: `Bearer ${token}` },
    signal
  });
  const loans = await response.json().catch(() => []);
  if (!response.ok) {
    throw new Error(loans.message || `Request failed (${response.status})`);
  }
  return loans;
}

function BorrowerDashboard() {
  const { token, user } = useAuth();
  const [loans, setLoans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [workingLoanId, setWorkingLoanId] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({ amount: "", interestRate: "", termMonths: "6" });
  const [maxLoanLimit, setMaxLoanLimit] = useState(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const handleCreditScoreData = useCallback((scoreData) => {
    const limit = Number(scoreData.maxEligibleLoan);
    setMaxLoanLimit(Number.isFinite(limit) ? limit : null);
  }, []);

  const refreshLoans = useCallback(async () => {
    setLoans(await fetchMyLoans(token));
  }, [token]);

  useEffect(() => {
    const controller = new AbortController();
    async function loadLoans() {
      try {
        setLoans(await fetchMyLoans(token, controller.signal));
      } catch (requestError) {
        if (requestError.name !== "AbortError") {
          setError(requestError.message || "Unable to load loans");
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }
    loadLoans();
    return () => controller.abort();
  }, [token]);

  function updateForm(event) {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
    if (name === "amount" && (maxLoanLimit === null || Number(value) <= maxLoanLimit)) {
      setError("");
    }
  }

  async function applyForLoan(event) {
    event.preventDefault();
    setError("");
    setMessage("");
    const requestedAmount = Number(form.amount);
    if (maxLoanLimit === null) {
      setError("Your credit limit is still loading. Please wait and try again.");
      return;
    }
    if (requestedAmount > maxLoanLimit) {
      setError(`The requested amount exceeds your maximum eligible loan of ${formatCurrency(maxLoanLimit)}.`);
      return;
    }
    setSubmitting(true);
    try {
      const response = await apiFetch(`${LOANS_URL}/apply`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          amount: form.amount,
          interestRate: form.interestRate,
          termMonths: Number(form.termMonths)
        })
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(result.message || "Unable to submit loan application");
      }
      setModalOpen(false);
      setForm({ amount: "", interestRate: "", termMonths: "6" });
      setMessage("Loan application submitted to the lender marketplace.");
      await refreshLoans();
    } catch (requestError) {
      setError(requestError.message || "Unable to submit loan application");
    } finally {
      setSubmitting(false);
    }
  }

  async function repayLoan(loanId) {
    setWorkingLoanId(loanId);
    setError("");
    setMessage("");
    try {
      const response = await apiFetch(`${LOANS_URL}/${loanId}/repay`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` }
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(result.message || "Unable to pay installment");
      }
      setMessage("Installment paid from your wallet.");
      await refreshLoans();
    } catch (requestError) {
      setError(requestError.message || "Unable to pay installment");
    } finally {
      setWorkingLoanId(null);
    }
  }

  const borrowerLoans = loans.filter((loan) => loan.borrowerId === user?.id);
  const applicationLoans = borrowerLoans.filter((loan) => ["SUBMITTED", "UNDER_REVIEW", "APPROVED", "PENDING", "REJECTED"].includes(loan.status));
  const applicationsInProgress = applicationLoans.filter((loan) => ["SUBMITTED", "UNDER_REVIEW", "APPROVED", "PENDING"].includes(loan.status));
  const activeLoans = borrowerLoans.filter((loan) => ["FUNDED", "ACTIVE"].includes(loan.status));
  const nextRepayment = activeLoans
    .flatMap((loan) => loan.repayments
      .filter((repayment) => ["PENDING", "OVERDUE"].includes(repayment.status))
      .map((repayment) => ({ ...repayment, loanId: loan.id })))
    .sort((first, second) => first.dueDate.localeCompare(second.dueDate))[0];

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-5 pb-16 pt-32 text-slate-100 sm:px-8">
      <div className="flex flex-col justify-between gap-5 sm:flex-row sm:items-center">
        <div>
          <p className="mb-2 text-xs font-bold uppercase tracking-[.18em] text-emerald-400">Your borrowing overview</p>
          <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Borrower dashboard</h1>
          <p className="mt-2 text-sm text-slate-400">Apply for a loan and manage your funded repayment schedules.</p>
        </div>
        <button className="primary-button" type="button" disabled={maxLoanLimit === 0} onClick={() => setModalOpen(true)}><Plus size={17} /> Apply for a loan</button>
      </div>
      <section className="my-8 grid gap-4 md:grid-cols-3" aria-label="Loan account summary">
        <article className="glass-panel flex items-start gap-4"><span className="icon-tile"><WalletCards /></span><div><span className="text-xs font-semibold text-slate-400">Active loans</span><strong className="mt-2 block text-3xl font-bold tracking-tight">{activeLoans.length}</strong><span className="mt-1 block text-xs text-slate-500">Loans currently being repaid</span></div></article>
        <article className="glass-panel flex items-start gap-4"><span className="icon-tile"><CreditCard /></span><div><span className="text-xs font-semibold text-slate-400">Applications in progress</span><strong className="mt-2 block text-3xl font-bold tracking-tight">{applicationsInProgress.length}</strong><span className="mt-1 block text-xs text-slate-500">Admin review or lender funding</span></div></article>
        <article className="glass-panel flex items-start gap-4"><span className="icon-tile"><CalendarClock /></span><div><span className="text-xs font-semibold text-slate-400">Next repayment</span><strong className="mt-2 block text-3xl font-bold tracking-tight">{nextRepayment ? formatCurrency(nextRepayment.amount) : "—"}</strong><span className="mt-1 block text-xs text-slate-500">{nextRepayment ? `Due ${nextRepayment.dueDate}` : "No repayments scheduled"}</span></div></article>
      </section>
      <div className="mb-6 max-w-xl">
        <CreditScoreCard onScoreData={handleCreditScoreData} />
      </div>
      {error && <p className="mb-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-300" role="alert">{error}</p>}
      {message && <p className="mb-4 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-300" role="status">{message}</p>}
      <section className="glass-panel mb-6">
        <div className="mb-5 flex items-center justify-between"><div><h2 className="text-lg font-semibold">Active loans</h2><p className="mt-1 text-xs text-slate-500">Your current repayment plans</p></div><span className="status-badge status-active">{activeLoans.length} active</span></div>
        {loading ? <p className="text-sm text-slate-400" role="status">Loading loans...</p> : activeLoans.length === 0
          ? <p className="rounded-xl border border-dashed border-slate-700 p-7 text-center text-sm text-slate-400">You do not have any active loans.</p>
          : activeLoans.map((loan) => {
            const nextInstallment = loan.repayments.find((repayment) =>
              ["PENDING", "OVERDUE"].includes(repayment.status));
            return (
              <LoanCard
                key={loan.id}
                loan={loan}
                actionLabel={nextInstallment
                  ? `${workingLoanId === loan.id ? "Paying..." : "Pay next installment"} (${formatCurrency(nextInstallment.amount)})`
                  : null}
                actionDisabled={workingLoanId !== null}
                onAction={() => repayLoan(loan.id)}
              />
            );
          })}
      </section>
      <section className="glass-panel">
        <div className="mb-5 flex items-center gap-3"><span className="icon-tile h-10 w-10"><ArrowUpRight size={19} /></span><div><h2 className="text-lg font-semibold">My applications</h2><p className="mt-1 text-xs text-slate-500">Track application decisions and lender funding</p></div></div>
        {loading ? null : applicationLoans.length === 0
          ? <p className="rounded-xl border border-dashed border-slate-700 p-7 text-center text-sm text-slate-400">No loan applications yet.</p>
          : applicationLoans.map((loan) => <LoanCard key={loan.id} loan={loan} />)}
      </section>
      {modalOpen && (
        <div className="fixed inset-0 z-40 grid place-items-center bg-slate-950/80 p-5 backdrop-blur-sm">
          <section className="glass-panel w-full max-w-lg" role="dialog" aria-modal="true" aria-labelledby="loan-form-title">
            <h2 className="mb-5 text-xl font-semibold" id="loan-form-title">Loan application</h2>
            <form className="grid gap-3" onSubmit={applyForLoan}>
              <div className="flex items-center justify-between gap-3"><label className="text-xs font-semibold text-slate-300" htmlFor="loan-amount">Amount</label><span className="text-xs font-semibold text-emerald-400">{maxLoanLimit === null ? "Max limit: loading…" : `Max limit: ${formatCurrency(maxLoanLimit)}`}</span></div>
              <input className={`field ${maxLoanLimit !== null && Number(form.amount) > maxLoanLimit ? "border-rose-500 focus:border-rose-500 focus:ring-rose-500" : ""}`} id="loan-amount" name="amount" type="number" min="0.01" max={maxLoanLimit ?? undefined} step="0.01" required value={form.amount} onChange={updateForm} aria-describedby="loan-amount-hint" aria-invalid={maxLoanLimit !== null && Number(form.amount) > maxLoanLimit} />
              <p className={`text-xs ${maxLoanLimit !== null && Number(form.amount) > maxLoanLimit ? "text-rose-300" : "text-slate-500"}`} id="loan-amount-hint">{maxLoanLimit !== null && Number(form.amount) > maxLoanLimit ? "Amount exceeds your maximum eligible limit." : "Your current score determines your maximum loan amount."}</p>
              <label className="text-xs font-semibold text-slate-300" htmlFor="loan-rate">Annual interest rate (%)</label>
              <input className="field" id="loan-rate" name="interestRate" type="number" min="0" max="100" step="0.01" required value={form.interestRate} onChange={updateForm} />
              <label className="text-xs font-semibold text-slate-300" htmlFor="loan-term">Term (months)</label>
              <input className="field" id="loan-term" name="termMonths" type="number" min="1" max="120" step="1" required value={form.termMonths} onChange={updateForm} />
              <div className="mt-3 flex flex-wrap gap-3">
                <button className="primary-button" type="submit" disabled={submitting || maxLoanLimit === null || Number(form.amount) > maxLoanLimit}>{submitting ? "Submitting..." : "Submit application"}</button>
                <button className="secondary-button" type="button" disabled={submitting} onClick={() => setModalOpen(false)}>Cancel</button>
              </div>
            </form>
          </section>
        </div>
      )}
    </main>
  );
}

export default BorrowerDashboard;
