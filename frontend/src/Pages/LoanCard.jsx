import { AlertTriangle, CheckCircle2, CircleDollarSign, Clock, TrendingUp } from "lucide-react";
import { formatCurrency } from "../utils/formatCurrency";

function LoanCard({ loan, actionLabel, actionDisabled, onAction }) {
  const statusClass = ["APPROVED", "FUNDED", "ACTIVE", "COSIGNED"].includes(loan.status)
    ? "status-active"
    : loan.status === "REJECTED" ? "status-rejected" : "status-pending";

  const totalInstallments = loan.repayments?.length || 0;
  const paidRepayments = loan.repayments?.filter((r) => r.status === "PAID") || [];
  const paidCount = paidRepayments.length;
  const paidAmount = paidRepayments.reduce((sum, r) => sum + Number(r.amount || 0), 0);
  const totalRepayable = Number(loan.totalRepayable || 0);
  const remainingAmount = Math.max(0, totalRepayable - paidAmount);
  const progressPercent = totalInstallments > 0 ? Math.round((paidCount / totalInstallments) * 100) : 0;

  const totalInterest = Math.max(0, totalRepayable - Number(loan.amount || 0));

  return (
    <article className="mb-4 rounded-2xl border border-slate-800 bg-slate-950/40 p-5 transition hover:-translate-y-0.5 hover:border-emerald-500/30 sm:p-6 shadow-lg shadow-black/20">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="icon-tile h-10 w-10">
            <CircleDollarSign size={19} />
          </span>
          <div>
            <h3 className="text-base font-semibold">Loan #{loan.id}</h3>
            {loan.borrowerName && <p className="text-xs text-slate-400">Borrower: {loan.borrowerName}</p>}
          </div>
        </div>
        <div className="flex items-center gap-2">
          {totalInterest > 0 && (
            <span className="hidden items-center gap-1 rounded-full border border-teal-500/30 bg-teal-500/10 px-2.5 py-0.5 text-xs font-medium text-teal-300 sm:inline-flex">
              <TrendingUp size={12} /> +{formatCurrency(totalInterest)} yield
            </span>
          )}
          <span className={`status-badge ${statusClass}`}>
            {loan.status}
          </span>
        </div>
      </div>

      <dl className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <dt className="text-xs text-slate-500">Principal</dt>
          <dd className="mt-1 font-semibold text-slate-200">{formatCurrency(loan.amount)}</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Interest rate</dt>
          <dd className="mt-1 font-semibold text-slate-200">{Number(loan.interestRate).toFixed(2)}% per year</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Term</dt>
          <dd className="mt-1 font-semibold text-slate-200">{loan.termMonths} months</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Total repayable</dt>
          <dd className="mt-1 font-semibold text-slate-200">{formatCurrency(loan.totalRepayable)}</dd>
        </div>
        {loan.lenderName && (
          <div>
            <dt className="text-xs text-slate-500">Lender</dt>
            <dd className="mt-1 font-semibold text-slate-200">{loan.lenderName}</dd>
          </div>
        )}
      </dl>

      {/* Repayment Progress Bar */}
      {totalInstallments > 0 && (
        <div className="mt-5 rounded-xl border border-slate-800/80 bg-slate-900/60 p-4">
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
            <span className="font-semibold text-slate-300">
              Repayment Progress: <span className="text-emerald-400">{progressPercent}%</span> ({paidCount} of {totalInstallments} paid)
            </span>
            <span className="text-slate-400">
              Remaining: <span className="font-medium text-slate-200">{formatCurrency(remainingAmount)}</span>
            </span>
          </div>
          <div className="mt-2.5 h-2.5 w-full overflow-hidden rounded-full bg-slate-800">
            <div
              className="h-full rounded-full bg-gradient-to-r from-emerald-500 to-teal-400 transition-all duration-500"
              style={{ width: `${progressPercent}%` }}
              role="progressbar"
              aria-valuenow={progressPercent}
              aria-valuemin="0"
              aria-valuemax="100"
            />
          </div>
        </div>
      )}

      {loan.status === "REJECTED" && loan.reviewReason && (
        <p className="mt-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-sm text-rose-200">
          Application decision: {loan.reviewReason}{loan.reviewedAt ? ` · ${new Date(loan.reviewedAt).toLocaleDateString()}` : ""}
        </p>
      )}

      {totalInstallments > 0 && (
        <div className="mt-5 border-t border-slate-800 pt-4">
          <h4 className="mb-3 text-sm font-semibold">Repayment schedule</h4>
          <ol className="grid gap-2 text-xs sm:grid-cols-2">
            {loan.repayments.map((repayment) => {
              const isPaid = repayment.status === "PAID";
              const isOverdue = repayment.status === "OVERDUE";
              return (
                <li
                  className={`flex items-center justify-between rounded-xl px-3.5 py-2.5 transition border ${
                    isPaid
                      ? "border-emerald-500/20 bg-emerald-500/5 text-slate-300"
                      : isOverdue
                      ? "border-rose-500/30 bg-rose-500/10 text-rose-200"
                      : "border-slate-800 bg-slate-900/80 text-slate-400"
                  }`}
                  key={repayment.id}
                >
                  <div>
                    <span className="font-semibold text-slate-200">#{repayment.installmentNumber}</span>: {formatCurrency(repayment.amount)}
                    <span className="mt-0.5 block text-[11px] text-slate-500">Due {repayment.dueDate}</span>
                  </div>
                  <span
                    className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                      isPaid
                        ? "bg-emerald-500/20 text-emerald-300"
                        : isOverdue
                        ? "bg-rose-500/20 text-rose-300"
                        : "bg-amber-500/15 text-amber-300"
                    }`}
                  >
                    {isPaid ? <CheckCircle2 size={11} /> : isOverdue ? <AlertTriangle size={11} /> : <Clock size={11} />}
                    {repayment.status}
                  </span>
                </li>
              );
            })}
          </ol>
        </div>
      )}

      {actionLabel && (
        <button className="primary-button mt-4" type="button" disabled={actionDisabled} onClick={onAction}>
          {actionLabel}
        </button>
      )}
    </article>
  );
}

export default LoanCard;
