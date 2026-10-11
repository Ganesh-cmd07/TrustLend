package app;

import app.Repo.UserRepo;
import app.Repo.LoanRepo;
import app.Repo.LoanApplicationReviewRepo;
import app.Repo.LenderRequestRepo;
import app.Repo.WalletRepo;
import app.models.LoanStatus;
import app.models.User;
import app.models.UserRole;
import app.services.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WorkflowIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepo userRepo;
    @Autowired LoanRepo loanRepo;
    @Autowired WalletRepo walletRepo;
    @Autowired LenderRequestRepo lenderRequestRepo;
    @Autowired LoanApplicationReviewRepo reviewRepo;
    @Autowired JwtService jwtService;

    @Test
    @Transactional
    void signupApprovalGroupCosignFundingAndRepaymentWorkAcrossHttpApi() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User admin = new User();
        admin.setName("Integration Admin");
        admin.setEmail("admin-" + suffix + "@example.test");
        admin.setPassword("test-only");
        admin.setRole(UserRole.ADMIN);
        userRepo.saveAndFlush(admin);
        String adminToken = jwtService.generateToken(admin.getEmail());

        String leaderEmail = "leader-" + suffix + "@example.test";
        signup(leaderEmail, "Leader User", "BORROWER");
        String memberEmail = "member-" + suffix + "@example.test";
        signup(memberEmail, "Member User", "BORROWER");
        String lenderEmail = "lender-" + suffix + "@example.test";
        String password = "integration-password-123";
        signup(lenderEmail, "Lender User", "LENDER", password);

        var request = lenderRequestRepo.findFirstByUser_IdOrderByCreatedAtDescIdDesc(
                userRepo.findByEmail(lenderEmail).orElseThrow().getId()).orElseThrow();
        mvc.perform(post("/api/v1/admin/lender-requests/{id}/approve", request.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        assertThat(userRepo.findByEmail(lenderEmail).orElseThrow().getRole()).isEqualTo(UserRole.LENDER);

        String leaderToken = login(leaderEmail, "password123");
        String memberToken = login(memberEmail, "password123");
        String lenderToken = login(lenderEmail, password);
        Long leaderId = currentUserId(leaderToken);
        JsonNode adminDirectory = getJson("/api/v1/admin/users?page=0&size=1&role=ADMIN&q="
                + admin.getEmail(), adminToken);
        assertThat(adminDirectory.get("totalElements").asLong()).isEqualTo(1);
        assertThat(adminDirectory.get("content").get(0).get("id").asLong()).isEqualTo(admin.getId());
        assertThat(adminDirectory.get("content").get(0).has("password")).isFalse();
        mvc.perform(get("/api/v1/admin/users?page=0&size=10")
                        .header("Authorization", "Bearer " + lenderToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users?page=0&size=101")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
        assertThat(postJson("/api/v1/ai/chat", memberToken, Map.of("prompt", ""))
                .get("reply").asText()).contains("Please enter a question");
        mvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("prompt", "x".repeat(2001)))))
                .andExpect(status().isBadRequest());

        JsonNode group = postJson("/api/v1/groups/create", leaderToken,
                Map.of("name", "Integration SHG " + suffix));
        long groupId = group.get("id").asLong();
        mvc.perform(post("/api/v1/groups/{id}/join", groupId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isCreated());
        JsonNode groupDetails = getJson("/api/v1/groups/" + groupId, leaderToken);
        long membershipId = -1;
        for (JsonNode membership : groupDetails.get("members")) {
            if (membership.get("status").asText().equals("PENDING")) {
                membershipId = membership.get("membershipId").asLong();
            }
        }
        assertThat(membershipId).isPositive();
        mvc.perform(post("/api/v1/groups/{id}/members/{membershipId}/approve", groupId, membershipId)
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk());
        getJson("/api/v1/credit-score/me", memberToken);
        getJson("/api/v1/credit-score/me", memberToken);
        assertThat(getJson("/api/v1/credit-score/history", memberToken).size()).isEqualTo(1);
        postJson("/api/v1/wallet/deposit", memberToken, Map.of("amount", 20));
        postJson("/api/v1/groups/{id}/contribute", memberToken, Map.of("amount", 10), groupId);
        getJson("/api/v1/credit-score/me", memberToken);
        JsonNode scoreHistory = getJson("/api/v1/credit-score/history", memberToken);
        assertThat(scoreHistory.size()).isEqualTo(2);
        assertThat(scoreHistory.get(0).get("factors").get("walletBalance").decimalValue())
                .isEqualByComparingTo("10.00");
        assertThat(getJson("/api/v1/credit-score/history", leaderToken).size()).isZero();
        JsonNode groupAfterContribution = getJson("/api/v1/groups/" + groupId, leaderToken);
        assertThat(groupAfterContribution.get("totalSavings").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(groupAfterContribution.get("contributions").get(0).get("memberName").asText())
                .isEqualTo("Member User");
        JsonNode memberLedgerPage = getJson("/api/v1/wallet/transactions?page=0&size=1", memberToken);
        assertThat(memberLedgerPage.get("totalElements").asLong()).isEqualTo(2);
        assertThat(memberLedgerPage.get("content").size()).isEqualTo(1);
        assertThat(getJson("/api/v1/wallet/transactions?page=1&size=1", memberToken)
                .get("content").size()).isEqualTo(1);
        assertThat(getJson("/api/v1/wallet/transactions", leaderToken)
                .get("totalElements").asLong()).isZero();
        mvc.perform(get("/api/v1/wallet/transactions?page=0&size=101")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isBadRequest());

        JsonNode groupLoan = postJson("/api/v1/groups/" + groupId + "/loans/apply", memberToken,
                Map.of("amount", 1000, "interestRate", 0, "termMonths", 1));
        long loanId = groupLoan.get("id").asLong();
        assertThat(groupLoan.get("status").asText()).isEqualTo("SUBMITTED");
        mvc.perform(post("/api/v1/groups/{id}/loans/{loanId}/cosign", groupId, loanId)
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk());

        JsonNode rejectedApplication = postJson("/api/v1/groups/" + groupId + "/loans/apply", memberToken,
                Map.of("amount", 200, "interestRate", 0, "termMonths", 1));
        long rejectedLoanId = rejectedApplication.get("id").asLong();
        mvc.perform(post("/api/v1/groups/{id}/loans/{loanId}/cosign", groupId, rejectedLoanId)
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/loan-applications")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/loan-applications")
                        .header("Authorization", "Bearer " + lenderToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/loan-review-history")
                        .header("Authorization", "Bearer " + lenderToken))
                .andExpect(status().isForbidden());
        assertThat(getJson("/api/v1/admin/loan-applications", adminToken).toString())
                .contains("\"id\":" + rejectedLoanId);
        mvc.perform(post("/api/v1/admin/loan-applications/{loanId}/review", rejectedLoanId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("decision", "REJECT"))))
                .andExpect(status().isBadRequest());
        JsonNode rejection = postJson("/api/v1/admin/loan-applications/{loanId}/review", adminToken,
                Map.of("decision", "REJECT", "reason", "The submitted terms need revision."), rejectedLoanId);
        assertThat(rejection.get("status").asText()).isEqualTo("REJECTED");
        assertThat(rejection.get("reviewReason").asText()).isEqualTo("The submitted terms need revision.");
        assertThat(reviewRepo.findAllByLoan_IdOrderByCreatedAtAscIdAsc(rejectedLoanId))
                .singleElement().satisfies(review -> {
                    assertThat(review.getReviewer().getId()).isEqualTo(admin.getId());
                    assertThat(review.getPreviousStatus()).isEqualTo(LoanStatus.SUBMITTED);
                    assertThat(review.getNewStatus()).isEqualTo(LoanStatus.REJECTED);
                });
        JsonNode reviewHistory = getJson("/api/v1/admin/loan-review-history", adminToken);
        assertThat(reviewHistory.toString()).contains("\"loanId\":" + rejectedLoanId,
                "\"reviewerName\":\"Integration Admin\"", "\"newStatus\":\"REJECTED\"");
        mvc.perform(post("/api/v1/loans/{id}/fund", loanId)
                        .header("Authorization", "Bearer " + lenderToken))
                .andExpect(status().isConflict());
        JsonNode approval = postJson("/api/v1/admin/loan-applications/{loanId}/review", adminToken,
                Map.of("decision", "APPROVE", "reason", "Meets the platform policy."), loanId);
        assertThat(approval.get("status").asText()).isEqualTo("APPROVED");
        mvc.perform(post("/api/v1/admin/loan-applications/{loanId}/review", loanId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("decision", "APPROVE"))))
                .andExpect(status().isConflict());

        postJson("/api/v1/wallet/deposit", lenderToken, Map.of("amount", 2000));
        JsonNode marketplace = getJson("/api/v1/loans/marketplace", lenderToken);
        assertThat(marketplace.toString()).contains("\"id\":" + loanId);
        assertThat(marketplace.toString()).doesNotContain("\"id\":" + rejectedLoanId);
        mvc.perform(post("/api/v1/loans/{id}/fund", loanId)
                        .header("Authorization", "Bearer " + lenderToken))
                .andExpect(status().isOk());
        postJson("/api/v1/wallet/deposit", memberToken, Map.of("amount", 100));
        JsonNode repaid = postJson("/api/v1/loans/{id}/repay", memberToken, null, loanId);
        assertThat(repaid.get("status").asText()).isEqualTo("REPAID");
        assertThat(repaid.get("repayments").get(0).get("paidAmount").decimalValue())
                .isEqualByComparingTo("1000.00");
        assertThat(repaid.get("repayments").get(0).get("remainingAmount").decimalValue())
                .isEqualByComparingTo("0.00");
        assertThat(loanRepo.findById(loanId).orElseThrow().getStatus()).isEqualTo(LoanStatus.REPAID);
        assertThat(walletRepo.findByUser_Id(userRepo.findByEmail(memberEmail).orElseThrow().getId())
                .orElseThrow().getBalance()).isEqualByComparingTo("110.00");
        assertThat(userRepo.findById(leaderId)).isPresent();
    }

    private void signup(String email, String name, String role) throws Exception {
        signup(email, name, role, "password123");
    }

    private void signup(String email, String name, String role, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of(
                                "name", name, "email", email, "password", password, "role", role))))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }

    private Long currentUserId(String token) throws Exception {
        return getJson("/api/v1/auth/me", token).get("id").asLong();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        String body = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private JsonNode postJson(String path, String token, Object requestBody, Object... pathVariables) throws Exception {
        var request = post(path, pathVariables).header("Authorization", "Bearer " + token);
        if (requestBody != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(requestBody));
        }
        String body = mvc.perform(request).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
    }
}
