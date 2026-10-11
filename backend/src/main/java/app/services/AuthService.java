package app.services;

import app.Repo.UserRepo;
import app.Repo.LenderRequestRepo;
import app.data.LoginRequest;
import app.data.SignupRequest;
import app.data.ProfileUpdateRequest;
import app.exception.ApiException;
import app.models.User;
import app.models.UserRole;
import app.models.LenderRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final WalletService walletService;
    private final LenderRequestRepo lenderRequestRepo;

    public AuthService(
            UserRepo userRepo,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            WalletService walletService,
            LenderRequestRepo lenderRequestRepo) {

        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.walletService = walletService;
        this.lenderRequestRepo = lenderRequestRepo;
    }

    @Transactional
    public void signup(SignupRequest request) {

        if (userRepo.existsByEmail(request.getEmail())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already exists");
        }

        User user = new User();

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.BORROWER);

        User savedUser = userRepo.save(user);

        walletService.createWallet(savedUser);
        if ("LENDER".equals(request.getRole())) {
            // Public registration never grants a privileged lending role. Admin approval does.
            lenderRequestRepo.save(new LenderRequest(savedUser));
        }

    }

    public String login(LoginRequest request) {

        User user = userRepo.findByEmail(request.getEmail())
                .filter(candidate -> passwordEncoder.matches(
                        request.getPassword(),
                        candidate.getPassword()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"));

        return jwtService.generateToken(user.getEmail());
    }

    @Transactional
    public User updateCurrentUser(String email, ProfileUpdateRequest request) {
        User user = userRepo.findByEmailForUpdate(email)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user was not found"));
        user.setName(request.name().trim());
        return userRepo.save(user);
    }
}
