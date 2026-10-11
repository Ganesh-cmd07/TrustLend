package app.Repo;

import app.models.GroupMembership;
import app.models.GroupMembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMembershipRepo extends JpaRepository<GroupMembership, Long> {

    Optional<GroupMembership> findByGroup_IdAndUser_Id(Long groupId, Long userId);

    List<GroupMembership> findAllByGroup_IdAndStatusOrderByJoinedAtAscIdAsc(
            Long groupId,
            GroupMembershipStatus status
    );

    List<GroupMembership> findAllByUser_IdAndStatusOrderByJoinedAtAscIdAsc(
            Long userId,
            GroupMembershipStatus status
    );
}
