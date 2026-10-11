import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../AuthContext";
import { apiFetch } from "../api/axios";
import LoanCard from "./LoanCard";
import { Activity, BriefcaseBusiness, CircleDollarSign, HandCoins, Users, WalletCards } from "lucide-react";
import { formatCurrency } from "../utils/formatCurrency";

const LOANS_URL = "/api/v1/loans";
const WALLET_URL = "/api/v1/wallet/me";

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

async function fetchMarketplace(token, signal) {
  const response = await apiFetch(`${LOANS_URL}/marketplace`, {
    headers: { Authorization: `Bearer ${token}` },
    signal
  });
  const loans = await response.json().catch(() => []);
  if (!response.ok) {
    throw new Error(loans.message || `Request failed (${response.status})`);
  }
  return loans;
}

async function fetchWallet(token, signal) {
  const response = await apiFetch(WALLET_URL, {
    headers: { Authorization: `Bearer ${token}` },
    signal
  });
  const wallet = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(wallet.message || `Request failed (${response.status})`);
  }
  return wallet;
}

function transactionLabel(type) {
  return ({
    LOAN_REPAYMENT: "Repayment received",
    REPAYMENT: "Installment paid",
    DISBURSEMENT: "Loan disbursement",
    DEPOSIT: "Wallet deposit",
    WITHDRAWAL: "Funding withdrawal"
  })[type] || type;
}

