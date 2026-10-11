package app;

import app.Repo.UserRepo;
import app.Repo.LenderRequestRepo;
import app.models.User;
import app.models.UserRole;
import app.models.LenderRequestStatus;
import app.services.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BackendApplicationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepo userRepo;
    @Autowired JwtService jwtService;
    @Autowired LenderRequestRepo lenderRequestRepo;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void contextLoads() { }

    @Test
    void unauthenticatedAdminRequestReturnsConsistentJsonUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    @Transactional
    void authSignupAliasCreatesBorrowerWithoutJwt() throws Exception {
        String email = "signup-" + UUID.randomUUID() + "@example.test";

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Signup Test\",\"email\":\"" + email
                                + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Account created successfully"));

        User created = userRepo.findByEmail(email).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.getRole()).isEqualTo(UserRole.BORROWER);
        org.assertj.core.api.Assertions.assertThat(passwordEncoder.matches("password123", created.getPassword()))
                .isTrue();
    }

    @Test
    void duplicateSignupReturnsConflictWithoutCreatingAnotherUser() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.test";
        String request = "{\"name\":\"Duplicate Test\",\"email\":\"" + email
                + "\",\"password\":\"password123\"}";

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists"));

        org.assertj.core.api.Assertions.assertThat(userRepo.findByEmail(email)).isPresent();
    }

    @Test
    @Transactional
    void lenderSignupCreatesPendingApprovalWithoutGrantingLenderRole() throws Exception {
        String email = "lender-signup-" + UUID.randomUUID() + "@example.test";

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lender Applicant\",\"email\":\"" + email
                                + "\",\"password\":\"password123\",\"role\":\"LENDER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Lender registration submitted for admin approval"));

        User created = userRepo.findByEmail(email).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.getRole()).isEqualTo(UserRole.BORROWER);
        var request = lenderRequestRepo.findFirstByUser_IdOrderByCreatedAtDescIdDesc(created.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(request.getStatus()).isEqualTo(LenderRequestStatus.PENDING);
    }

    @Test
    void signupRejectsAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad Role\",\"email\":\"bad-role@example.test\",\"password\":\"password123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void authenticatedUserCanUpdateOwnNameAndReceivesCreationDate() throws Exception {
        User user = new User();
        user.setName("Before Name");
        user.setEmail("profile-" + UUID.randomUUID() + "@example.test");
        user.setPassword("unused");
        user.setRole(UserRole.BORROWER);
        userRepo.saveAndFlush(user);
        String token = jwtService.generateToken(user.getEmail());

        mockMvc.perform(put("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Updated Name  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        org.assertj.core.api.Assertions.assertThat(userRepo.findByEmail(user.getEmail()).orElseThrow().getName())
                .isEqualTo("Updated Name");
    }

    @Test
    void profileUpdateRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Changed\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBearerTokenIsStillRejectedOnPublicSignup() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Signup Test\",\"email\":\"invalid-token@example.test\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired JWT"));
    }

    @Test
    @Transactional
    void lenderRoleCannotReadBorrowerOnlyCreditScoreEndpoint() throws Exception {
        User lender = new User();
        lender.setName("Test lender");
        lender.setEmail("security-lender@example.test");
        lender.setPassword("not-used-in-this-test");
        lender.setRole(UserRole.LENDER);
        userRepo.saveAndFlush(lender);
        String token = jwtService.generateToken(lender.getEmail());

        mockMvc.perform(get("/api/v1/credit-score/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Access is denied"));
    }
}
