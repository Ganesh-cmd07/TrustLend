package app.data;

import app.Repo.UserRepo;
import app.models.User;
import app.models.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class AdminBootstrapInitializer implements CommandLineRunner {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String name;
    private final String password;

    public AdminBootstrapInitializer(
            UserRepo userRepo,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.bootstrap.email:}") String email,
            @Value("${app.admin.bootstrap.name:TrustLend Administrator}") String name,
            @Value("${app.admin.bootstrap.password:}") String password) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.name = name == null || name.isBlank() ? "TrustLend Administrator" : name.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        boolean hasEmail = !email.isBlank();
        boolean hasPassword = !password.isBlank();
        if (!hasEmail && !hasPassword) {
            return;
        }
        if (!hasEmail || !hasPassword) {
            throw new IllegalStateException(
                    "Admin bootstrap requires both TRUSTLEND_ADMIN_EMAIL and TRUSTLEND_ADMIN_PASSWORD.");
        }
        if (password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException("TRUSTLEND_ADMIN_PASSWORD must be between 12 and 72 characters.");
        }

        var existing = userRepo.findByEmailForUpdate(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() != UserRole.ADMIN) {
                throw new IllegalStateException("Configured bootstrap email already belongs to a non-admin user.");
            }
            return;
        }

        User admin = new User();
        admin.setName(name);
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        userRepo.save(admin);
    }
}
