package app.models;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.math.RoundingMode;

@Entity
@Table(name = "loan_repayments", indexes = {
        @Index(name = "idx_loan_repayments_loan_due", columnList = "loan_id,due_date,id")
})
public class LoanRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, updatable = false)
    private Loan loan;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "principal_amount", precision = 15, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "interest_amount", precision = 15, scale = 2)
    private BigDecimal interestAmount;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RepaymentStatus status = RepaymentStatus.PENDING;

    @Column(name = "installment_number", nullable = false, updatable = false)
    private int installmentNumber;

    protected LoanRepayment() {
    }

    public LoanRepayment(Loan loan, BigDecimal amount, LocalDate dueDate, int installmentNumber) {
        this(loan, amount, null, null, dueDate, installmentNumber);
    }

    public LoanRepayment(Loan loan, BigDecimal amount, BigDecimal principalAmount,
                         BigDecimal interestAmount, LocalDate dueDate, int installmentNumber) {
        this.loan = loan;
        this.amount = amount;
        this.principalAmount = principalAmount;
        this.interestAmount = interestAmount;
        this.dueDate = dueDate;
        this.installmentNumber = installmentNumber;
        this.status = RepaymentStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Loan getLoan() {
        return loan;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPrincipalAmount() {
        if (principalAmount != null) return principalAmount;
        BigDecimal regularPrincipal = loan.getAmount()
                .divide(BigDecimal.valueOf(loan.getTermMonths()), 2, RoundingMode.DOWN);
        if (installmentNumber == loan.getTermMonths()) {
            return loan.getAmount().subtract(regularPrincipal.multiply(
                    BigDecimal.valueOf(loan.getTermMonths() - 1L)));
        }
        return regularPrincipal;
    }

    public BigDecimal getInterestAmount() {
        return interestAmount != null ? interestAmount : amount.subtract(getPrincipalAmount());
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getPaidDate() {
        return paidDate;
    }

    public RepaymentStatus getStatus() {
        return status;
    }

    public int getInstallmentNumber() {
        return installmentNumber;
    }

    public void markPaid(LocalDate date) {
        paidDate = date;
        status = RepaymentStatus.PAID;
    }

    public void markOverdue() {
        status = RepaymentStatus.OVERDUE;
    }
}
