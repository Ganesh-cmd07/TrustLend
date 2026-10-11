package app.data;

import app.models.LoanRepayment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanRepaymentResponse(
        Long id,
        int installmentNumber,
        BigDecimal amount,
        BigDecimal principalAmount,
        BigDecimal interestAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        LocalDate dueDate,
        LocalDate paidDate,
        String status
) {

    public static LoanRepaymentResponse from(LoanRepayment repayment) {
        return new LoanRepaymentResponse(
                repayment.getId(),
                repayment.getInstallmentNumber(),
                repayment.getAmount(),
                repayment.getPrincipalAmount(),
                repayment.getInterestAmount(),
                repayment.getStatus() == app.models.RepaymentStatus.PAID
                        ? repayment.getAmount() : BigDecimal.ZERO.setScale(2),
                repayment.getStatus() == app.models.RepaymentStatus.PAID
                        ? BigDecimal.ZERO.setScale(2) : repayment.getAmount(),
                repayment.getDueDate(),
                repayment.getPaidDate(),
                repayment.getStatus().name()
        );
    }
}
