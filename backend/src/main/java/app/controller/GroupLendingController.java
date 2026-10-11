package app.controller;

import app.data.GroupContributionRequest;
import app.data.GroupContributionResponse;
import app.data.GroupCreateRequest;
import app.data.GroupMemberResponse;
import app.data.GroupResponse;
import app.data.LoanApplicationRequest;
import app.data.LoanResponse;
import app.services.GroupLendingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupLendingController {

    private final GroupLendingService groupLendingService;

    public GroupLendingController(GroupLendingService groupLendingService) {
        this.groupLendingService = groupLendingService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('BORROWER')")
    public ResponseEntity<GroupResponse> create(
            Authentication authentication,
            @Valid @RequestBody GroupCreateRequest request) {
        return ResponseEntity.status(201)
                .body(groupLendingService.create(authentication.getName(), request));
    }

    @PostMapping("/{id}/join")
    @PreAuthorize("hasRole('BORROWER')")
    public ResponseEntity<GroupMemberResponse> join(
            Authentication authentication,
            @PathVariable Long id) {
        return ResponseEntity.status(201)
                .body(groupLendingService.requestToJoin(authentication.getName(), id));
    }

    @PostMapping("/{id}/contribute")
    @PreAuthorize("hasRole('BORROWER')")
    public GroupContributionResponse contribute(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody GroupContributionRequest request) {
        return groupLendingService.contribute(authentication.getName(), id, request);
    }

    @PostMapping("/{id}/loans/apply")
    @PreAuthorize("hasRole('BORROWER')")
    public LoanResponse applyForGroupLoan(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody LoanApplicationRequest request) {
        return groupLendingService.applyForGroupLoan(authentication.getName(), id, request);
    }

    @GetMapping
    public List<GroupResponse> activeGroups(Authentication authentication) {
        return groupLendingService.getActiveGroups(authentication.getName());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('BORROWER')")
    public List<GroupResponse> myGroups(Authentication authentication) {
        return groupLendingService.getMyGroups(authentication.getName());
    }

    @GetMapping("/{id}")
    public GroupResponse getGroup(
            Authentication authentication,
            @PathVariable Long id) {
        return groupLendingService.getGroup(authentication.getName(), id);
    }

    @PostMapping("/{id}/members/{membershipId}/approve")
    @PreAuthorize("hasRole('BORROWER')")
    public GroupMemberResponse approveMember(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long membershipId) {
        return groupLendingService.approveMemberRequest(authentication.getName(), id, membershipId);
    }

    @PostMapping("/{id}/members/{membershipId}/reject")
    @PreAuthorize("hasRole('BORROWER')")
    public GroupMemberResponse rejectMember(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long membershipId) {
        return groupLendingService.rejectMemberRequest(authentication.getName(), id, membershipId);
    }

    @PostMapping("/{id}/members/{membershipId}/review")
    @PreAuthorize("hasRole('BORROWER')")
    public GroupMemberResponse reviewMember(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long membershipId,
            @RequestParam boolean approve) {
        return groupLendingService.reviewMembership(authentication.getName(), id, membershipId, approve);
    }

    @PostMapping("/{id}/loans/{loanId}/co-sign")
    @PreAuthorize("hasRole('BORROWER')")
    public LoanResponse coSignLoan(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long loanId) {
        return groupLendingService.cosignGroupLoan(authentication.getName(), id, loanId);
    }

    @PostMapping("/{id}/loans/{loanId}/cosign")
    @PreAuthorize("hasRole('BORROWER')")
    public LoanResponse cosignLoan(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long loanId) {
        return groupLendingService.cosignGroupLoan(authentication.getName(), id, loanId);
    }
}
