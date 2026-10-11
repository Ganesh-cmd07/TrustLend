package app.models;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrower_id", nullable = false, updatable = false)
    private User borrower;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lender_id")
    private User lender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", updatable = false)
    private SelfHelpGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_approved_by_id")
    private User groupApprovedBy;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LoanStatus status = LoanStatus.PENDING;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "review_reason", length = 1000)
    private String reviewReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Loan() {
    }

    public Loan(User borrower, BigDecimal amount, BigDecimal interestRate, int termMonths) {
        this(borrower, amount, interestRate, termMonths, null);
    }

    public Loan(
            User borrower,
            BigDecimal amount,
            BigDecimal interestRate,
            int termMonths,
            SelfHelpGroup group) {
        this.borrower = borrower;
        this.amount = amount;
        this.interestRate = interestRate;
        this.termMonths = termMonths;
        this.group = group;
        this.status = LoanStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getBorrower() {
        return borrower;
    }

    public User getLender() {
        return lender;
    }

    public SelfHelpGroup getGroup() {
        return group;
    }

    public User getGroupApprovedBy() {
        return groupApprovedBy;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void review(User reviewer, boolean approved, String reason) {
        if (status != LoanStatus.SUBMITTED) {
            throw new IllegalStateException("Only submitted applications can be reviewed");
        }
        status = approved ? LoanStatus.APPROVED : LoanStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = LocalDateTime.now();
        reviewReason = approved ? null : reason;
    }

    public void submitApplication() {
        if (status != LoanStatus.PENDING) {
            throw new IllegalStateException("Only a new application can be submitted");
        }
        status = LoanStatus.SUBMITTED;
    }

    public void fund(User lender) {
        this.lender = lender;
        this.status = LoanStatus.FUNDED;
    }

    public void markActive() {
        this.status = LoanStatus.ACTIVE;
    }

    public void markRepaid() {
        this.status = LoanStatus.REPAID;
    }

    public boolean isGroupCosigned() {
        return group == null || groupApprovedBy != null;
    }

    public void cosignGroupLoan(User groupLeader) {
        this.groupApprovedBy = groupLeader;
    }
}
