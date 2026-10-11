package app.data;

import app.models.GroupContribution;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GroupContributionResponse(
        Long id,
        Long userId,
        String memberName,
        BigDecimal amount,
        LocalDateTime contributedAt
) {

    public static GroupContributionResponse from(GroupContribution contribution) {
        return new GroupContributionResponse(
                contribution.getId(),
                contribution.getUser().getId(),
                contribution.getUser().getName(),
                contribution.getAmount(),
                contribution.getContributedAt()
        );
    }
}
