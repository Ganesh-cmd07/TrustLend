package app.controller;

import app.data.LoginRequest;
import app.data.SignupRequest;
import app.data.ProfileUpdateRequest;
import app.Repo.LenderRequestRepo;
import app.Repo.UserRepo;
import app.data.AuthenticatedUserResponse;
import app.services.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final UserRepo userRepo;
    private final LenderRequestRepo lenderRequestRepo;

    public AuthController(
            AuthService authService,
            UserRepo userRepo,
            LenderRequestRepo lenderRequestRepo) {
        this.authService = authService;
        this.userRepo = userRepo;
        this.lenderRequestRepo = lenderRequestRepo;
    }

    @PostMapping({"/signup", "/auth/signup"})
    public ResponseEntity<Map<String, String>> signup(
            @Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "LENDER".equals(request.getRole())
                        ? "Lender registration submitted for admin approval"
                        : "Account created successfully"));
    }

    @PostMapping({"/login", "/auth/login"})
    public ResponseEntity<Map<String, String>> login(
            @Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);
        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "token", token
        ));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> currentUser(Authentication authentication) {
        return userRepo.findByEmail(authentication.getName())
                .<ResponseEntity<?>>map(user -> {
                    String lenderRequestStatus = lenderRequestRepo
                            .findFirstByUser_IdOrderByCreatedAtDescIdDesc(user.getId())
                            .map(request -> request.getStatus().name())
                            .orElse(null);

                    return ResponseEntity.ok(
                            AuthenticatedUserResponse.from(user, lenderRequestStatus));
                })
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PutMapping("/auth/me")
    public ResponseEntity<?> updateCurrentUser(
            Authentication authentication,
            @Valid @RequestBody ProfileUpdateRequest request) {
        var user = authService.updateCurrentUser(authentication.getName(), request);
        String lenderRequestStatus = lenderRequestRepo
                .findFirstByUser_IdOrderByCreatedAtDescIdDesc(user.getId())
                .map(lenderRequest -> lenderRequest.getStatus().name())
                .orElse(null);
        return ResponseEntity.ok(AuthenticatedUserResponse.from(user, lenderRequestStatus));
    }
}
