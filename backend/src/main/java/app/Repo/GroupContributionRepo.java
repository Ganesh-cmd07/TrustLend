package app.Repo;

import app.models.GroupContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupContributionRepo extends JpaRepository<GroupContribution, Long> {

    List<GroupContribution> findAllByGroup_IdOrderByContributedAtDescIdDesc(Long groupId);
}
