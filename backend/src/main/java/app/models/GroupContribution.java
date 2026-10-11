package app.models;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_contributions", indexes = {
        @Index(name = "idx_group_contributions_group_time", columnList = "group_id,contributed_at,id")
})
public class GroupContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private SelfHelpGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "contributed_at", nullable = false, updatable = false)
    private LocalDateTime contributedAt;

    protected GroupContribution() {
    }

    public GroupContribution(SelfHelpGroup group, User user, BigDecimal amount) {
        this.group = group;
        this.user = user;
        this.amount = amount;
    }

    @PrePersist
    protected void onCreate() {
        contributedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public SelfHelpGroup getGroup() {
        return group;
    }

    public User getUser() {
        return user;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getContributedAt() {
        return contributedAt;
    }
}
