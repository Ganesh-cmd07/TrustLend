package app.controller;

import app.Repo.UserRepo;
import app.exception.ApiException;
import app.models.User;
import app.services.CreditScoringService;
import app.services.CreditScoreHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/credit-score")
public class CreditScoringController {

    private final CreditScoringService creditScoringService;
    private final CreditScoreHistoryService historyService;
    private final UserRepo userRepo;

    public CreditScoringController(CreditScoringService creditScoringService, UserRepo userRepo,
                                   CreditScoreHistoryService historyService) {
        this.creditScoringService = creditScoringService;
        this.userRepo = userRepo;
        this.historyService = historyService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyCreditScore(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found");
        }
        User user = userRepo.findByEmail(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        var score = creditScoringService.calculateCreditScore(user);
        historyService.recordIfChanged(user, score);
        return ResponseEntity.ok(score);
    }

    @GetMapping("/history")
    public ResponseEntity<?> getMyCreditScoreHistory(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found");
        }
        User user = userRepo.findByEmail(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        return ResponseEntity.ok(historyService.getHistory(user.getId()));
    }
}