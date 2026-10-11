package app.services;

import app.Repo.GroupMembershipRepo;
import app.Repo.LoanRepo;
import app.Repo.LoanRepaymentRepo;
import app.Repo.WalletRepo;
import app.config.CacheConfig;
import app.models.User;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreditScoreCacheTests {

    @Test
    void cachesScoresPerUserAndRecomputesAfterEviction() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(CacheConfig.class, TestBeans.class);
            context.refresh();

            var loans = context.getBean(LoanRepo.class);
            var repayments = context.getBean(LoanRepaymentRepo.class);
            var wallets = context.getBean(WalletRepo.class);
            var memberships = context.getBean(GroupMembershipRepo.class);
            var user = new User();
            user.setId(42L);
            when(loans.findAllForUser(42L)).thenReturn(List.of());
            when(repayments.countDistinctByLoan_Borrower_IdAndStatus(org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
            when(wallets.findByUser_Id(42L)).thenReturn(Optional.empty());
            when(memberships.findAllByUser_IdAndStatusOrderByJoinedAtAscIdAsc(org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(List.of());

            var scores = context.getBean(CreditScoringService.class);
            scores.calculateCreditScore(user);
            scores.calculateCreditScore(user);
            verify(loans).findAllForUser(42L);
            verify(wallets).findByUser_Id(42L);
            verify(memberships).findAllByUser_IdAndStatusOrderByJoinedAtAscIdAsc(org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any());

            context.getBean(org.springframework.cache.CacheManager.class)
                    .getCache("creditScores").evict(42L);
            scores.calculateCreditScore(user);
            verify(loans, org.mockito.Mockito.times(2)).findAllForUser(42L);
        }
    }

    @Configuration
    static class TestBeans {
        @Bean LoanRepo loanRepo() { return mock(LoanRepo.class); }
        @Bean LoanRepaymentRepo repaymentRepo() { return mock(LoanRepaymentRepo.class); }
        @Bean WalletRepo walletRepo() { return mock(WalletRepo.class); }
        @Bean GroupMembershipRepo membershipRepo() { return mock(GroupMembershipRepo.class); }
        @Bean CreditScoringService creditScoringService(LoanRepo loans, LoanRepaymentRepo repayments, WalletRepo wallets, GroupMembershipRepo memberships) {
            return new CreditScoringService(loans, repayments, wallets, memberships);
        }
    }
}
