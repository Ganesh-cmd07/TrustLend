package app.services;

import app.Repo.GroupContributionRepo;
import app.Repo.GroupMembershipRepo;
import app.Repo.LoanRepo;
import app.Repo.UserRepo;
import app.Repo.SelfHelpGroupRepo;
import app.Repo.WalletRepo;
import app.Repo.WalletTransactionRepo;
import app.data.GroupContributionRequest;
import app.data.GroupContributionResponse;
import app.data.GroupCreateRequest;
import app.data.GroupMemberResponse;
import app.data.GroupResponse;
import app.data.LoanApplicationRequest;
import app.data.LoanResponse;
import app.exception.ApiException;
import app.models.GroupContribution;
import app.models.GroupMembership;
import app.models.GroupMembershipRole;
import app.models.GroupMembershipStatus;
import app.models.GroupStatus;
import app.models.Loan;
import app.models.LoanStatus;
import app.models.SelfHelpGroup;
import app.models.User;
import app.models.UserRole;
import app.models.Wallet;
import app.models.WalletTransaction;
import app.models.WalletTransactionType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class GroupLendingService {

    private final SelfHelpGroupRepo groupRepo;
    private final GroupMembershipRepo membershipRepo;
    private final GroupContributionRepo contributionRepo;
    private final LoanRepo loanRepo;
    private final UserRepo userRepo;
    private final WalletRepo walletRepo;
    private final WalletTransactionRepo walletTransactionRepo;
    private final CreditScoringService creditScoringService;

    public GroupLendingService(
            SelfHelpGroupRepo groupRepo,
            GroupMembershipRepo membershipRepo,
            GroupContributionRepo contributionRepo,
            LoanRepo loanRepo,
            UserRepo userRepo,
            WalletRepo walletRepo,
            WalletTransactionRepo walletTransactionRepo,
            CreditScoringService creditScoringService) {
        this.groupRepo = groupRepo;
        this.membershipRepo = membershipRepo;
        this.contributionRepo = contributionRepo;
        this.loanRepo = loanRepo;
        this.userRepo = userRepo;
        this.walletRepo = walletRepo;
        this.walletTransactionRepo = walletTransactionRepo;
        this.creditScoringService = creditScoringService;
    }

    @Transactional
    @CacheEvict(cacheNames = "creditScores", allEntries = true)
    public GroupResponse create(String email, GroupCreateRequest request) {
        User leader = findUser(email);
        requireBorrower(leader);
        SelfHelpGroup group = groupRepo.saveAndFlush(
                new SelfHelpGroup(request.name().trim(), normalizeDescription(request.description()), leader));
        membershipRepo.save(new GroupMembership(
                group,
                leader,
                GroupMembershipRole.LEADER,
                GroupMembershipStatus.ACTIVE
        ));
        return groupResponse(group, leader, false);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getActiveGroups(String email) {
        User viewer = findUser(email);
        return groupRepo.findAllByStatusOrderByCreatedAtDescIdDesc(GroupStatus.ACTIVE)
                .stream()
                .map(group -> {
                    boolean isLeader = group.getLeader().getId().equals(viewer.getId());
                    if (isLeader) {
                        return groupResponse(group, viewer, true);
                    }
                    List<GroupMemberResponse> viewerMembership = membershipRepo
                            .findByGroup_IdAndUser_Id(group.getId(), viewer.getId())
                            .map(GroupMemberResponse::from)
                            .map(List::of)
                            .orElseGet(List::of);
                    return GroupResponse.from(group, viewerMembership, List.of(), List.of());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups(String email) {
        User user = findUser(email);
        return membershipRepo.findAllByUser_IdAndStatusOrderByJoinedAtAscIdAsc(
                        user.getId(),
                        GroupMembershipStatus.ACTIVE)
                .stream()
                .map(membership -> groupResponse(membership.getGroup(), user, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(String email, Long groupId) {
        User user = findUser(email);
        SelfHelpGroup group = findActiveGroup(groupId);
        boolean isLeader = group.getLeader().getId().equals(user.getId());
        if (!isLeader && !isActiveMember(groupId, user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Group details are available to active group members");
        }
        return groupResponse(group, user, isLeader);
    }

    @Transactional
    public GroupMemberResponse requestToJoin(String email, Long groupId) {
        User user = findUser(email);
        requireBorrower(user);
        SelfHelpGroup group = findActiveGroup(groupId);
        if (group.getLeader().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "You are already the leader of this group");
        }

        GroupMembership membership = membershipRepo.findByGroup_IdAndUser_Id(groupId, user.getId())
                .map(existing -> {
                    if (existing.getStatus() == GroupMembershipStatus.ACTIVE) {
                        throw new ApiException(HttpStatus.CONFLICT, "You are already an active group member");
                    }
                    if (existing.getStatus() == GroupMembershipStatus.PENDING) {
                        throw new ApiException(HttpStatus.CONFLICT, "Your membership request is already pending");
                    }
                    existing.requestAgain();
                    return existing;
                })
                .orElseGet(() -> new GroupMembership(
                        group,
                        user,
                        GroupMembershipRole.MEMBER,
                        GroupMembershipStatus.PENDING
                ));
        return GroupMemberResponse.from(membershipRepo.save(membership));
    }

    @Transactional
    @CacheEvict(cacheNames = "creditScores", allEntries = true)
    public GroupMemberResponse reviewMembership(
            String email,
            Long groupId,
            Long membershipId,
            boolean approve) {
        User leader = findUser(email);
        SelfHelpGroup group = findActiveGroup(groupId);
        requireLeader(group, leader);
        GroupMembership membership = membershipRepo.findById(membershipId)
                .filter(candidate -> candidate.getGroup().getId().equals(groupId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Membership request was not found"));
        if (membership.getStatus() != GroupMembershipStatus.PENDING
                || membership.getRole() != GroupMembershipRole.MEMBER) {
            throw new ApiException(HttpStatus.CONFLICT, "Membership request has already been reviewed");
        }
        if (approve) {
            membership.approve();
        } else {
            membership.reject();
        }
        return GroupMemberResponse.from(membership);
    }

    @Transactional
    @CacheEvict(cacheNames = "creditScores", allEntries = true)
    public GroupMemberResponse approveMemberRequest(String email, Long groupId, Long membershipId) {
        return reviewMembership(email, groupId, membershipId, true);
    }

    @Transactional
    @CacheEvict(cacheNames = "creditScores", allEntries = true)
    public GroupMemberResponse approveMemberRequest(User leader, Long groupId, Long membershipId) {
        return reviewMembership(leader.getEmail(), groupId, membershipId, true);
    }

    @Transactional
    public GroupMemberResponse rejectMemberRequest(String email, Long groupId, Long membershipId) {
        return reviewMembership(email, groupId, membershipId, false);
    }

    @Transactional
    public GroupMemberResponse rejectMemberRequest(User leader, Long groupId, Long membershipId) {
        return reviewMembership(leader.getEmail(), groupId, membershipId, false);
    }

    @Transactional
    @CacheEvict(cacheNames = "creditScores", allEntries = true)
    public GroupContributionResponse contribute(
            String email,
            Long groupId,
            GroupContributionRequest request) {
        User user = findUser(email);
        requireBorrower(user);
        SelfHelpGroup group = groupRepo.findByIdForUpdate(groupId)
                .filter(candidate -> candidate.getStatus() == GroupStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Active group was not found"));
        requireActiveMembership(groupId, user.getId());
        Wallet wallet = walletRepo.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet was not found"));
        if (wallet.getBalance().compareTo(request.amount()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Insufficient wallet balance for contribution");
        }

        BigDecimal balanceAfter = wallet.getBalance().subtract(request.amount());
        wallet.setBalance(balanceAfter);
        group.addSavings(request.amount());
        walletRepo.save(wallet);
        groupRepo.save(group);
        walletTransactionRepo.save(new WalletTransaction(
                wallet,
                request.amount(),
                WalletTransactionType.WITHDRAWAL,
                balanceAfter
        ));
        return GroupContributionResponse.from(
                contributionRepo.save(new GroupContribution(group, user, request.amount())));
    }

    @Transactional
    public LoanResponse applyForGroupLoan(String email, Long groupId, LoanApplicationRequest request) {
        User borrower = findUser(email);
        requireBorrower(borrower);
        SelfHelpGroup group = findActiveGroup(groupId);
        requireActiveMembership(groupId, borrower.getId());
        if (group.getLeader().getId().equals(borrower.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "The group leader must co-sign; leaders cannot request their own group-backed loan");
        }

        if (creditScoringService != null) {
            Map<String, Object> scoreData = creditScoringService.calculateCreditScore(borrower);
            if (!Boolean.TRUE.equals(scoreData.get("eligible"))) {
                int score = ((Number) scoreData.get("creditScore")).intValue();
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Your credit score of " + score + " is below the minimum eligibility threshold of 550."
                );
            }
            BigDecimal maxAllowed = (BigDecimal) scoreData.get("maxEligibleLoan");
            if (maxAllowed != null && request.amount().compareTo(maxAllowed) > 0) {
                int score = ((Number) scoreData.get("creditScore")).intValue();
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        CreditScoringService.loanLimitExceededMessage(request.amount(), maxAllowed, score)
                );
            }
        }

        Loan loan = new Loan(
                borrower,
                request.amount(),
                request.interestRate(),
                request.termMonths(),
                group
        );
        loan.submitApplication();
        loan = loanRepo.save(loan);
        return LoanResponse.from(loan, List.of());
    }

    @Transactional
    public LoanResponse cosignGroupLoan(String email, Long groupId, Long loanId) {
        User leader = findUser(email);
        SelfHelpGroup group = findActiveGroup(groupId);
        requireLeader(group, leader);
        Loan loan = loanRepo.findByIdForUpdate(loanId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Group loan application was not found"));
        if (loan.getGroup() == null || !loan.getGroup().getId().equals(groupId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Group loan application was not found");
        }
        if ((loan.getStatus() != LoanStatus.SUBMITTED && loan.getStatus() != LoanStatus.PENDING)
                || loan.getGroupApprovedBy() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Group loan application has already been reviewed");
        }
        if (loan.getBorrower().getId().equals(leader.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "A leader cannot co-sign their own loan");
        }
        loan.cosignGroupLoan(leader);
        return LoanResponse.from(loan, List.of());
    }

    @Transactional
    public LoanResponse cosignGroupLoan(User leader, Long groupId, Long loanId) {
        return cosignGroupLoan(leader.getEmail(), groupId, loanId);
    }

    @Transactional
    public LoanResponse coSignGroupLoan(String email, Long groupId, Long loanId) {
        return cosignGroupLoan(email, groupId, loanId);
    }

    @Transactional
    public LoanResponse coSignGroupLoan(User leader, Long groupId, Long loanId) {
        return cosignGroupLoan(leader.getEmail(), groupId, loanId);
    }

    private GroupResponse groupResponse(SelfHelpGroup group, User viewer, boolean leaderView) {
        List<GroupMemberResponse> activeMembers = membershipRepo
                .findAllByGroup_IdAndStatusOrderByJoinedAtAscIdAsc(
                        group.getId(),
                        GroupMembershipStatus.ACTIVE)
                .stream()
                .map(GroupMemberResponse::from)
                .toList();
        List<GroupMemberResponse> pendingMembers = leaderView
                ? membershipRepo.findAllByGroup_IdAndStatusOrderByJoinedAtAscIdAsc(
                                group.getId(),
                                GroupMembershipStatus.PENDING)
                        .stream()
                        .map(GroupMemberResponse::from)
                        .toList()
                : List.of();
        List<GroupMemberResponse> members = java.util.stream.Stream
                .concat(activeMembers.stream(), pendingMembers.stream())
                .toList();
        List<GroupContributionResponse> contributions = contributionRepo
                .findAllByGroup_IdOrderByContributedAtDescIdDesc(group.getId())
                .stream()
                .map(GroupContributionResponse::from)
                .toList();
        List<LoanResponse> pendingCosignatures = leaderView
                ? loanRepo.findAwaitingGroupCosign(group.getId(), List.of(LoanStatus.SUBMITTED, LoanStatus.PENDING))
                        .stream()
                        .map(loan -> LoanResponse.from(loan, List.of()))
                        .toList()
                : List.of();
        return GroupResponse.from(group, members, contributions, pendingCosignatures);
    }

    private SelfHelpGroup findActiveGroup(Long groupId) {
        return groupRepo.findById(groupId)
                .filter(group -> group.getStatus() == GroupStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Active group was not found"));
    }

    private boolean isActiveMember(Long groupId, Long userId) {
        return membershipRepo.findByGroup_IdAndUser_Id(groupId, userId)
                .map(membership -> membership.getStatus() == GroupMembershipStatus.ACTIVE)
                .orElse(false);
    }

    private void requireActiveMembership(Long groupId, Long userId) {
        if (!isActiveMember(groupId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Active group membership is required");
        }
    }

    private void requireLeader(SelfHelpGroup group, User user) {
        if (!group.getLeader().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the group leader may perform this action");
        }
    }

    private void requireBorrower(User user) {
        if (user.getRole() != UserRole.BORROWER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only borrowers may perform this action");
        }
    }

    private User findUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
