package app.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "loan_application_reviews")
public class LoanApplicationReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, updatable = false)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false, updatable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 16, updatable = false)
    private LoanStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 16, updatable = false)
    private LoanStatus newStatus;

    @Column(length = 1000, updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LoanApplicationReview() { }

    public LoanApplicationReview(Loan loan, User reviewer, LoanStatus previousStatus,
                                 LoanStatus newStatus, String reason) {
        this.loan = loan;
        this.reviewer = reviewer;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Loan getLoan() { return loan; }
    public User getReviewer() { return reviewer; }
    public LoanStatus getPreviousStatus() { return previousStatus; }
    public LoanStatus getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
