package app.data;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WalletAmountRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "Amount must be greater than zero")
        @Digits(integer = 13, fraction = 2, message = "Amount must have at most 13 integer and 2 fractional digits")
        BigDecimal amount
) {
}
