package app.services;

import app.Repo.GroupContributionRepo;
import app.Repo.GroupMembershipRepo;
import app.Repo.SelfHelpGroupRepo;
import app.Repo.UserRepo;
import app.Repo.WalletRepo;
import app.data.GroupContributionRequest;
import app.data.GroupCreateRequest;
import app.exception.ApiException;
import app.models.GroupMembershipStatus;
import app.models.User;
import app.models.UserRole;
import app.models.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GroupLendingServiceTests {

    @Autowired
    private GroupLendingService groupLendingService;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private SelfHelpGroupRepo groupRepo;

    @Autowired
    private GroupMembershipRepo membershipRepo;

    @Autowired
    private GroupContributionRepo contributionRepo;

    @Autowired
    private WalletRepo walletRepo;

    @Test
    void creatingGroupPersistsGroupAndActiveLeaderMembership() {
        User leader = createBorrower();

        var response = groupLendingService.create(
                leader.getEmail(),
                new GroupCreateRequest("Village Savers", "Monthly savings circle"));

        var group = groupRepo.findById(response.id()).orElseThrow();
        var membership = membershipRepo.findByGroup_IdAndUser_Id(group.getId(), leader.getId()).orElseThrow();
        assertThat(group.getName()).isEqualTo("Village Savers");
        assertThat(group.getTotalSavings()).isEqualByComparingTo("0.00");
        assertThat(membership.getStatus()).isEqualTo(GroupMembershipStatus.ACTIVE);
        assertThat(groupLendingService.getActiveGroups(leader.getEmail()))
                .singleElement()
                .satisfies(listedGroup -> assertThat(listedGroup.members())
                        .singleElement()
                        .extracting(member -> member.status())
                        .isEqualTo("ACTIVE"));
    }

    @Test
    void activeMemberContributionUpdatesGroupAndPersistsContribution() {
        User leader = createBorrower();
        Wallet wallet = new Wallet(leader);
        wallet.setBalance(new BigDecimal("25.00"));
        walletRepo.saveAndFlush(wallet);
        var group = groupLendingService.create(
                leader.getEmail(),
                new GroupCreateRequest("Village Savers", null));

        var contribution = groupLendingService.contribute(
                leader.getEmail(),
                group.id(),
                new GroupContributionRequest(new BigDecimal("10.00")));

        assertThat(contribution.amount()).isEqualByComparingTo("10.00");
        assertThat(contributionRepo.findAllByGroup_IdOrderByContributedAtDescIdDesc(group.id()))
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getAmount()).isEqualByComparingTo("10.00");
                    assertThat(saved.getUser().getId()).isEqualTo(leader.getId());
                });
        assertThat(groupRepo.findById(group.id()).orElseThrow().getTotalSavings())
                .isEqualByComparingTo("10.00");
    }

    @Test
    void pendingJoinRequestCannotContribute() {
        User leader = createBorrower();
        User applicant = createBorrower();
        var group = groupLendingService.create(
                leader.getEmail(),
                new GroupCreateRequest("Village Savers", null));
        groupLendingService.requestToJoin(applicant.getEmail(), group.id());

        assertThatThrownBy(() -> groupLendingService.contribute(
                applicant.getEmail(),
                group.id(),
                new GroupContributionRequest(new BigDecimal("1.00"))))
                .isInstanceOf(ApiException.class)
                .hasMessage("Active group membership is required");
    }

    @Test
    void nonLeaderCannotApproveMemberOrCosignGroupLoan() {
        User leader = createBorrower();
        User member = createBorrower();
        User applicant = createBorrower();

        var group = groupLendingService.create(
                leader.getEmail(),
                new GroupCreateRequest("Savers Circle", "Community group"));

        var joinResponse = groupLendingService.requestToJoin(applicant.getEmail(), group.id());

        var memberJoinResponse = groupLendingService.requestToJoin(member.getEmail(), group.id());
        groupLendingService.approveMemberRequest(leader.getEmail(), group.id(), memberJoinResponse.membershipId());

        assertThatThrownBy(() -> groupLendingService.approveMemberRequest(
                member.getEmail(), group.id(), joinResponse.membershipId()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Only the group leader may perform this action");

        var loanResponse = groupLendingService.applyForGroupLoan(
                member.getEmail(),
                group.id(),
                new app.data.LoanApplicationRequest(new BigDecimal("500.00"), new BigDecimal("8.00"), 6));

        assertThatThrownBy(() -> groupLendingService.cosignGroupLoan(
                member.getEmail(), group.id(), loanResponse.id()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Only the group leader may perform this action");
    }

    private User createBorrower() {
        User user = new User();
        user.setName("Group lending test");
        user.setEmail("group-test-" + UUID.randomUUID() + "@example.test");
        user.setPassword("not-used-in-service-test");
        user.setRole(UserRole.BORROWER);
        return userRepo.saveAndFlush(user);
    }
}
