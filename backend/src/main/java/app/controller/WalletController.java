package app.controller;

import app.data.WalletAmountRequest;
import app.data.WalletResponse;
import app.data.WalletTransactionPageResponse;
import app.services.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/me")
    public ResponseEntity<WalletResponse> getWallet(Authentication authentication) {
        return ResponseEntity.ok(walletService.getWalletForUser(authentication.getName()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<WalletTransactionPageResponse> getTransactions(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(walletService.getTransactionsForUser(authentication.getName(), page, size));
    }

    @PostMapping("/deposit")
    public ResponseEntity<WalletResponse> deposit(
            Authentication authentication,
            @Valid @RequestBody WalletAmountRequest request) {
        return ResponseEntity.ok(
                walletService.deposit(authentication.getName(), request.amount()));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<WalletResponse> withdraw(
            Authentication authentication,
            @Valid @RequestBody WalletAmountRequest request) {
        return ResponseEntity.ok(
                walletService.withdraw(authentication.getName(), request.amount()));
    }
}
