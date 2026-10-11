package app.models;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 16, updatable = false)
    private WalletTransactionType type;

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "transaction_time", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    protected WalletTransaction() {
    }

    public WalletTransaction(
            Wallet wallet,
            BigDecimal amount,
            WalletTransactionType type,
            BigDecimal balanceAfter) {
        this.wallet = wallet;
        this.amount = amount;
        this.type = type;
        this.balanceAfter = balanceAfter;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public WalletTransactionType getType() {
        return type;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
