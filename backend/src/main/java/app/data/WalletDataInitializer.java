package app.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WalletDataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public WalletDataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.update("""
                INSERT INTO wallets (user_id, balance, created_at, updated_at)
                SELECT u.id, 0.00, NOW(), NOW()
                FROM users u
                LEFT JOIN wallets w ON w.user_id = u.id
                WHERE w.id IS NULL
                """);
    }
}
