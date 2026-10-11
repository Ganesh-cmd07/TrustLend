package app.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "group_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_membership_group_user",
                columnNames = {"group_id", "user_id"}
        ),
        indexes = @Index(name = "idx_group_memberships_group_status", columnList = "group_id,status")
)
public class GroupMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private SelfHelpGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GroupMembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GroupMembershipStatus status = GroupMembershipStatus.PENDING;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    protected GroupMembership() {
    }

    public GroupMembership(SelfHelpGroup group, User user, GroupMembershipRole role,
            GroupMembershipStatus status) {
        this.group = group;
        this.user = user;
        this.role = role;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        joinedAt = LocalDateTime.now();
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

    public GroupMembershipRole getRole() {
        return role;
    }

    public GroupMembershipStatus getStatus() {
        return status;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void approve() {
        status = GroupMembershipStatus.ACTIVE;
    }

    public void reject() {
        status = GroupMembershipStatus.REJECTED;
    }

    public void requestAgain() {
        status = GroupMembershipStatus.PENDING;
    }
}
