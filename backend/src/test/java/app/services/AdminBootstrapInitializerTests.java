package app.services;

import app.Repo.UserRepo;
import app.data.AdminBootstrapInitializer;
import app.models.User;
import app.models.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapInitializerTests {

    @Mock
    private UserRepo userRepo;

    @Test
    void createsAdminWithHashedPasswordWhenConfigured() {
        var encoder = new BCryptPasswordEncoder();
        var initializer = initializer(encoder, "owner@example.test", "A-long-bootstrap-secret-123");
        when(userRepo.findByEmailForUpdate("owner@example.test")).thenReturn(Optional.empty());

        initializer.run();

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(captor.getValue().getPassword()).isNotEqualTo("A-long-bootstrap-secret-123");
        assertThat(encoder.matches("A-long-bootstrap-secret-123", captor.getValue().getPassword())).isTrue();
    }

    @Test
    void doesNotOverwriteExistingAdmin() {
        var existing = new User();
        existing.setRole(UserRole.ADMIN);
        existing.setPassword("existing-hash");
        when(userRepo.findByEmailForUpdate("owner@example.test")).thenReturn(Optional.of(existing));

        initializer(new BCryptPasswordEncoder(), "owner@example.test", "A-long-bootstrap-secret-123").run();

        verify(userRepo, never()).save(any());
        assertThat(existing.getPassword()).isEqualTo("existing-hash");
    }

    @Test
    void failsOnPartialOrWeakConfiguration() {
        assertThatThrownBy(() -> initializer(new BCryptPasswordEncoder(), "owner@example.test", "").run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires both");
        assertThatThrownBy(() -> initializer(new BCryptPasswordEncoder(), "owner@example.test", "too-short").run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 12 and 72");
        verify(userRepo, never()).save(any());
    }

    private AdminBootstrapInitializer initializer(BCryptPasswordEncoder encoder, String email, String password) {
        return new AdminBootstrapInitializer(userRepo, encoder, email, "TrustLend Admin", password);
    }
}
