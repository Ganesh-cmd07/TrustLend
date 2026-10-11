package app.data;

import app.models.SelfHelpGroup;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GroupResponse(
        Long id,
        String name,
        String description,
        Long leaderId,
        String leaderName,
        BigDecimal totalSavings,
        String status,
        LocalDateTime createdAt,
        List<GroupMemberResponse> members,
        List<GroupContributionResponse> contributions,
        List<LoanResponse> loansAwaitingCosign
) {

    public static GroupResponse from(
            SelfHelpGroup group,
            List<GroupMemberResponse> members,
            List<GroupContributionResponse> contributions,
            List<LoanResponse> loansAwaitingCosign) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getLeader().getId(),
                group.getLeader().getName(),
                group.getTotalSavings(),
                group.getStatus().name(),
                group.getCreatedAt(),
                members,
                contributions,
                loansAwaitingCosign
        );
    }
}
