import { useState } from "react";
import { useAuth } from "../AuthContext";
import { apiFetch } from "../api/axios";
import { BadgeCheck, BriefcaseBusiness, Clock3, ShieldCheck } from "lucide-react";

function LenderRequest() {
  const { token, user, refreshUser } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function submitRequest() {
    setSubmitting(true);
    setError("");
    setMessage("");
    try {
      const response = await apiFetch("/api/v1/users/lender-request", {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` }
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body.message || "Unable to submit lender request");
      }
      await refreshUser();
      setMessage("Your request was submitted for admin review.");
    } catch (requestError) {
      setError(requestError.message || "Unable to submit lender request");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-5 pb-16 pt-32 text-slate-100 sm:px-8">
      <p className="mb-2 text-xs font-bold uppercase tracking-[.18em] text-emerald-400">Grow your financial impact</p>
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Become a lender</h1>
      <p className="mt-2 max-w-2xl text-sm leading-relaxed text-slate-400">Request lender access to support borrowers and participate in the TrustLend marketplace.</p>
      <section className="glass-panel mt-8 max-w-3xl">
        <div className="mb-6 flex items-center gap-4"><span className="icon-tile h-12 w-12"><BriefcaseBusiness /></span><div><h2 className="text-lg font-semibold">Lender access request</h2><p className="mt-1 text-xs text-slate-500">A TrustLend administrator will review your request.</p></div></div>
        <div className="mb-6 grid gap-3 sm:grid-cols-2">
          <div className="rounded-xl border border-slate-800 bg-slate-950/40 p-4"><ShieldCheck className="mb-3 text-emerald-400" size={19} /><p className="text-sm font-semibold">Secure review</p><p className="mt-1 text-xs leading-relaxed text-slate-500">Your account remains a borrower until approval.</p></div>
          <div className="rounded-xl border border-slate-800 bg-slate-950/40 p-4"><Clock3 className="mb-3 text-teal-400" size={19} /><p className="text-sm font-semibold">Status tracking</p><p className="mt-1 text-xs leading-relaxed text-slate-500">You will see your request status here.</p></div>
        </div>
        {user?.lenderRequestStatus === "PENDING" ? (
          <p className="flex items-center gap-2 rounded-xl border border-amber-500/20 bg-amber-500/10 p-4 text-sm text-amber-300" role="status"><Clock3 size={17} /> Your request is pending review.</p>
        ) : user?.lenderRequestStatus === "APPROVED" ? (
          <p className="flex items-center gap-2 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-300" role="status"><BadgeCheck size={17} /> Your lender request has been approved.</p>
        ) : (
          <button className="primary-button" type="button" onClick={submitRequest} disabled={submitting}>
            <BriefcaseBusiness size={16} /> {submitting ? "Submitting..." : user?.lenderRequestStatus === "REJECTED" ? "Submit another request" : "Submit lender request"}
          </button>
        )}
        {error && <p className="mt-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-300" role="alert">{error}</p>}
        {message && <p className="mt-4 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-300" role="status">{message}</p>}
      </section>
    </main>
  );
}

export default LenderRequest;
