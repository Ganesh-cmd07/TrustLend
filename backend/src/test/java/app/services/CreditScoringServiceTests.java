package app.services;

import app.Repo.GroupMembershipRepo;
import app.Repo.LoanRepo;
import app.Repo.LoanRepaymentRepo;
import app.Repo.WalletRepo;
import app.models.GroupMembership;
import app.models.GroupMembershipStatus;
import app.models.User;
import app.models.Wallet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreditScoringServiceTests {

    @Test
    void calculatesLoanLimitsAtEachCreditScoreTier() {
        assertThat(CreditScoringService.maxEligibleLoanForScore(750))
                .isEqualByComparingTo("100000.00");
        assertThat(CreditScoringService.maxEligibleLoanForScore(650))
                .isEqualByComparingTo("50000.00");
        assertThat(CreditScoringService.maxEligibleLoanForScore(550))
                .isEqualByComparingTo("25000.00");
        assertThat(CreditScoringService.maxEligibleLoanForScore(549))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void describesRequestedAmountLimitAndCreditScoreInValidationMessage() {
        assertThat(CreditScoringService.loanLimitExceededMessage(
                new BigDecimal("100000.00"),
                new BigDecimal("10000.00"),
                580
        )).isEqualTo(
                "Loan amount (₹100,000) exceeds your credit limit of ₹10,000 based on your current credit score of 580."
        );
    }

    @Test
    void calculatesScoreFromMissingWalletAndOverdueInstallmentSafely() {
        var loanRepo = mock(LoanRepo.class);
        var repaymentRepo = mock(LoanRepaymentRepo.class);
        var walletRepo = mock(WalletRepo.class);
        var membershipRepo = mock(GroupMembershipRepo.class);
        var user = new User();
        user.setId(91L);
        when(loanRepo.findAllForUser(91L)).thenReturn(List.of());
        when(repaymentRepo.countDistinctByLoan_Borrower_IdAndStatus(91L, app.models.RepaymentStatus.OVERDUE)).thenReturn(1L);
        var wallet = new Wallet(user);
        wallet.setBalance(new BigDecimal("1000.00"));
        when(walletRepo.findByUser_Id(91L)).thenReturn(Optional.of(wallet));
        when(membershipRepo.findAllByUser_IdAndStatusOrderByJoinedAtAscIdAsc(91L, GroupMembershipStatus.ACTIVE))
                .thenReturn(List.of(mock(GroupMembership.class)));

        var result = new CreditScoringService(loanRepo, repaymentRepo, walletRepo, membershipRepo)
                .calculateCreditScore(user);

        assertThat(result.get("creditScore")).isEqualTo(500);
        assertThat(result.get("eligible")).isEqualTo(false);
        var factors = (java.util.Map<?, ?>) result.get("factors");
        assertThat(factors.get("overdueLoans")).isEqualTo(1L);
        assertThat(factors.get("activeGroupMember")).isEqualTo(true);
    }
}
