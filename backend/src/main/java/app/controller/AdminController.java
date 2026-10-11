package app.controller;

import app.data.LenderRequestResponse;
import app.data.PagedUsersResponse;
import app.data.LoanResponse;
import app.data.LoanReviewRequest;
import app.data.LoanReviewResponse;
import app.services.LenderRequestService;
import app.services.LoanService;
import app.services.KnowledgeIndexJob;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import app.models.UserRole;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final LenderRequestService lenderRequestService;
    private final KnowledgeIndexJob knowledgeIndexJob;
    private final LoanService loanService;

    public AdminController(LenderRequestService lenderRequestService, KnowledgeIndexJob knowledgeIndexJob,
                           LoanService loanService) {
        this.lenderRequestService = lenderRequestService;
        this.knowledgeIndexJob = knowledgeIndexJob;
        this.loanService = loanService;
    }

    @GetMapping("/users")
    public PagedUsersResponse getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false, name = "q") String query) {
        return lenderRequestService.searchUsers(page, size, role, query);
    }

    @GetMapping("/lender-requests")
    public List<LenderRequestResponse> getPendingLenderRequests() {
        return lenderRequestService.getPendingRequests();
    }

    @GetMapping("/loan-applications")
    public List<LoanResponse> getSubmittedLoanApplications(Authentication authentication) {
        return loanService.getApplicationsForReview(authentication.getName());
    }

    @PostMapping("/loan-applications/{loanId}/review")
    public LoanResponse reviewLoanApplication(
            Authentication authentication,
            @PathVariable Long loanId,
            @Valid @RequestBody LoanReviewRequest request) {
        return loanService.reviewApplication(authentication.getName(), loanId, request);
    }

    @GetMapping("/loan-review-history")
    public List<LoanReviewResponse> getRecentLoanReviewHistory(Authentication authentication) {
        return loanService.getRecentReviewHistory(authentication.getName());
    }

    @PostMapping("/lender-requests/{requestId}/approve")
    public LenderRequestResponse approveLenderRequest(@PathVariable Long requestId) {
        return lenderRequestService.approveRequest(requestId);
    }

    @PostMapping("/lender-requests/{requestId}/reject")
    public LenderRequestResponse rejectLenderRequest(@PathVariable Long requestId) {
        return lenderRequestService.rejectRequest(requestId);
    }

    @PostMapping("/ai/reindex")
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> reindexKnowledge() {
        knowledgeIndexJob.reindexTrustedDocuments();
        return org.springframework.http.ResponseEntity.accepted().body(java.util.Map.of(
                "message", "Knowledge indexing started. Check /api/v1/ai/status for indexed chunk count."));
    }
}
