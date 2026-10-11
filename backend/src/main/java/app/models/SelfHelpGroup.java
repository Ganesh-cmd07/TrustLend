package app.models;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "self_help_groups")
public class SelfHelpGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leader_id", nullable = false, updatable = false)
    private User leader;

    @Column(name = "total_savings", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalSavings = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GroupStatus status = GroupStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SelfHelpGroup() {
    }

    public SelfHelpGroup(String name, String description, User leader) {
        this.name = name;
        this.description = description;
        this.leader = leader;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (totalSavings == null) {
            totalSavings = BigDecimal.ZERO;
        }
        if (status == null) {
            status = GroupStatus.ACTIVE;
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public User getLeader() {
        return leader;
    }

    public BigDecimal getTotalSavings() {
        return totalSavings;
    }

    public GroupStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void addSavings(BigDecimal amount) {
        totalSavings = totalSavings.add(amount);
    }
}
