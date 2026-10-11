package app.services;

import app.Repo.LoanRepo;
import app.Repo.LoanApplicationReviewRepo;
import app.Repo.LoanRepaymentRepo;
import app.Repo.UserRepo;
import app.Repo.WalletRepo;
import app.Repo.WalletTransactionRepo;
import app.data.LoanApplicationRequest;
import app.data.LoanResponse;
import app.data.LoanReviewRequest;
import app.data.LoanReviewResponse;
import app.exception.ApiException;
import app.models.Loan;
import app.models.LoanRepayment;
import app.models.LoanApplicationReview;
import app.models.LoanStatus;
import app.models.RepaymentStatus;
import app.models.User;
import app.models.UserRole;
import app.models.Wallet;
import app.models.WalletTransaction;
import app.models.WalletTransactionType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LoanService {

    private static final List<RepaymentStatus> UNPAID_STATUSES =
            List.of(RepaymentStatus.PENDING, RepaymentStatus.OVERDUE);

    private final LoanRepo loanRepo;
    private final LoanRepaymentRepo loanRepaymentRepo;
    private final UserRepo userRepo;
    private final WalletRepo walletRepo;
    private final WalletTransactionRepo walletTransactionRepo;
    private final CreditScoringService creditScoringService;
    private final LoanApplicationReviewRepo loanApplicationReviewRepo;

    public LoanService(
            LoanRepo loanRepo,
            LoanRepaymentRepo loanRepaymentRepo,
            UserRepo userRepo,
            WalletRepo walletRepo,
            WalletTransactionRepo walletTransactionRepo,
            CreditScoringService creditScoringService,
            LoanApplicationReviewRepo loanApplicationReviewRepo) {
        this.loanRepo = loanRepo;
        this.loanRepaymentRepo = loanRepaymentRepo;
        this.userRepo = userRepo;
        this.walletRepo = walletRepo;
        this.walletTransactionRepo = walletTransactionRepo;
        this.creditScoringService = creditScoringService;
        this.loanApplicationReviewRepo = loanApplicationReviewRepo;
    }

    @Transactional
    public LoanResponse apply(String email, LoanApplicationRequest request) {
        User borrower = findUser(email);
        requireRole(borrower, UserRole.BORROWER, "Only borrowers may apply for loans");

        if (creditScoringService != null) {
            Map<String, Object> scoreData = creditScoringService.calculateCreditScore(borrower);
            if (!Boolean.TRUE.equals(scoreData.get("eligible"))) {
                int score = ((Number) scoreData.get("creditScore")).intValue();
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Your credit score of " + score + " is below the minimum eligibility threshold of 550."
                );
            }
            BigDecimal maxAllowed = (BigDecimal) scoreData.get("maxEligibleLoan");
            if (maxAllowed != null && request.amount().compareTo(maxAllowed) > 0) {
                int score = ((Number) scoreData.get("creditScore")).intValue();
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        CreditScoringService.loanLimitExceededMessage(request.amount(), maxAllowed, score)
                );
            }
        }

        Loan loan = new Loan(
                borrower,
                request.amount(),
                request.interestRate(),
                request.termMonths()
        );
        loan.submitApplication();
        loan = loanRepo.save(loan);
        return LoanResponse.from(loan, List.of());
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> getMarketplace(String email) {
        User lender = findUser(email);
        requireRole(lender, UserRole.LENDER, "Only lenders may view loan applications");
        return loanRepo.findAvailableForMarketplace(List.of(LoanStatus.APPROVED, LoanStatus.PENDING))
                .stream()
                .map(loan -> LoanResponse.from(loan, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> getApplicationsForReview(String email) {
        User administrator = findUser(email);
        requireRole(administrator, UserRole.ADMIN, "Only administrators may review loan applications");
        return loanRepo.findAllByStatusOrderByCreatedAtAscIdAsc(LoanStatus.SUBMITTED)
                .stream().map(loan -> LoanResponse.from(loan, List.of())).toList();
    }

    @Transactional
    public LoanResponse reviewApplication(String email, Long loanId, LoanReviewRequest request) {
        User administrator = findUser(email);
        requireRole(administrator, UserRole.ADMIN, "Only administrators may review loan applications");
        Loan loan = loanRepo.findByIdForUpdate(loanId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Loan application was not found"));
        if (loan.getStatus() != LoanStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Loan application has already been reviewed");
        }
        boolean approved = request.decision() == LoanReviewRequest.Decision.APPROVE;
        String reason = request.reason() == null ? null : request.reason().trim();
        if (!approved && (reason == null || reason.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A reason is required when rejecting a loan application");
        }
        if (approved && loan.getGroup() != null && !loan.isGroupCosigned()) {
            throw new ApiException(HttpStatus.CONFLICT, "Group-backed application requires leader co-signature before approval");
        }
        LoanStatus previousStatus = loan.getStatus();
        loan.review(administrator, approved, reason);
        loanRepo.save(loan);
        loanApplicationReviewRepo.save(new LoanApplicationReview(
                loan, administrator, previousStatus, loan.getStatus(), reason));
        return LoanResponse.from(loan, List.of());
    }

    @Transactional(readOnly = true)
    public List<LoanReviewResponse> getRecentReviewHistory(String email) {
        User administrator = findUser(email);
        requireRole(administrator, UserRole.ADMIN, "Only administrators may view loan review history");
        return loanApplicationReviewRepo.findTop20ByOrderByCreatedAtDescIdDesc()
                .stream().map(LoanReviewResponse::from).toList();
    }

    @Transactional
    @CacheEvict(cacheNames = "creditScores", allEntries = true)
    public LoanResponse fund(String email, Long loanId) {
        User lender = findUser(email);
        requireRole(lender, UserRole.LENDER, "Only lenders may fund loans");
        Loan loan = loanRepo.findByIdForUpdate(loanId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Loan application was not found"));
        if (loan.getStatus() != LoanStatus.APPROVED && loan.getStatus() != LoanStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Loan application is no longer available for funding");
        }
        if (!loan.isGroupCosigned()) {
            throw new ApiException(HttpStatus.CONFLICT, "Group-backed application requires leader co-signature");
        }
        if (loan.getBorrower().getId().equals(lender.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "You cannot fund your own loan application");
        }

        Wallet[] wallets = lockWallets(lender.getId(), loan.getBorrower().getId());
        Wallet lenderWallet = wallets[0];
        Wallet borrowerWallet = wallets[1];
        if (lenderWallet.getBalance().compareTo(loan.getAmount()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Insufficient wallet balance to fund this loan");
        }

        BigDecimal lenderBalance = lenderWallet.getBalance().subtract(loan.getAmount());
        BigDecimal borrowerBalance = borrowerWallet.getBalance().add(loan.getAmount());
        lenderWallet.setBalance(lenderBalance);
        borrowerWallet.setBalance(borrowerBalance);
        walletRepo.saveAll(List.of(lenderWallet, borrowerWallet));
        walletTransactionRepo.save(new WalletTransaction(
                lenderWallet, loan.getAmount(), WalletTransactionType.WITHDRAWAL, lenderBalance));
        walletTransactionRepo.save(new WalletTransaction(
                borrowerWallet, loan.getAmount(), WalletTransactionType.DISBURSEMENT, borrowerBalance));

        loan.fund(lender);
        loanRepo.save(loan);
        loanRepaymentRepo.saveAll(createSchedule(loan));
        return toResponse(loan);
    }

    @Transactional
    @CacheEvict(cacheNames = "creditScores", allEntries = true)
    public LoanResponse repay(String email, Long loanId) {
        User borrower = findUser(email);
        requireRole(borrower, UserRole.BORROWER, "Only borrowers may repay loans");
        Loan loan = loanRepo.findByIdForUpdate(loanId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Loan was not found"));
        if (!loan.getBorrower().getId().equals(borrower.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You may only repay your own loans");
        }
        if (loan.getStatus() != LoanStatus.FUNDED && loan.getStatus() != LoanStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "This loan is not awaiting repayment");
        }

        LoanRepayment repayment = loanRepaymentRepo
                .findFirstByLoan_IdAndStatusInOrderByInstallmentNumberAsc(loanId, UNPAID_STATUSES)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "No unpaid installments remain"));

        Wallet[] wallets = lockWallets(borrower.getId(), loan.getLender().getId());
        Wallet borrowerWallet = wallets[0];
        Wallet lenderWallet = wallets[1];
        if (borrowerWallet.getBalance().compareTo(repayment.getAmount()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Insufficient wallet balance to pay this installment");
        }

        BigDecimal borrowerBalance = borrowerWallet.getBalance().subtract(repayment.getAmount());
        BigDecimal lenderBalance = lenderWallet.getBalance().add(repayment.getAmount());
        borrowerWallet.setBalance(borrowerBalance);
        lenderWallet.setBalance(lenderBalance);
        walletRepo.saveAll(List.of(borrowerWallet, lenderWallet));
        walletTransactionRepo.save(new WalletTransaction(
                borrowerWallet, repayment.getAmount(), WalletTransactionType.REPAYMENT, borrowerBalance));
        walletTransactionRepo.save(new WalletTransaction(
                lenderWallet, repayment.getAmount(), WalletTransactionType.LOAN_REPAYMENT, lenderBalance));

        repayment.markPaid(LocalDate.now());
        loanRepaymentRepo.save(repayment);
        if (loanRepaymentRepo.existsByLoan_IdAndStatusIn(loanId, UNPAID_STATUSES)) {
            loan.markActive();
        } else {
            loan.markRepaid();
        }
        loanRepo.save(loan);
        return toResponse(loan);
    }

    @Transactional
    @CacheEvict(cacheNames = "creditScores", allEntries = true)
    public List<LoanResponse> getMyLoans(String email) {
        User user = findUser(email);
        updateOverdueRepayments();
        List<Loan> loans = loanRepo.findAllForUser(user.getId());
        return loans.stream().map(this::toResponse).toList();
    }

    @Transactional
    @CacheEvict(cacheNames = "creditScores", allEntries = true)
    public int updateOverdueRepayments() {
        LocalDate today = LocalDate.now();
        List<LoanRepayment> overdueRepayments = loanRepaymentRepo
                .findAllByStatusAndDueDateBefore(RepaymentStatus.PENDING, today);
        for (LoanRepayment repayment : overdueRepayments) {
            repayment.markOverdue();
        }
        if (!overdueRepayments.isEmpty()) {
            loanRepaymentRepo.saveAll(overdueRepayments);
        }
        return overdueRepayments.size();
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    @CacheEvict(cacheNames = "creditScores", allEntries = true)
    public void scheduleOverdueRepaymentCheck() {
        updateOverdueRepayments();
    }

    private List<LoanRepayment> createSchedule(Loan loan) {
        BigDecimal totalInterest = loan.getAmount()
                .multiply(loan.getInterestRate())
                .multiply(BigDecimal.valueOf(loan.getTermMonths()))
                .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
        BigDecimal regularPrincipal = loan.getAmount()
                .divide(BigDecimal.valueOf(loan.getTermMonths()), 2, RoundingMode.DOWN);
        BigDecimal regularInterest = totalInterest
                .divide(BigDecimal.valueOf(loan.getTermMonths()), 2, RoundingMode.DOWN);
        LocalDate firstDueDate = LocalDate.now().plusMonths(1);
        List<LoanRepayment> schedule = new ArrayList<>(loan.getTermMonths());

        for (int number = 1; number <= loan.getTermMonths(); number++) {
            BigDecimal principal = number == loan.getTermMonths()
                    ? loan.getAmount().subtract(regularPrincipal.multiply(BigDecimal.valueOf(number - 1L)))
                    : regularPrincipal;
            BigDecimal interest = number == loan.getTermMonths()
                    ? totalInterest.subtract(regularInterest.multiply(BigDecimal.valueOf(number - 1L)))
                    : regularInterest;
            schedule.add(new LoanRepayment(
                    loan,
                    principal.add(interest),
                    principal,
                    interest,
                    firstDueDate.plusMonths(number - 1L),
                    number
            ));
        }
        return schedule;
    }

    private LoanResponse toResponse(Loan loan) {
        return LoanResponse.from(
                loan,
                loanRepaymentRepo.findAllByLoan_IdOrderByInstallmentNumberAsc(loan.getId())
        );
    }

    private Wallet[] lockWallets(Long firstUserId, Long secondUserId) {
        Long lowerId = Math.min(firstUserId, secondUserId);
        Long higherId = Math.max(firstUserId, secondUserId);
        Wallet lowerWallet = walletRepo.findByUserIdForUpdate(lowerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet was not found"));
        Wallet higherWallet = walletRepo.findByUserIdForUpdate(higherId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet was not found"));
        return firstUserId.equals(lowerId)
                ? new Wallet[]{lowerWallet, higherWallet}
                : new Wallet[]{higherWallet, lowerWallet};
    }

    private User findUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
    }

    private void requireRole(User user, UserRole role, String message) {
        if (user.getRole() != role) {
            throw new ApiException(HttpStatus.FORBIDDEN, message);
        }
    }
}
