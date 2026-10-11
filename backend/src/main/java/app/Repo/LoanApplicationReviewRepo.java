package app.Repo;

import app.models.LoanApplicationReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanApplicationReviewRepo extends JpaRepository<LoanApplicationReview, Long> {
    List<LoanApplicationReview> findAllByLoan_IdOrderByCreatedAtAscIdAsc(Long loanId);
    List<LoanApplicationReview> findTop20ByOrderByCreatedAtDescIdDesc();
}
