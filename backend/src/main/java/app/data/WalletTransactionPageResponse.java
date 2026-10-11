package app.data;

import app.models.WalletTransaction;
import org.springframework.data.domain.Page;

import java.util.List;

public record WalletTransactionPageResponse(
        List<WalletTransactionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static WalletTransactionPageResponse from(Page<WalletTransaction> transactions) {
        return new WalletTransactionPageResponse(
                transactions.getContent().stream().map(WalletTransactionResponse::from).toList(),
                transactions.getNumber(),
                transactions.getSize(),
                transactions.getTotalElements(),
                transactions.getTotalPages()
        );
    }
}
