import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../AuthContext";
import { apiFetch } from "../api/axios";
import { Bot, Check, RefreshCw, ShieldCheck, Users, UserRound, X } from "lucide-react";
import { formatCurrency } from "../utils/formatCurrency";

const ADMIN_URL = "/api/v1/admin";

async function parseResponse(response) {
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body.message || `Request failed (${response.status})`);
  }
  return body;
}

function Admin() {
  const { token } = useAuth();
  const [users, setUsers] = useState([]);
  const [requests, setRequests] = useState([]);
  const [loanApplications, setLoanApplications] = useState([]);
  const [loanReviewHistory, setLoanReviewHistory] = useState([]);
  const [loanReasons, setLoanReasons] = useState({});
  const [aiStatus, setAiStatus] = useState({ configured: false, indexedChunks: 0, chatModel: "", embeddingModel: "" });
  const [reindexing, setReindexing] = useState(false);
  const [reindexMessage, setReindexMessage] = useState("");
  const [loading, setLoading] = useState(true);
  const [workingId, setWorkingId] = useState(null);
  const [error, setError] = useState("");
  const [activeFilter, setActiveFilter] = useState("ALL");
  const [userSearch, setUserSearch] = useState("");
  const [userPage, setUserPage] = useState(0);
  const [userTotalElements, setUserTotalElements] = useState(0);
  const [userTotalPages, setUserTotalPages] = useState(0);

  const loadManagementData = useCallback(async (signal) => {
    const headers = { Authorization: `Bearer ${token}` };
    const userParams = new URLSearchParams({ page: String(userPage), size: "10" });
    if (activeFilter !== "ALL") userParams.set("role", activeFilter);
    if (userSearch.trim()) userParams.set("q", userSearch.trim());
    const [usersResponse, requestsResponse, loansResponse, reviewHistoryResponse, aiStatusResponse] = await Promise.all([
      apiFetch(`${ADMIN_URL}/users?${userParams}`, { headers, signal }),
      apiFetch(`${ADMIN_URL}/lender-requests`, { headers, signal }),
      apiFetch(`${ADMIN_URL}/loan-applications`, { headers, signal }),
      apiFetch(`${ADMIN_URL}/loan-review-history`, { headers, signal }),
      apiFetch('/api/v1/ai/status', { headers, signal }).catch(() => null)
    ]);
    const [userPageData, pendingRequests, submittedLoans, recentLoanReviews] = await Promise.all([
      parseResponse(usersResponse),
      parseResponse(requestsResponse),
      parseResponse(loansResponse),
      parseResponse(reviewHistoryResponse)
    ]);
    setUsers(userPageData.content || []);
    setUserTotalElements(userPageData.totalElements || 0);
    setUserTotalPages(userPageData.totalPages || 0);
    setRequests(pendingRequests);
    setLoanApplications(submittedLoans);
    setLoanReviewHistory(recentLoanReviews);
    if (aiStatusResponse) {
      try {
        const aiData = await parseResponse(aiStatusResponse);
        if (aiData && typeof aiData === "object" && !Array.isArray(aiData)) {
          setAiStatus(aiData);
        }
      } catch {
        // ignore if not configured
      }
    }
    setError("");
  }, [token, userPage, activeFilter, userSearch]);

  useEffect(() => {
    const controller = new AbortController();

    async function loadInitialData() {
      try {
        await loadManagementData(controller.signal);
      } catch (requestError) {
        if (requestError.name !== "AbortError") {
          setError(requestError.message || "Unable to load user management data");
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }

    loadInitialData();
    return () => controller.abort();
  }, [loadManagementData]);

  async function reviewRequest(requestId, decision) {
    setWorkingId(requestId);
    setError("");
    try {
      const response = await apiFetch(
        `${ADMIN_URL}/lender-requests/${requestId}/${decision}`,
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}` }
        }
      );
      await parseResponse(response);
      await loadManagementData(new AbortController().signal);
    } catch (requestError) {
      setError(requestError.message || "Unable to review lender request");
    } finally {
      setWorkingId(null);
    }
  }

  async function reviewLoan(loanId, decision) {
    const reason = loanReasons[loanId]?.trim() || "";
    setWorkingId(`loan-${loanId}`);
    setError("");
    try {
      const response = await apiFetch(`${ADMIN_URL}/loan-applications/${loanId}/review`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ decision, reason: reason || null })
      });
      await parseResponse(response);
      await loadManagementData(new AbortController().signal);
    } catch (requestError) {
      setError(requestError.message || "Unable to review loan application");
    } finally {
      setWorkingId(null);
    }
  }

  async function triggerReindex() {
    setReindexing(true);
    setReindexMessage("");
    setError("");
    try {
      const response = await apiFetch(`${ADMIN_URL}/ai/reindex`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` }
      });
      const data = await parseResponse(response);
      setReindexMessage(data.message || "Knowledge indexing started in the background.");
      setTimeout(async () => {
        try {
          const res = await apiFetch("/api/v1/ai/status", {
            headers: { Authorization: `Bearer ${token}` }
          });
          const updated = await parseResponse(res);
          if (updated && typeof updated === "object") setAiStatus(updated);
        } catch {
          // ignore
        }
      }, 2000);
    } catch (requestError) {
      setError(requestError.message || "Failed to trigger AI knowledge re-indexing");
    } finally {
      setReindexing(false);
    }
  }

  const usersPerPage = 10;
  const visibleUsers = users;

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-5 pb-16 pt-32 text-slate-100 sm:px-8">
      <p className="mb-2 text-xs font-bold uppercase tracking-[.18em] text-emerald-400">TrustLend operations</p>
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">User management</h1>
      <p className="mt-2 text-sm text-slate-400">Review access requests and manage the TrustLend community.</p>
      <section className="my-8 grid gap-4 md:grid-cols-3" aria-label="Administration overview">
        <article className="glass-panel flex items-start gap-4"><span className="icon-tile"><Users /></span><div><span className="text-xs font-semibold text-slate-400">Registered users</span><strong className="mt-2 block text-3xl font-bold">{userTotalElements}</strong><span className="mt-1 block text-xs text-slate-500">Across all account types</span></div></article>
        <article className="glass-panel flex items-start gap-4"><span className="icon-tile"><ShieldCheck /></span><div><span className="text-xs font-semibold text-slate-400">Pending reviews</span><strong className="mt-2 block text-3xl font-bold">{requests.length}</strong><span className="mt-1 block text-xs text-slate-500">Lender applications to review</span></div></article>
        <article className="glass-panel flex items-start gap-4"><span className="icon-tile"><UserRound /></span><div><span className="text-xs font-semibold text-slate-400">Active lenders</span><strong className="mt-2 block text-3xl font-bold">{users.filter((managedUser) => managedUser.role === "LENDER").length}</strong><span className="mt-1 block text-xs text-slate-500">Approved lending accounts</span></div></article>
      </section>
      {error && <p className="mb-5 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-300" role="alert">{error}</p>}
      {loading ? (
        <p className="glass-panel text-sm text-slate-400" role="status">Loading user management...</p>
      ) : (
        <>
          <section className="glass-panel mb-6">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-100">
                  <Bot size={20} className="text-emerald-400" /> AI Assistant & Knowledge Base
                </h2>
                <p className="mt-1 text-xs text-slate-500">
                  Retrieval-Augmented Generation (RAG) vector index and policy knowledge base status.
                </p>
              </div>
              <button
                type="button"
                className="secondary-button border-emerald-500/30 text-emerald-300 hover:bg-emerald-500/10 flex items-center gap-2 text-xs"
                disabled={reindexing}
                onClick={triggerReindex}
              >
                <RefreshCw size={14} className={reindexing ? "animate-spin" : ""} />
                {reindexing ? "Re-indexing..." : "Re-index Policies"}
              </button>
            </div>

            {reindexMessage && (
              <div className="mb-4 rounded-xl border border-emerald-500/30 bg-emerald-950/30 p-3 text-xs text-emerald-300">
                {reindexMessage}
              </div>
            )}

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-3.5">
                <span className="block text-xs text-slate-400">RAG Integration</span>
                <div className="mt-1.5 flex items-center gap-2">
                  <span className={`inline-block h-2 w-2 rounded-full ${aiStatus.configured ? "bg-emerald-400 shadow-[0_0_8px_#34d399]" : "bg-amber-400"}`} />
                  <span className="text-sm font-semibold text-slate-200">
                    {aiStatus.configured ? "Provider Configured" : "Unconfigured / Fallback"}
                  </span>
                </div>
              </div>
              <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-3.5">
                <span className="block text-xs text-slate-400">Indexed Chunks</span>
                <span className="mt-1 block text-lg font-bold text-emerald-400">
                  {aiStatus.indexedChunks ?? 0}
                </span>
              </div>
              <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-3.5">
                <span className="block text-xs text-slate-400">Chat Model</span>
                <span className="mt-1 block font-mono text-xs text-slate-300 truncate">
                  {aiStatus.chatModel || "gemini-2.5-flash"}
                </span>
              </div>
              <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-3.5">
                <span className="block text-xs text-slate-400">Embedding Model</span>
                <span className="mt-1 block font-mono text-xs text-slate-300 truncate">
                  {aiStatus.embeddingModel || "gemini-embedding-001"}
                </span>
              </div>
            </div>
          </section>

          <section className="glass-panel mb-6">
            <div className="mb-5 flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-lg font-semibold">Loan applications</h2><p className="mt-1 text-xs text-slate-500">Approve eligible applications for lender consideration or reject with a reason.</p></div><span className="status-badge status-pending">{loanApplications.length} awaiting review</span></div>
            {loanApplications.length === 0 ? <p className="rounded-xl border border-dashed border-slate-700 p-6 text-center text-sm text-slate-400">No loan applications await review.</p> : (
              <div className="overflow-x-auto rounded-xl border border-slate-800"><table className="data-table">
                <thead><tr><th scope="col">Applicant</th><th scope="col">Request</th><th scope="col">SHG</th><th scope="col">Review reason</th><th scope="col">Actions</th></tr></thead>
                <tbody>{loanApplications.map((loan) => {
                  const cosignRequired = loan.groupId && !loan.groupApprovedById;
                  return <tr key={loan.id}>
                    <td><span className="font-medium text-slate-100">{loan.borrowerName}</span><span className="block text-xs text-slate-500">Loan #{loan.id}</span></td>
                    <td>{formatCurrency(loan.amount)}<span className="block text-xs text-slate-500">{loan.termMonths} months at {loan.interestRate}%</span></td>
                    <td>{loan.groupName || "—"}{cosignRequired && <span className="block text-xs text-amber-300">Leader co-sign required</span>}</td>
                    <td><input className="field min-w-48" aria-label={`Rejection reason for loan ${loan.id}`} maxLength={1000} value={loanReasons[loan.id] || ""} onChange={(event) => setLoanReasons((current) => ({ ...current, [loan.id]: event.target.value }))} placeholder="Required to reject" /></td>
                    <td><div className="flex gap-2"><button className="secondary-button border-emerald-500/20 text-emerald-300" type="button" disabled={workingId !== null || Boolean(cosignRequired)} onClick={() => reviewLoan(loan.id, "APPROVE")}><Check size={15} /> Approve</button><button className="danger-button" type="button" disabled={workingId !== null || !loanReasons[loan.id]?.trim()} onClick={() => reviewLoan(loan.id, "REJECT")}><X size={15} /> Reject</button></div></td>
                  </tr>;
                })}</tbody>
              </table></div>
            )}
          </section>
          <section className="glass-panel mb-6">
            <div className="mb-5"><h2 className="text-lg font-semibold">Recent loan review history</h2><p className="mt-1 text-xs text-slate-500">Latest 20 administrator decisions, with reviewer and reason.</p></div>
            {loanReviewHistory.length === 0 ? <p className="rounded-xl border border-dashed border-slate-700 p-6 text-center text-sm text-slate-400">No loan decisions recorded yet.</p> : (
              <div className="overflow-x-auto rounded-xl border border-slate-800"><table className="data-table"><thead><tr><th scope="col">Date</th><th scope="col">Application</th><th scope="col">Reviewer</th><th scope="col">Decision</th><th scope="col">Reason</th></tr></thead><tbody>
                {loanReviewHistory.map((review) => <tr key={review.id}><td>{new Date(review.createdAt).toLocaleString()}</td><td>#{review.loanId}</td><td>{review.reviewerName}</td><td><span className={`status-badge ${review.newStatus === "REJECTED" ? "status-rejected" : "status-active"}`}>{review.newStatus}</span></td><td>{review.reason || "—"}</td></tr>)}
              </tbody></table></div>
            )}
          </section>
          <section className="glass-panel mb-6">
            <div className="mb-5 flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-lg font-semibold">Lender requests</h2><p className="mt-1 text-xs text-slate-500">Approve or decline account access</p></div><span className="status-badge status-pending">{requests.length} pending</span></div>
            {requests.length === 0 ? <p className="rounded-xl border border-dashed border-slate-700 p-6 text-center text-sm text-slate-400">No pending requests.</p> : (
              <div className="overflow-x-auto rounded-xl border border-slate-800">
                <table className="data-table">
                  <thead><tr><th scope="col">Applicant</th><th scope="col">Email</th><th scope="col">Status</th><th scope="col">Actions</th></tr></thead>
                  <tbody>{requests.map((request) => (
                    <tr key={request.id}>
                      <td className="font-medium text-slate-100">{request.name}</td><td>{request.email}</td><td><span className="status-badge status-pending">Pending</span></td>
                      <td><div className="flex gap-2"><button className="secondary-button border-emerald-500/20 text-emerald-300 hover:bg-emerald-500/10" type="button" disabled={workingId !== null} onClick={() => reviewRequest(request.id, "approve")}><Check size={15} /> Approve</button><button className="danger-button" type="button" disabled={workingId !== null} onClick={() => reviewRequest(request.id, "reject")}><X size={15} /> Reject</button></div></td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            )}
          </section>
          <section className="glass-panel">
            <div className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end"><div><h2 className="text-lg font-semibold">Users</h2><p className="mt-1 text-xs text-slate-500">Directory of registered TrustLend accounts</p><label className="mt-3 block text-xs font-semibold text-slate-300" htmlFor="admin-user-search">Search name or email</label><input className="field mt-1 max-w-sm" id="admin-user-search" type="search" value={userSearch} onChange={(event) => { setUserSearch(event.target.value); setUserPage(0); }} /></div><div className="flex flex-wrap gap-2" aria-label="Filter users">{["ALL", "BORROWER", "LENDER", "ADMIN"].map((role) => <button key={role} className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition ${activeFilter === role ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300" : "border-slate-700 bg-slate-950/40 text-slate-400 hover:border-slate-600"}`} type="button" aria-pressed={activeFilter === role} onClick={() => { setActiveFilter(role); setUserPage(0); }}>{role === "ALL" ? "All users" : role}</button>)}</div></div>
            <div className="overflow-x-auto rounded-xl border border-slate-800">
              <table className="data-table">
                <thead><tr><th scope="col">Name</th><th scope="col">Email</th><th scope="col">Account type</th></tr></thead>
                <tbody>{visibleUsers.map((managedUser) => (
                  <tr key={managedUser.id}><td className="font-medium text-slate-100">{managedUser.name}</td><td>{managedUser.email}</td><td><span className={`status-badge ${managedUser.role === "ADMIN" ? "status-rejected" : managedUser.role === "LENDER" ? "status-active" : "status-neutral"}`}>{managedUser.role}</span></td></tr>
                ))}</tbody>
              </table>
              {visibleUsers.length === 0 && <p className="p-6 text-center text-sm text-slate-400">No users match this filter.</p>}
            </div>
            {userTotalElements > usersPerPage && <nav className="mt-4 flex items-center justify-between gap-3" aria-label="User directory pages"><span className="text-xs text-slate-500">Page {userPage + 1} of {userTotalPages} · {userTotalElements} users</span><div className="flex gap-2"><button className="secondary-button" type="button" disabled={userPage === 0} onClick={() => setUserPage((page) => Math.max(0, page - 1))}>Previous users</button><button className="secondary-button" type="button" disabled={userPage + 1 >= userTotalPages} onClick={() => setUserPage((page) => page + 1)}>Next users</button></div></nav>}
          </section>
        </>
      )}
    </main>
  );
}

export default Admin;
