import { useCallback, useEffect, useState, useMemo } from "react";
import { useAuth } from "../AuthContext";
import { apiFetch } from "../api/axios";
import LoanCard from "./LoanCard";
import { Activity, ArrowDownUp, BriefcaseBusiness, CircleDollarSign, Filter, HandCoins, Search, TrendingUp, WalletCards } from "lucide-react";
import { formatCurrency } from "../utils/formatCurrency";
import { Link } from "react-router-dom";

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

  // Marketplace filter and sort state
  const [searchTerm, setSearchTerm] = useState("");
  const [termFilter, setTermFilter] = useState("ALL");
  const [sortBy, setSortBy] = useState("RATE_DESC");

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
      setMessage(`Loan #${loanId} funded successfully from your wallet.`);
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

  // Filtered & sorted marketplace
  const filteredMarketplace = useMemo(() => {
    return marketplace
      .filter((loan) => {
        if (searchTerm.trim()) {
          const matchId = String(loan.id).includes(searchTerm.trim());
          const matchBorrower = loan.borrowerName?.toLowerCase().includes(searchTerm.toLowerCase());
          if (!matchId && !matchBorrower) return false;
        }
        if (termFilter === "SHORT" && Number(loan.termMonths) > 6) return false;
        if (termFilter === "MEDIUM" && (Number(loan.termMonths) <= 6 || Number(loan.termMonths) > 12)) return false;
        if (termFilter === "LONG" && Number(loan.termMonths) <= 12) return false;
        return true;
      })
      .sort((a, b) => {
        if (sortBy === "RATE_DESC") return Number(b.interestRate) - Number(a.interestRate);
        if (sortBy === "RATE_ASC") return Number(a.interestRate) - Number(b.interestRate);
        if (sortBy === "AMOUNT_ASC") return Number(a.amount) - Number(b.amount);
        if (sortBy === "AMOUNT_DESC") return Number(b.amount) - Number(a.amount);
        if (sortBy === "TERM_ASC") return Number(a.termMonths) - Number(b.termMonths);
        return 0;
      });
  }, [marketplace, searchTerm, termFilter, sortBy]);

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-5 pb-16 pt-32 text-slate-100 sm:px-8">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <p className="mb-2 text-xs font-bold uppercase tracking-[.18em] text-emerald-400">Capital that builds community</p>
          <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Lender dashboard</h1>
          <p className="mt-2 text-sm text-slate-400">Review community loan applications and manage your funded portfolio.</p>
        </div>
        <Link to="/wallet" className="secondary-button self-start sm:self-auto">
          <WalletCards size={16} /> Manage Wallet Funds
        </Link>
      </div>

      {/* Portfolio Stats Grid */}
      <section className="my-8 grid gap-4 md:grid-cols-2 xl:grid-cols-4" aria-label="Lender account summary">
        <article className="glass-panel flex items-start gap-4">
          <span className="icon-tile"><BriefcaseBusiness /></span>
          <div>
            <span className="text-xs font-semibold text-slate-400">Marketplace</span>
            <strong className="mt-2 block text-3xl font-bold">{marketplace.length}</strong>
            <span className="mt-1 block text-xs text-slate-500">Available to fund</span>
          </div>
        </article>

        <article className="glass-panel flex items-start gap-4">
          <span className="icon-tile"><HandCoins /></span>
          <div>
            <span className="text-xs font-semibold text-slate-400">Portfolio loans</span>
            <strong className="mt-2 block text-3xl font-bold">{lenderLoans.length}</strong>
            <span className="mt-1 block text-xs text-slate-500">Currently active loans</span>
          </div>
        </article>

        <article className="glass-panel flex items-start gap-4">
          <span className="icon-tile"><CircleDollarSign /></span>
          <div>
            <span className="text-xs font-semibold text-slate-400">Principal disbursed</span>
            <strong className="mt-2 block text-2xl font-bold text-emerald-400">{formatCurrency(totalFunded)}</strong>
            <span className="mt-1 block text-xs text-slate-500">Lifetime investment</span>
          </div>
        </article>

        <article className="glass-panel flex items-start gap-4">
          <span className="icon-tile"><TrendingUp /></span>
          <div>
            <span className="text-xs font-semibold text-slate-400">Repayments received</span>
            <strong className="mt-2 block text-2xl font-bold text-teal-300">{formatCurrency(repaymentsReceived)}</strong>
            <span className="mt-1 block text-xs text-slate-500">Principal + interest paid back</span>
          </div>
        </article>
      </section>

      {/* Secondary Stats Row */}
      <section className="mb-8 grid gap-4 sm:grid-cols-3">
        <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
          <span className="text-xs text-slate-400 block">Outstanding Principal</span>
          <strong className="text-xl font-bold text-slate-200 mt-1 block">{formatCurrency(outstandingPrincipal)}</strong>
          <span className="text-[11px] text-slate-500 mt-0.5 block">Unpaid installment balances</span>
        </div>
        <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
          <span className="text-xs text-slate-400 block">Wallet Available Balance</span>
          <strong className="text-xl font-bold text-emerald-300 mt-1 block">{wallet ? formatCurrency(wallet.balance) : "—"}</strong>
          <span className="text-[11px] text-slate-500 mt-0.5 block">Ready to fund loans</span>
        </div>
        <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
          <span className="text-xs text-slate-400 block">SHG Group Loans</span>
          <strong className="text-xl font-bold text-indigo-300 mt-1 block">{groupLoans.length}</strong>
          <span className="text-[11px] text-slate-500 mt-0.5 block">Community co-signed loans</span>
        </div>
      </section>

      {error && (
        <p className="mb-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-300" role="alert">
          {error}
        </p>
      )}
      {message && (
        <p className="mb-4 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-300" role="status">
          {message}
        </p>
      )}

      {/* Marketplace with Filter and Search Controls */}
      <section className="glass-panel mb-8">
        <div className="mb-5 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-lg font-semibold">Loan marketplace</h2>
            <p className="mt-1 text-xs text-slate-500">Approved borrower requests seeking peer funding</p>
          </div>
          <span className="status-badge status-pending self-start sm:self-auto">
            {filteredMarketplace.length} of {marketplace.length} available
          </span>
        </div>

        {/* Filter and Search Bar */}
        <div className="mb-6 grid gap-3 sm:grid-cols-3">
          <div className="relative">
            <Search className="absolute left-3.5 top-3.5 text-slate-500" size={15} />
            <input
              type="text"
              placeholder="Search borrower or loan #..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="field !pl-9 !py-2.5 text-xs"
            />
          </div>

          <div className="flex items-center gap-1.5 rounded-xl border border-slate-800 bg-slate-950/60 p-1 text-xs">
            <span className="text-slate-500 ml-1.5 text-[11px] flex items-center gap-1">
              <Filter size={11} /> Term:
            </span>
            {["ALL", "SHORT", "MEDIUM", "LONG"].map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setTermFilter(t)}
                className={`rounded-lg px-2 py-1 font-medium text-[11px] transition ${
                  termFilter === t ? "bg-emerald-500/20 text-emerald-300 font-semibold" : "text-slate-400 hover:text-slate-200"
                }`}
              >
                {t}
              </button>
            ))}
          </div>

          <div className="flex items-center gap-2">
            <ArrowDownUp size={14} className="text-slate-500 shrink-0" />
            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value)}
              className="field !py-2.5 text-xs text-slate-200"
            >
              <option value="RATE_DESC">Highest Interest %</option>
              <option value="RATE_ASC">Lowest Interest %</option>
              <option value="AMOUNT_ASC">Lowest Amount</option>
              <option value="AMOUNT_DESC">Highest Amount</option>
              <option value="TERM_ASC">Shortest Term</option>
            </select>
          </div>
        </div>

        {loading ? (
          <p className="text-sm text-slate-400" role="status">Loading opportunities...</p>
        ) : filteredMarketplace.length === 0 ? (
          <p className="rounded-xl border border-dashed border-slate-700 p-7 text-center text-sm text-slate-400">
            {marketplace.length === 0 ? "No loan applications seeking funding right now." : "No loans match your search filters."}
          </p>
        ) : (
          filteredMarketplace.map((loan) => (
            <LoanCard
              key={loan.id}
              loan={loan}
              actionLabel={workingLoanId === loan.id ? "Funding..." : `Fund this loan (${formatCurrency(loan.amount)})`}
              actionDisabled={workingLoanId !== null || (wallet && Number(wallet.balance) < Number(loan.amount))}
              onAction={() => fundLoan(loan.id)}
            />
          ))
        )}
      </section>

      {/* My Funded Loans */}
      <section className="glass-panel mb-8">
        <h2 className="mb-4 text-lg font-semibold">My funded loans portfolio</h2>
        {loading ? null : lenderLoans.length === 0 ? (
          <p className="rounded-xl border border-dashed border-slate-700 p-7 text-center text-sm text-slate-400">
            You have not funded any loans yet. Browse the marketplace above to begin investing.
          </p>
        ) : (
          lenderLoans.map((loan) => <LoanCard key={loan.id} loan={loan} />)
        )}
      </section>

      {/* Recent Ledger Activity */}
      <section className="glass-panel">
        <div className="mb-5 flex items-center gap-3">
          <span className="icon-tile h-10 w-10"><Activity size={19} /></span>
          <div>
            <h2 className="text-lg font-semibold">Simulated ledger activity</h2>
            <p className="mt-1 text-xs text-slate-500">Disbursements and monthly repayments credited to your wallet</p>
          </div>
        </div>
        {!loading && (!wallet?.transactions || wallet.transactions.length === 0) ? (
          <p className="rounded-xl border border-dashed border-slate-700 p-6 text-center text-sm text-slate-400">
            No lender transactions yet.
          </p>
        ) : (
          wallet?.transactions?.length > 0 && (
            <div className="overflow-x-auto rounded-xl border border-slate-800">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Date</th>
                    <th scope="col">Activity</th>
                    <th scope="col">Amount</th>
                    <th scope="col">Balance after</th>
                  </tr>
                </thead>
                <tbody>
                  {wallet.transactions.slice(0, 8).map((transaction) => (
                    <tr key={transaction.id}>
                      <td>{new Date(transaction.timestamp).toLocaleString()}</td>
                      <td>{transactionLabel(transaction.type)}</td>
                      <td className={transaction.amount > 0 ? "text-emerald-400 font-semibold" : ""}>
                        {formatCurrency(transaction.amount)}
                      </td>
                      <td>{formatCurrency(transaction.balanceAfter)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        )}
      </section>
    </main>
  );
}

export default LenderDashboard;
