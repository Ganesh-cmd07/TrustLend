package app.controller;

import app.data.LenderRequestResponse;
import app.services.LenderRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class LenderRequestController {

    private final LenderRequestService lenderRequestService;

    public LenderRequestController(LenderRequestService lenderRequestService) {
        this.lenderRequestService = lenderRequestService;
    }

    @PostMapping("/lender-request")
    @PreAuthorize("hasRole('BORROWER')")
    public ResponseEntity<LenderRequestResponse> requestLenderStatus(Authentication authentication) {
        return ResponseEntity.status(201)
                .body(lenderRequestService.requestLenderStatus(authentication.getName()));
    }
}
