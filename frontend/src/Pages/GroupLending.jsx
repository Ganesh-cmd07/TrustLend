import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../AuthContext";
import { apiFetch } from "../api/axios";
import { AlertCircle, CheckCircle2, Clock3, Crown, HandCoins, Plus, ShieldCheck, Users } from "lucide-react";
import { formatCurrency } from "../utils/formatCurrency";
const GROUPS_URL = "/api/v1/groups";

async function readResponse(response) {
  const result = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(result.message || `Request failed (${response.status})`);
  }
  return result;
}

function GroupLending() {
  const { token, user } = useAuth();
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [working, setWorking] = useState("");
  const [contributions, setContributions] = useState({});
  const [loanForms, setLoanForms] = useState({});
  const [newGroup, setNewGroup] = useState({ name: "", description: "" });
  const [maxLoanLimit, setMaxLoanLimit] = useState(null);
  const [creditLimitError, setCreditLimitError] = useState("");

  const loadGroups = useCallback(async (signal) => {
    const response = await apiFetch(GROUPS_URL, {
      headers: { Authorization: `Bearer ${token}` },
      signal
    });
    setGroups(await readResponse(response));
  }, [token]);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      try {
        await loadGroups(controller.signal);
      } catch (requestError) {
        if (requestError.name !== "AbortError") {
          setError(requestError.message || "Unable to load active groups");
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }
    load();
    return () => controller.abort();
  }, [loadGroups]);

  useEffect(() => {
    if (!token) return undefined;
    const controller = new AbortController();

    async function loadCreditLimit() {
      try {
        const response = await apiFetch("/api/v1/credit-score/me", {
          headers: { Authorization: `Bearer ${token}` },
          signal: controller.signal
        });
        const scoreData = await readResponse(response);
        const limit = Number(scoreData.maxEligibleLoan);
        setMaxLoanLimit(Number.isFinite(limit) ? limit : null);
        setCreditLimitError("");
      } catch (requestError) {
        if (requestError.name !== "AbortError") {
          setCreditLimitError("Unable to load your credit limit. Group loan requests are unavailable.");
        }
      }
    }

    loadCreditLimit();
    return () => controller.abort();
  }, [token]);

  async function submitAction(key, successMessage, request) {
    setWorking(key);
    setError("");
    setMessage("");
    try {
      await request();
      setMessage(successMessage);
      await loadGroups();
    } catch (requestError) {
      setError(requestError.message || "Unable to complete the group request");
    } finally {
      setWorking("");
    }
  }

  function membershipFor(group) {
    return group.members?.find((member) => member.userId === user?.id);
  }

  function approveMember(groupId, membershipId) {
    const key = `approve-member-${membershipId}`;
    return submitAction(key, "Member join request approved.", async () => {
      const response = await apiFetch(`${GROUPS_URL}/${groupId}/members/${membershipId}/approve`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` }
      });
      await readResponse(response);
    });
  }

  function rejectMember(groupId, membershipId) {
    const key = `reject-member-${membershipId}`;
    return submitAction(key, "Member join request declined.", async () => {
      const response = await apiFetch(`${GROUPS_URL}/${groupId}/members/${membershipId}/reject`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` }
      });
      await readResponse(response);
    });
  }

  function cosignLoan(groupId, loanId) {
    const key = `cosign-loan-${loanId}`;
    return submitAction(key, "Group-backed loan application co-signed.", async () => {
      const response = await apiFetch(`${GROUPS_URL}/${groupId}/loans/${loanId}/co-sign`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` }
      });
      await readResponse(response);
    });
  }

  function joinGroup(group) {
    const key = `join-${group.id}`;
    return submitAction(key, `Join request sent to ${group.name}.`, async () => {
      const response = await apiFetch(`${GROUPS_URL}/${group.id}/join`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` }
      });
      await readResponse(response);
    });
  }

  function contribute(group) {
    const amount = contributions[group.id];
    const key = `contribute-${group.id}`;
    return submitAction(key, `Contribution sent to ${group.name}.`, async () => {
      const response = await apiFetch(`${GROUPS_URL}/${group.id}/contribute`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ amount })
      });
      await readResponse(response);
      setContributions((current) => ({ ...current, [group.id]: "" }));
    });
  }

  function applyForGroupLoan(group) {
    const form = loanForms[group.id] || { amount: "", interestRate: "", termMonths: "6" };
    const requestedAmount = Number(form.amount);
    if (maxLoanLimit === null) {
      setError("Your credit limit is still loading. Please wait and try again.");
      return;
    }
    if (requestedAmount > maxLoanLimit) {
      setError(`The requested amount exceeds your maximum eligible loan of ${formatCurrency(maxLoanLimit)}.`);
      return;
    }
    const key = `loan-${group.id}`;
    return submitAction(key, `Group-backed loan request submitted for ${group.name}.`, async () => {
      const response = await apiFetch(`${GROUPS_URL}/${group.id}/loans/apply`, {
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
      await readResponse(response);
      setLoanForms((current) => ({
        ...current,
        [group.id]: { amount: "", interestRate: "", termMonths: "6" }
      }));
    });
  }

  async function createGroup(event) {
    event.preventDefault();
    return submitAction("create", "Your group has been created.", async () => {
      const response = await apiFetch(`${GROUPS_URL}/create`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(newGroup)
      });
      await readResponse(response);
      setNewGroup({ name: "", description: "" });
    });
  }

  function updateLoanForm(groupId, event) {
    const { name, value } = event.target;
    if (name === "amount" && (maxLoanLimit === null || Number(value) <= maxLoanLimit)) {
      setError("");
    }
    setLoanForms((current) => ({
      ...current,
      [groupId]: {
        ...(current[groupId] || { amount: "", interestRate: "", termMonths: "6" }),
        [name]: value
      }
    }));
  }

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-5 pb-16 pt-32 text-slate-100 sm:px-8">
      <header className="mb-8 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-2 text-xs font-bold uppercase tracking-[.18em] text-emerald-400">Shared goals, shared growth</p>
          <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Group lending</h1>
          <p className="mt-2 text-sm text-slate-400">Build savings together and request group-backed loans.</p>
        </div>
        <span className="status-badge status-active self-start sm:self-auto"><Users size={14} /> {groups.length} groups</span>
      </header>

      {error && <p className="mb-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-300" role="alert">{error}</p>}
      {message && <p className="mb-4 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-300" role="status">{message}</p>}
      {creditLimitError && <p className="mb-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-300" role="alert">{creditLimitError}</p>}

      <section className="glass-panel mb-8" aria-labelledby="create-group-title">
        <div className="mb-5 flex items-center gap-3"><span className="icon-tile"><Plus /></span><div><h2 className="text-lg font-semibold" id="create-group-title">Create a group</h2><p className="mt-1 text-xs text-slate-500">Start a savings circle with your community</p></div></div>
        <form className="grid gap-4 md:grid-cols-2" onSubmit={createGroup}>
          <label className="grid gap-2 text-xs font-semibold text-slate-300" htmlFor="group-name">Group name
          <input
            className="field"
            id="group-name"
            maxLength="100"
            required
            value={newGroup.name}
            onChange={(event) => setNewGroup((current) => ({ ...current, name: event.target.value }))}
          /></label>
          <label className="grid gap-2 text-xs font-semibold text-slate-300 md:row-span-2" htmlFor="group-description">Description
          <textarea
            className="field min-h-28 resize-y"
            id="group-description"
            maxLength="1000"
            value={newGroup.description}
            onChange={(event) => setNewGroup((current) => ({ ...current, description: event.target.value }))}
          /></label>
          <button className="primary-button justify-self-start" type="submit" disabled={working !== ""}>
            {working === "create" ? "Creating..." : "Create group"}
          </button>
        </form>
      </section>

      <section aria-labelledby="active-groups-title">
        <div className="mb-5 flex items-end justify-between"><div><h2 className="text-xl font-semibold" id="active-groups-title">Active groups</h2><p className="mt-1 text-sm text-slate-500">Membership, savings, and group loan status</p></div></div>
        {loading ? <p className="glass-panel text-sm text-slate-400" role="status">Loading active groups...</p> : groups.length === 0
          ? <p className="glass-panel text-center text-sm text-slate-400">No active groups yet. Create one to get started.</p>
          : <div className="grid gap-5 lg:grid-cols-2">{groups.map((group) => {
            const membership = membershipFor(group);
            const isMember = membership?.status === "ACTIVE";
            const isPending = membership?.status === "PENDING";
            const isGroupLeader = group.leaderId === user?.id || membership?.role === "LEADER";
            const canRequestLoan = isMember && !isGroupLeader;
            const pendingMembers = group.members?.filter((m) => m.status === "PENDING") || [];
            const loanForm = loanForms[group.id] || {
              amount: "",
              interestRate: "",
              termMonths: "6"
            };
            return (
              <article className="glass-panel flex flex-col" key={group.id}>
                {isGroupLeader && pendingMembers.length > 0 && (
                  <div className="mb-4 flex items-center justify-between rounded-xl border border-amber-500/30 bg-amber-500/10 p-3 text-xs text-amber-300 animate-pulse">
                    <span className="flex items-center gap-1.5 font-medium">
                      <AlertCircle size={15} /> Action required: {pendingMembers.length} member request(s) awaiting your approval
                    </span>
                    <span className="rounded bg-amber-500/20 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider">
                      Leader Review
                    </span>
                  </div>
                )}
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <h3 className="text-lg font-semibold">{group.name}</h3>
                    <p className="mt-1 text-sm leading-relaxed text-slate-400">{group.description || "No description provided."}</p>
                  </div>
                  <span className={`status-badge ${isMember ? "status-active" : isPending ? "status-pending" : "status-neutral"}`}>
                    {isGroupLeader ? "GROUP LEADER" : (membership?.status || "NOT A MEMBER")}
                  </span>
                </div>
                <dl className="my-5 grid grid-cols-2 gap-3">
                  <div className="rounded-xl border border-slate-800 bg-slate-950/40 p-3">
                    <dt className="text-xs text-slate-500">Group leader</dt>
                    <dd className="mt-1 text-sm font-semibold text-slate-200 flex items-center gap-1.5">
                      <Crown size={14} className="text-amber-400" /> {group.leaderName}
                    </dd>
                  </div>
                  <div className="rounded-xl border border-slate-800 bg-slate-950/40 p-3">
                    <dt className="text-xs text-slate-500">Group savings</dt>
                    <dd className="mt-1 text-sm font-semibold text-emerald-300">{formatCurrency(group.totalSavings)}</dd>
                  </div>
                </dl>
                {group.contributions?.length > 0 && (
                  <div className="mb-5 rounded-xl border border-slate-800 bg-slate-950/30 p-4">
                    <h4 className="mb-3 text-sm font-semibold">Recent contributions</h4>
                    <ul className="grid gap-2">
                      {group.contributions.slice(0, 5).map((contribution) => (
                        <li className="flex flex-wrap items-center justify-between gap-2 text-xs" key={contribution.id}>
                          <span className="text-slate-300">{contribution.memberName}</span>
                          <span className="font-semibold text-emerald-300">{formatCurrency(contribution.amount)}</span>
                          <time className="text-slate-500" dateTime={contribution.contributedAt}>{new Date(contribution.contributedAt).toLocaleString()}</time>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {group.members?.length > 0 && (
                  <div className="mb-5 rounded-xl border border-slate-800 bg-slate-950/30 p-4">
                    <div className="mb-3 flex items-center justify-between gap-2">
                      <h4 className="text-sm font-semibold">Member & co-signer status</h4>
                      <ShieldCheck size={16} className="text-teal-400" />
                    </div>
                    <ul className="grid gap-2 sm:grid-cols-2">
                      {group.members.map((member, index) => {
                        const memberActive = member.status === "ACTIVE";
                        const memberPending = member.status === "PENDING";
                        const mName = member.name || member.userName || (member.userId === user?.id ? user?.name : null) || `Member ${index + 1}`;
                        const initials = mName.split(" ").filter(Boolean).map((n) => n[0]).join("").slice(0, 2).toUpperCase() || "M";
                        return (
                          <li className="flex min-w-0 flex-wrap items-center justify-between gap-2 rounded-lg bg-slate-900/70 px-3 py-2.5" key={member.userId || index}>
                            <div className="flex items-center gap-2 truncate">
                              <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full border border-emerald-500/30 bg-emerald-500/10 text-[10px] font-bold text-emerald-300">
                                {initials}
                              </span>
                              <span className="truncate text-xs text-slate-300 flex items-center gap-1">
                                {mName}
                                {member.role === "LEADER" && <Crown size={12} className="text-amber-400 inline shrink-0" />}
                              </span>
                            </div>
                            <div className="flex items-center gap-1.5">
                              <span className={`status-badge shrink-0 !px-2 !py-0.5 !text-[10px] ${memberActive ? "status-active" : memberPending ? "status-pending" : "status-neutral"}`}>
                                {memberActive ? <CheckCircle2 size={12} /> : memberPending ? <Clock3 size={12} /> : null}
                                {member.status}
                              </span>
                              {isGroupLeader && memberPending && member.membershipId && (
                                <div className="flex gap-1">
                                  <button
                                    className="rounded border border-emerald-500/30 bg-emerald-500/20 px-2 py-0.5 text-[10px] font-semibold text-emerald-300 transition hover:bg-emerald-500/30"
                                    type="button"
                                    disabled={working !== ""}
                                    onClick={() => approveMember(group.id, member.membershipId)}
                                  >
                                    {working === `approve-member-${member.membershipId}` ? "..." : "Approve"}
                                  </button>
                                  <button
                                    className="rounded border border-rose-500/30 bg-rose-500/20 px-2 py-0.5 text-[10px] font-semibold text-rose-300 transition hover:bg-rose-500/30"
                                    type="button"
                                    disabled={working !== ""}
                                    onClick={() => rejectMember(group.id, member.membershipId)}
                                  >
                                    {working === `reject-member-${member.membershipId}` ? "..." : "Reject"}
                                  </button>
                                </div>
                              )}
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                  </div>
                )}
                {isGroupLeader && group.loansAwaitingCosign?.length > 0 && (
                  <div className="mb-5 rounded-xl border border-amber-500/30 bg-amber-500/[0.05] p-4">
                    <div className="mb-3 flex items-center gap-2">
                      <h4 className="text-xs font-bold uppercase tracking-wider text-amber-400">Loans awaiting co-signature</h4>
                    </div>
                    <div className="grid gap-3">
                      {group.loansAwaitingCosign.map((loan) => (
                        <div key={loan.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-800 bg-slate-900/90 p-3">
                          <div>
                            <p className="text-xs font-semibold text-slate-200">{loan.borrowerName} requested {formatCurrency(loan.amount)}</p>
                            <p className="text-[11px] text-slate-400">Interest: {loan.interestRate}% · Term: {loan.termMonths} months</p>
                          </div>
                          <button
                            className="primary-button !py-1.5 !px-3 !text-xs"
                            type="button"
                            disabled={working !== ""}
                            onClick={() => cosignLoan(group.id, loan.id)}
                          >
                            {working === `cosign-loan-${loan.id}` ? "Co-signing..." : "Co-sign loan"}
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                {!membership && (
                  <button
                    className="primary-button mt-auto self-start"
                    type="button"
                    disabled={working !== ""}
                    onClick={() => joinGroup(group)}
                  >
                    {working === `join-${group.id}` ? "Sending request..." : "Request to join"}
                  </button>
                )}
                {isPending && (
                  <p className="mb-4 flex items-center gap-2 rounded-xl border border-amber-500/20 bg-amber-500/10 p-3 text-xs text-amber-300">
                    <Clock3 size={15} /> Your membership request is awaiting the group leader.
                  </p>
                )}
                {isMember && (
                  <div className="mt-auto space-y-4">
                    <form
                      className="grid gap-3 rounded-xl border border-slate-800 bg-slate-950/30 p-4"
                      onSubmit={(event) => {
                        event.preventDefault();
                        contribute(group);
                      }}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <label className="text-xs font-semibold text-slate-300" htmlFor={`contribution-${group.id}`}>Contribute funds</label>
                        <div className="flex items-center gap-1">
                          {[100, 250, 500, 1000].map((amt) => (
                            <button
                              key={amt}
                              type="button"
                              onClick={() => setContributions((curr) => ({ ...curr, [group.id]: String(amt) }))}
                              className="rounded border border-slate-800 bg-slate-900 px-1.5 py-0.5 text-[10px] font-medium text-slate-300 hover:text-emerald-400"
                            >
                              +{amt}
                            </button>
                          ))}
                        </div>
                      </div>
                      <div className="flex gap-2">
                        <input
                          className="field"
                          id={`contribution-${group.id}`}
                          type="number"
                          min="0.01"
                          step="0.01"
                          placeholder="e.g. 500"
                          required
                          value={contributions[group.id] || ""}
                          onChange={(event) => setContributions((current) => ({
                            ...current,
                            [group.id]: event.target.value
                          }))}
                        />
                        <button className="primary-button shrink-0" type="submit" disabled={working !== ""}>
                          {working === `contribute-${group.id}` ? "..." : "Contribute"}
                        </button>
                      </div>
                    </form>
                    {canRequestLoan ? (
                      <form className="grid gap-3 rounded-xl border border-emerald-500/15 bg-emerald-500/[0.03] p-4"
                        onSubmit={(event) => {
                          event.preventDefault();
                          applyForGroupLoan(group);
                        }}
                      >
                        <h4 className="flex items-center gap-2 text-sm font-semibold"><HandCoins size={16} className="text-emerald-400" /> Group-backed loan request</h4>
                        <label className="grid gap-2 text-xs font-semibold text-slate-300" htmlFor={`loan-amount-${group.id}`}><span className="flex items-center justify-between gap-3">Amount<span className="text-emerald-400">{maxLoanLimit === null ? "Max limit: loading…" : `Max limit: ${formatCurrency(maxLoanLimit)}`}</span></span>
                        <input
                          className="field"
                          id={`loan-amount-${group.id}`}
                          name="amount"
                          type="number"
                          min="0.01"
                          max={maxLoanLimit ?? undefined}
                          step="0.01"
                          required
                          value={loanForm.amount}
                          onChange={(event) => updateLoanForm(group.id, event)}
                        /></label>
                        <label className="grid gap-2 text-xs font-semibold text-slate-300" htmlFor={`loan-rate-${group.id}`}>Annual interest rate (%)
                        <input
                          className="field"
                          id={`loan-rate-${group.id}`}
                          name="interestRate"
                          type="number"
                          min="0"
                          max="100"
                          step="0.01"
                          required
                          value={loanForm.interestRate}
                          onChange={(event) => updateLoanForm(group.id, event)}
                        /></label>
                        <label className="grid gap-2 text-xs font-semibold text-slate-300" htmlFor={`loan-term-${group.id}`}>Term (months)
                        <input
                          className="field"
                          id={`loan-term-${group.id}`}
                          name="termMonths"
                          type="number"
                          min="1"
                          max="120"
                          step="1"
                          required
                          value={loanForm.termMonths}
                          onChange={(event) => updateLoanForm(group.id, event)}
                        /></label>
                        <button className="primary-button mt-1" type="submit" disabled={working !== "" || maxLoanLimit === null || maxLoanLimit === 0 || Number(loanForm.amount) > maxLoanLimit}>
                          {working === `loan-${group.id}` ? "Submitting..." : "Request group-backed loan"}
                        </button>
                      </form>
                    ) : (
                      <button type="button" disabled aria-disabled="true" className="secondary-button w-full justify-start whitespace-normal text-left text-xs">
                        Group leaders must co-sign; only other active members may apply
                      </button>
                    )}
                  </div>
                )}
                {!isMember && (
                  <button type="button" disabled aria-disabled="true" className="secondary-button mt-auto w-full justify-start whitespace-normal text-left text-xs">
                    Group-backed loan requests require active membership
                  </button>
                )}
              </article>
            );
          })}</div>}
      </section>
    </main>
  );
}

export default GroupLending;