function LenderDashboard() {
  const { token, user } = useAuth();
  const [marketplace, setMarketplace] = useState([]);
  const [loans, setLoans] = useState([]);
  const [wallet, setWallet] = useState(null);
  const [loading, setLoading] = useState(true);
  const [workingLoanId, setWorkingLoanId] = useState(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const refreshData = useCallback(async () => {
    const [pendingLoans, userLoans, walletData] = await Promise.all([
      fetchMarketplace(token),
      fetchMyLoans(token),
      fetchWallet(token)
    ]);
    setMarketplace(pendingLoans);
    setLoans(userLoans);
    setWallet(walletData);
  }, [token]);

  useEffect(() => {
    const controller = new AbortController();
    async function loadData() {
      try {
        const [pendingLoans, userLoans, walletData] = await Promise.all([
          fetchMarketplace(token, controller.signal),
          fetchMyLoans(token, controller.signal),
          fetchWallet(token, controller.signal)
        ]);
        setMarketplace(pendingLoans);
        setLoans(userLoans);
        setWallet(walletData);
      } catch (requestError) {
        if (requestError.name !== "AbortError") {
          setError(requestError.message || "Unable to load lender dashboard");
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }
    loadData();
    return () => controller.abort();
  }, [token]);

  async function fundLoan(loanId) {
    setWorkingLoanId(loanId);
    setError("");
    setMessage("");
    try {
      const response = await apiFetch(`${LOANS_URL}/${loanId}/fund`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` }
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(result.message || "Unable to fund loan");
      }
      setMessage(`Loan #${loanId} funded from your wallet.`);
      await refreshData();
    } catch (requestError) {
      setError(requestError.message || "Unable to fund loan");
    } finally {
      setWorkingLoanId(null);
    }
  }

  const lenderLoans = loans.filter((loan) => loan.lenderId === user?.id);
  const totalFunded = lenderLoans.reduce((total, loan) => total + Number(loan.amount), 0);
  const outstandingPrincipal = lenderLoans.reduce((total, loan) => total + loan.repayments
    .filter((repayment) => ["PENDING", "OVERDUE"].includes(repayment.status))
    .reduce((loanTotal, repayment) => loanTotal + Number(repayment.principalAmount), 0), 0);
  const repaymentsReceived = lenderLoans.reduce((total, loan) => total + loan.repayments
    .filter((repayment) => repayment.status === "PAID")
    .reduce((loanTotal, repayment) => loanTotal + Number(repayment.amount), 0), 0);
  const groupLoans = lenderLoans.filter((loan) => loan.groupId !== null);

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-5 pb-16 pt-32 text-slate-100 sm:px-8">
      <p className="mb-2 text-xs font-bold uppercase tracking-[.18em] text-emerald-400">Capital that builds community</p>
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Lender dashboard</h1>
      <p className="mt-2 text-sm text-slate-400">Review pending applications and manage loans you have funded.</p>
      <p className="mt-3 text-xs text-slate-500">Disbursed principal, outstanding installments, received repayments, and simulated wallet funds are shown separately.</p>
      <section className="my-8 grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-label="Lender account summary">
        <article className="glass-panel flex items-start gap-4"><span className="icon-tile"><BriefcaseBusiness /></span><div><span className="text-xs font-semibold text-slate-400">Available opportunities</span><strong className="mt-2 block text-3xl font-bold">{marketplace.length}</strong><span className="mt-1 block text-xs text-slate-500">Applications seeking funding</span></div></article>
        <article className="glass-panel flex items-start gap-4"><span className="icon-tile"><HandCoins /></span><div><span className="text-xs font-semibold text-slate-400">Loans funded</span><strong className="mt-2 block text-3xl font-bold">{lenderLoans.length}</strong><span className="mt-1 block text-xs text-slate-500">Loans in your portfolio</span></div></article>
        <article className="glass-panel flex items-start gap-4"><span className="icon-tile"><CircleDollarSign /></span><div><span className="text-xs font-semibold text-slate-400">Principal disbursed</span><strong className="mt-2 block text-2xl font-bold">{formatCurrency(totalFunded)}</strong><span className="mt-1 block text-xs text-slate-500">Lifetime funding amount</span></div></article>
        <article className="glass-panel flex items-start gap-4"><span className="icon-tile"><Activity /></span><div><span className="text-xs font-semibold text-slate-400">Outstanding principal</span><strong className="mt-2 block text-2xl font-bold">{formatCurrency(outstandingPrincipal)}</strong><span className="mt-1 block text-xs text-slate-500">Principal portion of unpaid installments</span></div></article>
        <article className="glass-panel flex items-start gap-4"><span className="icon-tile"><HandCoins /></span><div><span className="text-xs font-semibold text-slate-400">Repayments received</span><strong className="mt-2 block text-2xl font-bold">{formatCurrency(repaymentsReceived)}</strong><span className="mt-1 block text-xs text-slate-500">Paid installments in your loans</span></div></article>
        <article className="glass-panel flex items-start gap-4"><span className="icon-tile"><WalletCards /></span><div><span className="text-xs font-semibold text-slate-400">Available wallet funds</span><strong className="mt-2 block text-2xl font-bold">{wallet ? formatCurrency(wallet.balance) : "—"}</strong><span className="mt-1 block text-xs text-slate-500">Simulated project balance</span></div></article>
        <article className="glass-panel flex items-start gap-4"><span className="icon-tile"><Users /></span><div><span className="text-xs font-semibold text-slate-400">SHG lending</span><strong className="mt-2 block text-2xl font-bold">{groupLoans.length}</strong><span className="mt-1 block text-xs text-slate-500">Group-backed loans funded</span></div></article>
      </section>
      {error && <p className="mb-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-300" role="alert">{error}</p>}
      {message && <p className="mb-4 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-300" role="status">{message}</p>}
      <section className="glass-panel mb-6">
        <div className="mb-5 flex items-center justify-between"><div><h2 className="text-lg font-semibold">Loan marketplace</h2><p className="mt-1 text-xs text-slate-500">Applications seeking funding</p></div><span className="status-badge status-pending">{marketplace.length} available</span></div>
        {loading ? <p className="text-sm text-slate-400" role="status">Loading applications...</p> : marketplace.length === 0
          ? <p className="rounded-xl border border-dashed border-slate-700 p-7 text-center text-sm text-slate-400">No pending loan applications.</p>
          : marketplace.map((loan) => (
            <LoanCard
              key={loan.id}
              loan={loan}
              actionLabel={workingLoanId === loan.id ? "Funding..." : "Fund loan"}
              actionDisabled={workingLoanId !== null}
              onAction={() => fundLoan(loan.id)}
            />
          ))}
      </section>
      <section className="glass-panel">
        <h2 className="mb-4 text-lg font-semibold">My funded loans</h2>
        {loading ? null : lenderLoans.length === 0
          ? <p className="rounded-xl border border-dashed border-slate-700 p-7 text-center text-sm text-slate-400">You have not funded any loans.</p>
          : lenderLoans.map((loan) => <LoanCard key={loan.id} loan={loan} />)}
      </section>
      <section className="glass-panel mt-6">
        <div className="mb-5 flex items-center gap-3"><span className="icon-tile h-10 w-10"><Activity size={19} /></span><div><h2 className="text-lg font-semibold">Recent wallet activity</h2><p className="mt-1 text-xs text-slate-500">Funding and repayments posted to your simulated ledger</p></div></div>
        {!loading && (!wallet?.transactions || wallet.transactions.length === 0)
          ? <p className="rounded-xl border border-dashed border-slate-700 p-6 text-center text-sm text-slate-400">No lender transactions yet.</p>
          : wallet?.transactions?.length > 0 && <div className="overflow-x-auto rounded-xl border border-slate-800"><table className="data-table"><thead><tr><th scope="col">Date</th><th scope="col">Activity</th><th scope="col">Amount</th><th scope="col">Balance after</th></tr></thead><tbody>{wallet.transactions.slice(0, 6).map((transaction) => <tr key={transaction.id}><td>{new Date(transaction.timestamp).toLocaleString()}</td><td>{transactionLabel(transaction.type)}</td><td>{formatCurrency(transaction.amount)}</td><td>{formatCurrency(transaction.balanceAfter)}</td></tr>)}</tbody></table></div>}
      </section>
    </main>
  );
}

export default LenderDashboard;
