package app.Repo;

import app.models.LenderRequest;
import app.models.LenderRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LenderRequestRepo extends JpaRepository<LenderRequest, Long> {

    Optional<LenderRequest> findFirstByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    List<LenderRequest> findAllByStatusOrderByCreatedAtAscIdAsc(LenderRequestStatus status);
}
