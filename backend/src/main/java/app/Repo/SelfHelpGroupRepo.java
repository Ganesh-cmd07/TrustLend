package app.Repo;

import app.models.GroupStatus;
import app.models.SelfHelpGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SelfHelpGroupRepo extends JpaRepository<SelfHelpGroup, Long> {

    List<SelfHelpGroup> findAllByStatusOrderByCreatedAtDescIdDesc(GroupStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select group from SelfHelpGroup group where group.id = :groupId")
    Optional<SelfHelpGroup> findByIdForUpdate(@Param("groupId") Long groupId);
}
