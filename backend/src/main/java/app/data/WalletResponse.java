package app.data;

import app.models.Wallet;

import java.math.BigDecimal;
import java.util.List;

public record WalletResponse(
        BigDecimal balance,
        List<WalletTransactionResponse> transactions
) {
    public static WalletResponse from(
            Wallet wallet,
            List<WalletTransactionResponse> transactions) {
        return new WalletResponse(wallet.getBalance(), transactions);
    }
}
