package app.data;

import app.models.WalletTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletTransactionResponse(
        Long id,
        BigDecimal amount,
        String type,
        BigDecimal balanceAfter,
        LocalDateTime timestamp
) {
    public static WalletTransactionResponse from(WalletTransaction transaction) {
        return new WalletTransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getType().name(),
                transaction.getBalanceAfter(),
                transaction.getTimestamp()
        );
    }
}
