package app.services;

import app.Repo.CreditScoreSnapshotRepo;
import app.data.CreditScoreHistoryResponse;
import app.models.CreditScoreSnapshot;
import app.models.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class CreditScoreHistoryService {
    private final CreditScoreSnapshotRepo snapshotRepo;

    public CreditScoreHistoryService(CreditScoreSnapshotRepo snapshotRepo) {
        this.snapshotRepo = snapshotRepo;
    }

    @Transactional
    public void recordIfChanged(User user, Map<String, Object> scoreData) {
        Map<?, ?> factors = (Map<?, ?>) scoreData.get("factors");
        CreditScoreSnapshot.MapValues values = new CreditScoreSnapshot.MapValues(
                ((Number) scoreData.get("creditScore")).intValue(),
                String.valueOf(scoreData.get("riskTier")),
                Boolean.TRUE.equals(scoreData.get("eligible")),
                decimal(scoreData.get("maxEligibleLoan")),
                ((Number) factors.get("repaidLoans")).longValue(),
                ((Number) factors.get("overdueLoans")).longValue(),
                decimal(factors.get("walletBalance")),
                Boolean.TRUE.equals(factors.get("activeGroupMember"))
        );
        snapshotRepo.findFirstByUser_IdOrderByRecordedAtDescIdDesc(user.getId())
                .filter(snapshot -> snapshot.hasSameValues(values))
                .orElseGet(() -> snapshotRepo.save(new CreditScoreSnapshot(user, values)));
    }

    @Transactional(readOnly = true)
    public List<CreditScoreHistoryResponse> getHistory(Long userId) {
        return snapshotRepo.findTop20ByUser_IdOrderByRecordedAtDescIdDesc(userId)
                .stream().map(CreditScoreHistoryResponse::from).toList();
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        return new BigDecimal(String.valueOf(value));
    }
}
