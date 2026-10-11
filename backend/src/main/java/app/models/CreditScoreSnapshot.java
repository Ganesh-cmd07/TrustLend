package app.models;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_score_history", indexes = {
        @Index(name = "idx_credit_score_history_user_recorded", columnList = "user_id,recorded_at,id")
})
public class CreditScoreSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, length = 16)
    private String riskTier;

    @Column(nullable = false)
    private boolean eligible;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal maxEligibleLoan;

    @Column(nullable = false)
    private long repaidLoans;

    @Column(nullable = false)
    private long overdueLoans;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal walletBalance;

    @Column(nullable = false)
    private boolean activeGroupMember;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    protected CreditScoreSnapshot() {}

    public CreditScoreSnapshot(User user, MapValues values) {
        this.user = user;
        this.score = values.score();
        this.riskTier = values.riskTier();
        this.eligible = values.eligible();
        this.maxEligibleLoan = values.maxEligibleLoan();
        this.repaidLoans = values.repaidLoans();
        this.overdueLoans = values.overdueLoans();
        this.walletBalance = values.walletBalance();
        this.activeGroupMember = values.activeGroupMember();
    }

    @PrePersist
    void onCreate() { recordedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public int getScore() { return score; }
    public String getRiskTier() { return riskTier; }
    public boolean isEligible() { return eligible; }
    public BigDecimal getMaxEligibleLoan() { return maxEligibleLoan; }
    public long getRepaidLoans() { return repaidLoans; }
    public long getOverdueLoans() { return overdueLoans; }
    public BigDecimal getWalletBalance() { return walletBalance; }
    public boolean isActiveGroupMember() { return activeGroupMember; }
    public LocalDateTime getRecordedAt() { return recordedAt; }

    public boolean hasSameValues(MapValues values) {
        return score == values.score()
                && riskTier.equals(values.riskTier())
                && eligible == values.eligible()
                && maxEligibleLoan.compareTo(values.maxEligibleLoan()) == 0
                && repaidLoans == values.repaidLoans()
                && overdueLoans == values.overdueLoans()
                && walletBalance.compareTo(values.walletBalance()) == 0
                && activeGroupMember == values.activeGroupMember();
    }

    public record MapValues(int score, String riskTier, boolean eligible, BigDecimal maxEligibleLoan,
                            long repaidLoans, long overdueLoans, BigDecimal walletBalance,
                            boolean activeGroupMember) {}
}
