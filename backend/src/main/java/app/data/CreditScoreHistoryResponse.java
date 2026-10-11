package app.data;

import app.models.CreditScoreSnapshot;

import java.util.Map;

public record CreditScoreHistoryResponse(
        Long id,
        int creditScore,
        String riskTier,
        boolean eligible,
        java.math.BigDecimal maxEligibleLoan,
        Map<String, Object> factors,
        java.time.LocalDateTime recordedAt
) {
    public static CreditScoreHistoryResponse from(CreditScoreSnapshot snapshot) {
        return new CreditScoreHistoryResponse(
                snapshot.getId(), snapshot.getScore(), snapshot.getRiskTier(), snapshot.isEligible(),
                snapshot.getMaxEligibleLoan(), Map.of(
                        "repaidLoans", snapshot.getRepaidLoans(),
                        "overdueLoans", snapshot.getOverdueLoans(),
                        "walletBalance", snapshot.getWalletBalance(),
                        "activeGroupMember", snapshot.isActiveGroupMember()),
                snapshot.getRecordedAt());
    }
}
