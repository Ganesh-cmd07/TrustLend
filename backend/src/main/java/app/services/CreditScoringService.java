package app.services;

import app.Repo.GroupMembershipRepo;
import app.Repo.LoanRepo;
import app.Repo.LoanRepaymentRepo;
import app.Repo.WalletRepo;
import app.models.GroupMembershipStatus;
import app.models.Loan;
import app.models.RepaymentStatus;
import app.models.User;
import app.models.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CreditScoringService {

    private final LoanRepo loanRepo;
    private final LoanRepaymentRepo repaymentRepo;
    private final WalletRepo walletRepo;
    private final GroupMembershipRepo membershipRepo;

    public CreditScoringService(
            LoanRepo loanRepo,
            LoanRepaymentRepo repaymentRepo,
            WalletRepo walletRepo,
            GroupMembershipRepo membershipRepo) {
        this.loanRepo = loanRepo;
        this.repaymentRepo = repaymentRepo;
        this.walletRepo = walletRepo;
        this.membershipRepo = membershipRepo;
    }

    @Cacheable(cacheNames = "creditScores", key = "#user.id")
    public Map<String, Object> calculateCreditScore(User user) {
        int score = 500; // Base score

        // 1. Loan repayment history
        List<Loan> userLoans = loanRepo.findAllForUser(user.getId());
        long repaidCount = userLoans.stream()
                .filter(l -> l.getStatus() != null && l.getStatus().name().equalsIgnoreCase("REPAID"))
                .count();
        long overdueCount = repaymentRepo.countDistinctByLoan_Borrower_IdAndStatus(
                user.getId(), RepaymentStatus.OVERDUE);

        score += (int) (repaidCount * 50);
        score -= (int) (overdueCount * 100);

        // 2. Wallet balance evaluation
        Wallet wallet = walletRepo.findByUser_Id(user.getId()).orElse(null);
        BigDecimal balance = (wallet != null && wallet.getBalance() != null)
                ? wallet.getBalance()
                : BigDecimal.ZERO;
        if (balance.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            score += 50;
        } else if (balance.compareTo(BigDecimal.valueOf(500)) >= 0) {
            score += 25;
        }

        // 3. Group membership evaluation
        boolean inActiveGroup = !membershipRepo
                .findAllByUser_IdAndStatusOrderByJoinedAtAscIdAsc(user.getId(), GroupMembershipStatus.ACTIVE)
                .isEmpty();
        if (inActiveGroup) {
            score += 50;
        }

        // Clamp credit score within range [300, 850]
        score = Math.max(300, Math.min(850, score));

        BigDecimal maxEligibleLoan = maxEligibleLoanForScore(score);

        Map<String, Object> result = new HashMap<>();
        result.put("creditScore", score);
        result.put("riskTier", riskTierForScore(score));
        result.put("eligible", score >= 550);
        result.put("maxEligibleLoan", maxEligibleLoan);
        result.put("factors", Map.of(
                "repaidLoans", repaidCount,
                "overdueLoans", overdueCount,
                "walletBalance", balance,
                "activeGroupMember", inActiveGroup
        ));

        return result;
    }

    public static String riskTierForScore(int score) {
        if (score >= 750) {
            return "LOW";
        }
        if (score >= 650) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    public static BigDecimal maxEligibleLoanForScore(int score) {
        if (score >= 750) {
            return new BigDecimal("100000.00");
        } else if (score >= 650) {
            return new BigDecimal("50000.00");
        } else if (score >= 550) {
            return new BigDecimal("25000.00");
        }
        return BigDecimal.ZERO.setScale(2);
    }

    public static String loanLimitExceededMessage(BigDecimal amount, BigDecimal maxAllowed, int score) {
        DecimalFormat amountFormat = new DecimalFormat(
                "#,##0.##",
                DecimalFormatSymbols.getInstance(Locale.forLanguageTag("en-IN"))
        );
        return "Loan amount (₹" + amountFormat.format(amount)
                + ") exceeds your credit limit of ₹" + amountFormat.format(maxAllowed)
                + " based on your current credit score of " + score + ".";
    }
}
