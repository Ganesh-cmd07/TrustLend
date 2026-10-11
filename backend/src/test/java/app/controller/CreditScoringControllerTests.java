package app.controller;

import app.Repo.UserRepo;
import app.exception.ApiException;
import app.models.User;
import app.models.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CreditScoringControllerTests {

    @Autowired
    private CreditScoringController creditScoringController;

    @Autowired
    private UserRepo userRepo;

    @Test
    void unauthenticatedRequestThrowsUnauthorizedException() {
        assertThatThrownBy(() -> creditScoringController.getMyCreditScore(null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Authenticated user was not found")
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void authenticatedRequestReturnsCreditScoreResponse() {
        User user = new User();
        user.setName("Credit Score Test User");
        user.setEmail("score-test-" + UUID.randomUUID() + "@example.test");
        user.setPassword("password123");
        user.setRole(UserRole.BORROWER);
        userRepo.saveAndFlush(user);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_BORROWER"))
        );

        ResponseEntity<?> response = creditScoringController.getMyCreditScore(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("creditScore", 500)
                .containsKey("riskTier")
                .containsKey("eligible");
        assertThat(body.get("maxEligibleLoan")).isNotNull();
    }
}
