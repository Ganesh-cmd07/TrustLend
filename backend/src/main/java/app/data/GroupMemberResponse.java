package app.data;

import app.models.GroupMembership;

import java.time.LocalDateTime;

public record GroupMemberResponse(
        Long membershipId,
        Long userId,
        String name,
        String role,
        String status,
        LocalDateTime joinedAt
) {

    public static GroupMemberResponse from(GroupMembership membership) {
        return new GroupMemberResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getName(),
                membership.getRole().name(),
                membership.getStatus().name(),
                membership.getJoinedAt()
        );
    }
}
