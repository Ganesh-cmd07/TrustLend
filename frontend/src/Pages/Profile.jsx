import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../AuthContext";
import api, { getApiErrorMessage } from "../api/axios";
import { ArrowRight, BadgeCheck, CircleUserRound, Mail, ShieldCheck, UserRound } from "lucide-react";

function Profile() {
  const { user, error, refreshUser } = useAuth();
  const [name, setName] = useState(user?.name || "");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [saveError, setSaveError] = useState("");

  async function updateProfile(event) {
    event.preventDefault();
    setSaving(true);
    setMessage("");
    setSaveError("");
    try {
      const response = await api.put("/api/v1/auth/me", { name });
      setName(response.data.name);
      await refreshUser();
      setMessage("Profile updated.");
    } catch (requestError) {
      setSaveError(getApiErrorMessage(requestError, "Unable to update profile"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-5 pb-16 pt-32 text-slate-100 sm:px-8">
      <p className="mb-2 text-xs font-bold uppercase tracking-[.18em] text-emerald-400">Your TrustLend account</p>
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Welcome{user?.name ? `, ${user.name.split(" ")[0]}` : " back"}</h1>
      <p className="mt-2 text-sm text-slate-400">Your financial community, all in one place.</p>
      {error && <p className="mt-5 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-300" role="alert">{error}</p>}
      {user && (
        <section className="glass-panel mt-8" aria-label="Authenticated user information">
          <div className="mb-6 flex items-center gap-3"><span className="icon-tile"><CircleUserRound /></span><div><h2 className="text-lg font-semibold">Account details</h2><p className="mt-1 text-xs text-slate-500">Your profile and account access</p></div></div>
          <div className="grid gap-4 sm:grid-cols-3">
            <p className="rounded-xl border border-slate-800 bg-slate-950/40 p-4"><span className="flex items-center gap-2 text-xs text-slate-500"><UserRound size={15} /> Full name</span><strong className="mt-2 block text-sm font-semibold text-slate-200">{user.name}</strong></p>
            <p className="rounded-xl border border-slate-800 bg-slate-950/40 p-4"><span className="flex items-center gap-2 text-xs text-slate-500"><Mail size={15} /> Email address</span><strong className="mt-2 block break-all text-sm font-semibold text-slate-200">{user.email}</strong></p>
            <p className="rounded-xl border border-slate-800 bg-slate-950/40 p-4"><span className="flex items-center gap-2 text-xs text-slate-500"><ShieldCheck size={15} /> Account type</span><strong className="mt-2 block"><span className={`status-badge ${user.role === "ADMIN" ? "status-rejected" : user.role === "LENDER" ? "status-active" : "status-neutral"}`}>{user.role}</span></strong></p>
            <p className="rounded-xl border border-slate-800 bg-slate-950/40 p-4 sm:col-span-3"><span className="text-xs text-slate-500">Account created</span><strong className="mt-2 block text-sm font-semibold text-slate-200">{user.createdAt ? new Date(user.createdAt).toLocaleString() : "Unavailable"}</strong></p>
          </div>
          <form className="mt-6 grid max-w-xl gap-3" onSubmit={updateProfile}>
            <label className="text-xs font-semibold text-slate-300" htmlFor="profile-name">Update your name</label>
            <input className="field" id="profile-name" value={name} onChange={(event) => setName(event.target.value)} maxLength={100} required />
            <button className="primary-button w-fit" type="submit" disabled={saving || name.trim() === user.name}>{saving ? "Saving..." : "Save profile"}</button>
          </form>
          {message && <p className="mt-4 text-sm text-emerald-300" role="status">{message}</p>}
          {saveError && <p className="mt-4 text-sm text-rose-300" role="alert">{saveError}</p>}
          {user.role === "BORROWER" && user.lenderRequestStatus !== "PENDING" && (
            <p className="mt-6"><Link className="primary-button" to="/lender-request">Request lender status <ArrowRight size={16} /></Link></p>
          )}
          {user.lenderRequestStatus === "PENDING" && (
            <p className="mt-6 flex flex-wrap items-center gap-3 text-sm text-slate-400"><BadgeCheck className="text-amber-400" size={17} /> Lender request status: <span className="status-badge status-pending">Pending review</span></p>
          )}
          {user.lenderRequestStatus === "REJECTED" && (
            <p className="mt-5 text-sm text-slate-400">Your previous lender request was declined. You may submit another request.</p>
          )}
        </section>
      )}
    </main>
  );
}

export default Profile;
