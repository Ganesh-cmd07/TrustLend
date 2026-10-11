import { CircleDollarSign } from "lucide-react";
import { formatCurrency } from "../utils/formatCurrency";

function LoanCard({ loan, actionLabel, actionDisabled, onAction }) {
  const statusClass = ["APPROVED", "FUNDED", "ACTIVE", "COSIGNED"].includes(loan.status)
    ? "status-active"
    : loan.status === "REJECTED" ? "status-rejected" : "status-pending";

  return (
    <article className="mb-4 rounded-2xl border border-slate-800 bg-slate-950/40 p-5 transition hover:-translate-y-0.5 hover:border-emerald-500/30 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3"><span className="icon-tile h-10 w-10"><CircleDollarSign size={19} /></span><h3 className="text-base font-semibold">Loan #{loan.id}</h3></div>
        <span className={`status-badge ${statusClass}`}>
          {loan.status}
        </span>
      </div>
      <dl className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div><dt className="text-xs text-slate-500">Principal</dt><dd className="mt-1 font-semibold text-slate-200">{formatCurrency(loan.amount)}</dd></div>
        <div><dt className="text-xs text-slate-500">Interest rate</dt><dd className="mt-1 font-semibold text-slate-200">{Number(loan.interestRate).toFixed(2)}% per year</dd></div>
        <div><dt className="text-xs text-slate-500">Term</dt><dd className="mt-1 font-semibold text-slate-200">{loan.termMonths} months</dd></div>
        <div><dt className="text-xs text-slate-500">Total repayable</dt><dd className="mt-1 font-semibold text-slate-200">{formatCurrency(loan.totalRepayable)}</dd></div>
        {loan.borrowerName && <div><dt className="text-xs text-slate-500">Borrower</dt><dd className="mt-1 font-semibold text-slate-200">{loan.borrowerName}</dd></div>}
        {loan.lenderName && <div><dt className="text-xs text-slate-500">Lender</dt><dd className="mt-1 font-semibold text-slate-200">{loan.lenderName}</dd></div>}
      </dl>
      {loan.status === "REJECTED" && loan.reviewReason && (
        <p className="mt-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-sm text-rose-200">Application decision: {loan.reviewReason}{loan.reviewedAt ? ` · ${new Date(loan.reviewedAt).toLocaleDateString()}` : ""}</p>
      )}
      {loan.repayments.length > 0 && (
        <div className="mt-5 border-t border-slate-800 pt-4">
          <h4 className="mb-3 text-sm font-semibold">Repayment schedule</h4>
          <ol className="grid gap-2 text-xs text-slate-400 sm:grid-cols-2">
            {loan.repayments.map((repayment) => (
              <li className="rounded-xl bg-slate-900 px-3 py-2.5" key={repayment.id}>
                <span className="font-semibold text-slate-200">Installment {repayment.installmentNumber}</span>: {formatCurrency(repayment.amount)}
                <span className="mt-1 block text-slate-500">Principal {formatCurrency(repayment.principalAmount)} · Interest {formatCurrency(repayment.interestAmount)}</span>
                {" — "}due {repayment.dueDate} ({repayment.status.toLowerCase()})
              </li>
            ))}
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
