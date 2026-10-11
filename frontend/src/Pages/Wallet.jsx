import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";
import { apiFetch } from "../api/axios";
import { ArrowDownLeft, ArrowUpRight, CreditCard, History, WalletCards } from "lucide-react";

const WALLET_URL = "/api/v1/wallet";

function formatAmount(amount) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(Number(amount));
}

function transactionLabel(type) {
  return ({
    DEPOSIT: "Wallet deposit",
    WITHDRAWAL: "Wallet withdrawal",
    DISBURSEMENT: "Loan disbursement",
    REPAYMENT: "Loan installment paid",
    LOAN_REPAYMENT: "Loan repayment received"
  })[type] || type;
}

async function readResponse(response) {
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body.message || `Request failed (${response.status})`);
  }
  return body;
}

function Wallet() {
  const { token, logout } = useAuth();
  const navigate = useNavigate();
  const [wallet, setWallet] = useState(null);
  const [transactionPage, setTransactionPage] = useState({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  const [historyPage, setHistoryPage] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [amounts, setAmounts] = useState({ deposit: "", withdraw: "" });
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const handleUnauthorized = useCallback(() => {
    logout();
    navigate("/login", { replace: true });
  }, [logout, navigate]);

  const loadTransactions = useCallback(async (page, signal) => {
    try {
      const response = await apiFetch(`${WALLET_URL}/transactions?page=${page}&size=20`, {
        headers: { Authorization: `Bearer ${token}` }, signal
      });
      if (response.status === 401) {
        handleUnauthorized();
        return;
      }
      setTransactionPage(await readResponse(response));
    } finally {
      if (!signal?.aborted) setHistoryLoading(false);
    }
  }, [token, handleUnauthorized]);

  useEffect(() => {
    const controller = new AbortController();

    async function loadWallet() {
      try {
        const response = await apiFetch(`${WALLET_URL}/me`, {
          headers: { Authorization: `Bearer ${token}` },
          signal: controller.signal
        });
        if (response.status === 401) {
          handleUnauthorized();
          return;
        }
        setWallet(await readResponse(response));
      } catch (requestError) {
        if (requestError.name !== "AbortError") {
          setError(requestError.message || "Unable to load wallet");
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }

    loadWallet();
    return () => controller.abort();
  }, [token, handleUnauthorized]);

  useEffect(() => {
    const controller = new AbortController();
    loadTransactions(historyPage, controller.signal).catch((requestError) => {
      if (requestError.name !== "AbortError") setError(requestError.message || "Unable to load transaction history");
    });
    return () => controller.abort();
  }, [historyPage, loadTransactions]);

  const handleAmountChange = (type, value) => {
    setAmounts((current) => ({ ...current, [type]: value }));
    setError("");
    setMessage("");
  };

  const submitTransaction = async (event, type) => {
    event.preventDefault();
    const rawAmount = amounts[type];
    const amount = Number(rawAmount);

    if (!rawAmount || !Number.isFinite(amount) || amount <= 0
      || !/^\d+(\.\d{1,2})?$/.test(rawAmount)) {
      setError("Enter an amount greater than zero with no more than two decimal places.");
      return;
    }

    setSubmitting(type);
    setError("");
    setMessage("");
    try {
      const response = await apiFetch(`${WALLET_URL}/${type}`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ amount: rawAmount })
      });
      if (response.status === 401) {
        handleUnauthorized();
        return;
      }
      setWallet(await readResponse(response));
      setHistoryLoading(true);
      await loadTransactions(0);
      setHistoryPage(0);
      setAmounts((current) => ({ ...current, [type]: "" }));
      setMessage(type === "deposit" ? "Deposit recorded." : "Withdrawal recorded.");
    } catch (requestError) {
      setError(requestError.message || "Unable to complete wallet transaction");
    } finally {
      setSubmitting("");
    }
  };

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-5 pb-16 pt-32 text-slate-100 sm:px-8">
      <p className="mb-2 text-xs font-bold uppercase tracking-[.18em] text-emerald-400">Your money, in one place</p>
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">My wallet</h1>
      <p className="mt-2 text-sm text-slate-400">Manage your balance and follow every transaction.</p>
      <p className="mt-4 rounded-xl border border-amber-500/20 bg-amber-500/10 p-4 text-sm text-amber-200" role="note">Demo wallet: balances and transactions are simulated for this project. No real money is deposited, withdrawn, or transferred.</p>
      {error && <p className="mt-5 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-300" role="alert">{error}</p>}
      {message && <p className="mt-5 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-300" role="status">{message}</p>}

      <section className="glass-panel relative mt-8 overflow-hidden bg-gradient-to-br from-emerald-950/70 via-slate-900/90 to-teal-950/50" aria-label="Wallet balance">
        <div className="absolute -right-10 -top-16 h-56 w-56 rounded-full bg-emerald-500/10 blur-3xl" />
        <div className="relative flex flex-wrap items-start justify-between gap-5">
          <div><div className="mb-4 flex items-center gap-2 text-sm font-medium text-emerald-100/70"><WalletCards size={18} /> Available balance</div><strong className="block text-4xl font-bold tracking-tight text-white sm:text-5xl">{loading && !wallet ? "Loading..." : wallet ? formatAmount(wallet.balance) : "—"}</strong></div>
          <span className="rounded-xl border border-white/10 bg-white/5 p-3 text-emerald-300"><CreditCard size={25} /></span>
        </div>
      </section>

      <section className="my-6 grid gap-4 md:grid-cols-2" aria-label="Wallet actions">
        {["deposit", "withdraw"].map((type) => (
          <form
            className="glass-panel grid content-start gap-3"
            key={type}
            onSubmit={(event) => submitTransaction(event, type)}
          >
            <div className="mb-1 flex items-center gap-3"><span className="icon-tile h-10 w-10">{type === "deposit" ? <ArrowDownLeft size={19} /> : <ArrowUpRight size={19} />}</span><h2 className="text-lg font-semibold">{type === "deposit" ? "Add funds" : "Withdraw funds"}</h2></div>
            <label className="text-xs font-semibold text-slate-300" htmlFor={`${type}-amount`}>Amount</label>
            <input
              className="field"
              id={`${type}-amount`}
              type="number"
              min="0.01"
              step="0.01"
              inputMode="decimal"
              required
              value={amounts[type]}
              onChange={(event) => handleAmountChange(type, event.target.value)}
              aria-label={`${type} amount`}
            />
            <button className="primary-button mt-1 w-full" type="submit" disabled={Boolean(submitting) || loading}>
              {submitting === type
                ? "Submitting..."
                : type === "deposit" ? "Deposit" : "Withdraw"}
            </button>
          </form>
        ))}
      </section>

      <section className="glass-panel">
        <div className="mb-5 flex items-center gap-3"><span className="icon-tile h-10 w-10"><History size={19} /></span><div><h2 className="text-lg font-semibold">Transaction history</h2><p className="mt-1 text-xs text-slate-500">A record of recent wallet activity</p></div></div>
        {loading && <p className="text-sm text-slate-400" role="status">Loading transactions...</p>}
        {!loading && !historyLoading && transactionPage.totalElements === 0 && (
          <p className="rounded-xl border border-dashed border-slate-700 p-7 text-center text-sm text-slate-400">No transactions yet.</p>
        )}
        {!loading && transactionPage.content.length > 0 && (
          <div className="overflow-x-auto rounded-xl border border-slate-800">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Date</th>
                  <th scope="col">Type</th>
                  <th scope="col">Amount</th>
                  <th scope="col">Balance after</th>
                </tr>
              </thead>
              <tbody>
                {transactionPage.content.map((transaction) => (
                  <tr key={transaction.id}>
                    <td>{new Date(transaction.timestamp).toLocaleString()}</td>
                    <td>{transactionLabel(transaction.type)}</td>
                    <td>{formatAmount(transaction.amount)}</td>
                    <td>{formatAmount(transaction.balanceAfter)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {!loading && transactionPage.totalPages > 1 && <nav className="mt-4 flex items-center justify-between gap-3" aria-label="Transaction history pages">
          <span className="text-xs text-slate-500">Page {historyPage + 1} of {transactionPage.totalPages} · {transactionPage.totalElements} transactions</span>
          <div className="flex gap-2"><button className="secondary-button" type="button" disabled={historyLoading || historyPage === 0} onClick={() => { setHistoryLoading(true); setHistoryPage((page) => Math.max(0, page - 1)); }}>Previous</button><button className="secondary-button" type="button" disabled={historyLoading || historyPage + 1 >= transactionPage.totalPages} onClick={() => { setHistoryLoading(true); setHistoryPage((page) => page + 1); }}>Next</button></div>
        </nav>}
      </section>
    </main>
  );
}

export default Wallet;
