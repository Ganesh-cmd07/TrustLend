package app.Repo;

import app.models.LoanRepayment;
import app.models.RepaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoanRepaymentRepo extends JpaRepository<LoanRepayment, Long> {

    List<LoanRepayment> findAllByLoan_IdOrderByInstallmentNumberAsc(Long loanId);

    Optional<LoanRepayment> findFirstByLoan_IdAndStatusInOrderByInstallmentNumberAsc(
            Long loanId,
            List<RepaymentStatus> statuses
    );

    boolean existsByLoan_IdAndStatusIn(Long loanId, List<RepaymentStatus> statuses);

    List<LoanRepayment> findAllByStatusAndDueDateBefore(RepaymentStatus status, LocalDate date);

    long countDistinctByLoan_Borrower_IdAndStatus(Long borrowerId, RepaymentStatus status);
}

