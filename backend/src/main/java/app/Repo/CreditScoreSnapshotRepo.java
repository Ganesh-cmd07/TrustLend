package app.Repo;

import app.models.CreditScoreSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditScoreSnapshotRepo extends JpaRepository<CreditScoreSnapshot, Long> {
    Optional<CreditScoreSnapshot> findFirstByUser_IdOrderByRecordedAtDescIdDesc(Long userId);
    List<CreditScoreSnapshot> findTop20ByUser_IdOrderByRecordedAtDescIdDesc(Long userId);
}
