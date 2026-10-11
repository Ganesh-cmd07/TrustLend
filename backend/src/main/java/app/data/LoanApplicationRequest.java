package app.data;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LoanApplicationRequest(
        @NotNull(message = "Loan amount is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "Loan amount must be greater than zero")
        @Digits(integer = 13, fraction = 2, message = "Loan amount must have at most 13 integer and 2 fractional digits")
        BigDecimal amount,

        @NotNull(message = "Interest rate is required")
        @DecimalMin(value = "0.00", message = "Interest rate cannot be negative")
        @DecimalMax(value = "100.00", message = "Interest rate cannot exceed 100 percent")
        @Digits(integer = 3, fraction = 2, message = "Interest rate must have at most 3 integer and 2 fractional digits")
        BigDecimal interestRate,

        @Min(value = 1, message = "Term must be at least 1 month")
        @Max(value = 120, message = "Term cannot exceed 120 months")
        int termMonths
) {
}
