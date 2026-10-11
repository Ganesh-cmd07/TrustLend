package app.services;

import app.Repo.LoanRepo;
import app.Repo.LoanRepaymentRepo;
import app.Repo.UserRepo;
import app.Repo.WalletRepo;
import app.Repo.WalletTransactionRepo;
import app.data.LoanApplicationRequest;
import app.exception.ApiException;
import app.models.Loan;
import app.models.LoanStatus;
import app.models.User;
import app.models.UserRole;
import app.models.Wallet;
import app.models.WalletTransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoanServiceTests {

    @Autowired
    private LoanService loanService;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private WalletRepo walletRepo;

    @Autowired
    private LoanRepo loanRepo;

    @Autowired
    private LoanRepaymentRepo loanRepaymentRepo;

    @Autowired
    private WalletTransactionRepo walletTransactionRepo;

    @Test
    void fundingTransfersFundsAndCreatesLedgerAndRepayments() {
        User borrower = createUser(UserRole.BORROWER);
        User lender = createUser(UserRole.LENDER);
        Wallet borrowerWallet = walletRepo.saveAndFlush(walletWithBalance(borrower, "5.00"));
        Wallet lenderWallet = walletRepo.saveAndFlush(walletWithBalance(lender, "100.00"));
        Loan loan = loanRepo.saveAndFlush(new Loan(
                borrower,
                new BigDecimal("40.00"),
                new BigDecimal("12.00"),
                4
        ));

        loanService.fund(lender.getEmail(), loan.getId());

        assertThat(walletRepo.findByUserIdForUpdate(lender.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("60.00");
        assertThat(walletRepo.findByUserIdForUpdate(borrower.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("45.00");
        assertThat(loanRepo.findById(loan.getId()).orElseThrow().getStatus())
                .isEqualTo(LoanStatus.FUNDED);
        var schedule = loanRepaymentRepo.findAllByLoan_IdOrderByInstallmentNumberAsc(loan.getId());
        assertThat(schedule).hasSize(4);
        assertThat(schedule.stream().map(app.models.LoanRepayment::getPrincipalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("40.00");
        assertThat(schedule.stream().map(app.models.LoanRepayment::getInterestAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1.60");
        assertThat(schedule.stream().map(app.models.LoanRepayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("41.60");
        assertThat(walletTransactionRepo.findAllByWallet_IdOrderByTimestampDescIdDesc(lenderWallet.getId()))
                .singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getType()).isEqualTo(WalletTransactionType.WITHDRAWAL);
                    assertThat(transaction.getAmount()).isEqualByComparingTo("40.00");
                    assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("60.00");
                });
        assertThat(walletTransactionRepo.findAllByWallet_IdOrderByTimestampDescIdDesc(borrowerWallet.getId()))
                .singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getType()).isEqualTo(WalletTransactionType.DISBURSEMENT);
                    assertThat(transaction.getAmount()).isEqualByComparingTo("40.00");
                    assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("45.00");
                });
    }

    @Test
    void insufficientLenderBalanceLeavesLoanAndWalletsUnchanged() {
        User borrower = createUser(UserRole.BORROWER);
        User lender = createUser(UserRole.LENDER);
        walletRepo.saveAndFlush(walletWithBalance(borrower, "0.00"));
        walletRepo.saveAndFlush(walletWithBalance(lender, "10.00"));
        Loan loan = loanRepo.saveAndFlush(new Loan(
                borrower,
                new BigDecimal("40.00"),
                new BigDecimal("0.00"),
                1
        ));

        assertThatThrownBy(() -> loanService.fund(lender.getEmail(), loan.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Insufficient wallet balance to fund this loan");

        assertThat(walletRepo.findByUserIdForUpdate(lender.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("10.00");
        assertThat(walletRepo.findByUserIdForUpdate(borrower.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("0.00");
        assertThat(loanRepo.findById(loan.getId()).orElseThrow().getStatus())
                .isEqualTo(LoanStatus.PENDING);
        assertThat(loanRepaymentRepo.findAllByLoan_IdOrderByInstallmentNumberAsc(loan.getId()))
                .isEmpty();
    }

    @Test
    void lenderRepaymentReceiptUsesDistinctLedgerTransactionType() {
        User borrower = createUser(UserRole.BORROWER);
        User lender = createUser(UserRole.LENDER);
        walletRepo.saveAndFlush(walletWithBalance(borrower, "0.00"));
        Wallet lenderWallet = walletRepo.saveAndFlush(walletWithBalance(lender, "100.00"));
        Loan loan = loanRepo.saveAndFlush(new Loan(
                borrower, new BigDecimal("100.00"), BigDecimal.ZERO, 1));

        loanService.fund(lender.getEmail(), loan.getId());
        loanService.repay(borrower.getEmail(), loan.getId());

        assertThat(walletTransactionRepo.findAllByWallet_IdOrderByTimestampDescIdDesc(lenderWallet.getId()))
                .extracting(transaction -> transaction.getType())
                .containsExactly(WalletTransactionType.LOAN_REPAYMENT, WalletTransactionType.WITHDRAWAL);
    }

    @Test
    void legacyInstallmentsWithoutStoredComponentsAreSplitFromLoanTerms() {
        User borrower = createUser(UserRole.BORROWER);
        Loan legacyLoan = new Loan(borrower, new BigDecimal("100.00"), new BigDecimal("8.00"), 3);
        var first = new app.models.LoanRepayment(
                legacyLoan, new BigDecimal("34.00"), java.time.LocalDate.now().plusMonths(1), 1);
        var finalInstallment = new app.models.LoanRepayment(
                legacyLoan, new BigDecimal("34.00"), java.time.LocalDate.now().plusMonths(3), 3);

        assertThat(first.getPrincipalAmount()).isEqualByComparingTo("33.33");
        assertThat(first.getInterestAmount()).isEqualByComparingTo("0.67");
        assertThat(finalInstallment.getPrincipalAmount()).isEqualByComparingTo("33.34");
        assertThat(finalInstallment.getInterestAmount()).isEqualByComparingTo("0.66");
    }

    @Test
    void loanApplicationRejectsBorrowerBelowEligibilityThreshold() {
        User borrower = createUser(UserRole.BORROWER);

        assertThatThrownBy(() -> loanService.apply(
                borrower.getEmail(),
                new LoanApplicationRequest(
                        new BigDecimal("10000.01"),
                        new BigDecimal("5.00"),
                        6
                )
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("Your credit score of 500 is below the minimum eligibility threshold of 550.");
    }

    @Test
    void overdueDetectionUpdatesOnlyPendingPastDueInstallments() {
        User borrower = createUser(UserRole.BORROWER);
        User lender = createUser(UserRole.LENDER);
        walletRepo.saveAndFlush(walletWithBalance(borrower, "100.00"));
        walletRepo.saveAndFlush(walletWithBalance(lender, "100.00"));
        Loan loan = loanRepo.saveAndFlush(new Loan(
                borrower,
                new BigDecimal("50.00"),
                new BigDecimal("10.00"),
                3
        ));

        java.time.LocalDate today = java.time.LocalDate.now();
        app.models.LoanRepayment pastPaid = loanRepaymentRepo.saveAndFlush(new app.models.LoanRepayment(
                loan, new BigDecimal("20.00"), today.minusDays(10), 1
        ));
        pastPaid.markPaid(today.minusDays(10));
        loanRepaymentRepo.saveAndFlush(pastPaid);

        app.models.LoanRepayment pastPending = loanRepaymentRepo.saveAndFlush(new app.models.LoanRepayment(
                loan, new BigDecimal("20.00"), today.minusDays(5), 2
        ));

        app.models.LoanRepayment futurePending = loanRepaymentRepo.saveAndFlush(new app.models.LoanRepayment(
                loan, new BigDecimal("20.00"), today.plusDays(10), 3
        ));

        int updatedCount = loanService.updateOverdueRepayments();

        assertThat(updatedCount).isEqualTo(1);
        assertThat(loanRepaymentRepo.findById(pastPaid.getId()).orElseThrow().getStatus())
                .isEqualTo(app.models.RepaymentStatus.PAID);
        assertThat(loanRepaymentRepo.findById(pastPending.getId()).orElseThrow().getStatus())
                .isEqualTo(app.models.RepaymentStatus.OVERDUE);
        assertThat(loanRepaymentRepo.findById(futurePending.getId()).orElseThrow().getStatus())
                .isEqualTo(app.models.RepaymentStatus.PENDING);

        int secondRunCount = loanService.updateOverdueRepayments();
        assertThat(secondRunCount).isEqualTo(0);
    }

    private User createUser(UserRole role) {
        User user = new User();
        user.setName("Loan lifecycle test");
        user.setEmail("loan-test-" + UUID.randomUUID() + "@example.test");
        user.setPassword("not-used-in-service-test");
        user.setRole(role);
        return userRepo.saveAndFlush(user);
    }

    private Wallet walletWithBalance(User user, String balance) {
        Wallet wallet = new Wallet(user);
        wallet.setBalance(new BigDecimal(balance));
        return wallet;
    }
}
