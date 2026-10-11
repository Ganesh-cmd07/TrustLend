package app.data;

import app.models.Loan;
import app.models.LoanRepayment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

public record LoanResponse(
        Long id,
        Long borrowerId,
        String borrowerName,
        Long lenderId,
        String lenderName,
        Long groupId,
        String groupName,
        Long groupApprovedById,
        BigDecimal amount,
        BigDecimal interestRate,
        int termMonths,
        BigDecimal totalRepayable,
        String status,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        String reviewReason,
        List<LoanRepaymentResponse> repayments
) {

    public static LoanResponse from(Loan loan, List<LoanRepayment> repayments) {
        BigDecimal interest = loan.getAmount()
                .multiply(loan.getInterestRate())
                .multiply(BigDecimal.valueOf(loan.getTermMonths()))
                .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
        return new LoanResponse(
                loan.getId(),
                loan.getBorrower().getId(),
                loan.getBorrower().getName(),
                loan.getLender() == null ? null : loan.getLender().getId(),
                loan.getLender() == null ? null : loan.getLender().getName(),
                loan.getGroup() == null ? null : loan.getGroup().getId(),
                loan.getGroup() == null ? null : loan.getGroup().getName(),
                loan.getGroupApprovedBy() == null ? null : loan.getGroupApprovedBy().getId(),
                loan.getAmount(),
                loan.getInterestRate(),
                loan.getTermMonths(),
                loan.getAmount().add(interest),
                loan.getStatus().name(),
                loan.getCreatedAt(),
                loan.getReviewedAt(),
                loan.getReviewReason(),
                repayments.stream().map(LoanRepaymentResponse::from).toList()
        );
    }
}
