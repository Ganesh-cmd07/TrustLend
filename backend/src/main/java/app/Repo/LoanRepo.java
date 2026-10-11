package app.Repo;

import app.models.Loan;
import app.models.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface LoanRepo extends JpaRepository<Loan, Long> {

    @Query("""
            select loan from Loan loan
            where loan.status in :statuses
              and (loan.group is null or loan.groupApprovedBy is not null)
            order by loan.createdAt asc, loan.id asc
            """)
    List<Loan> findAvailableForMarketplace(@Param("statuses") List<LoanStatus> statuses);

    @Query("""
            select loan from Loan loan
            where loan.group.id = :groupId
              and loan.groupApprovedBy is null
              and loan.status in :statuses
            order by loan.createdAt asc, loan.id asc
            """)
    List<Loan> findAwaitingGroupCosign(
            @Param("groupId") Long groupId,
            @Param("statuses") List<LoanStatus> statuses
    );

    List<Loan> findAllByStatusOrderByCreatedAtAscIdAsc(LoanStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select loan from Loan loan where loan.id = :loanId")
    Optional<Loan> findByIdForUpdate(@Param("loanId") Long loanId);

    @Query("""
            select loan from Loan loan
            where loan.borrower.id = :userId or loan.lender.id = :userId
            order by loan.createdAt desc, loan.id desc
            """)
    List<Loan> findAllForUser(@Param("userId") Long userId);
}
