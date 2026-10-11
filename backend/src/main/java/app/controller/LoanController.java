package app.controller;

import app.data.LoanApplicationRequest;
import app.data.LoanResponse;
import app.services.LoanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping("/apply")
    @PreAuthorize("hasRole('BORROWER')")
    public ResponseEntity<LoanResponse> apply(
            Authentication authentication,
            @Valid @RequestBody LoanApplicationRequest request) {
        return ResponseEntity.status(201)
                .body(loanService.apply(authentication.getName(), request));
    }

    @GetMapping("/marketplace")
    @PreAuthorize("hasRole('LENDER')")
    public List<LoanResponse> marketplace(Authentication authentication) {
        return loanService.getMarketplace(authentication.getName());
    }

    @PostMapping("/{id}/fund")
    @PreAuthorize("hasRole('LENDER')")
    public LoanResponse fund(Authentication authentication, @PathVariable Long id) {
        return loanService.fund(authentication.getName(), id);
    }

    @PostMapping("/{id}/repay")
    @PreAuthorize("hasRole('BORROWER')")
    public LoanResponse repay(Authentication authentication, @PathVariable Long id) {
        return loanService.repay(authentication.getName(), id);
    }

    @GetMapping("/my-loans")
    public List<LoanResponse> myLoans(Authentication authentication) {
        return loanService.getMyLoans(authentication.getName());
    }
}
