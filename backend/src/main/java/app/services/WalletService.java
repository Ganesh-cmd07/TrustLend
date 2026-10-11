package app.services;

import app.Repo.UserRepo;
import app.Repo.WalletRepo;
import app.Repo.WalletTransactionRepo;
import app.data.WalletResponse;
import app.data.WalletTransactionResponse;
import app.data.WalletTransactionPageResponse;
import app.exception.ApiException;
import app.models.User;
import app.models.Wallet;
import app.models.WalletTransaction;
import app.models.WalletTransactionType;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WalletService {

    private final WalletRepo walletRepo;
    private final WalletTransactionRepo walletTransactionRepo;
    private final UserRepo userRepo;

    public WalletService(
            WalletRepo walletRepo,
            WalletTransactionRepo walletTransactionRepo,
            UserRepo userRepo) {
        this.walletRepo = walletRepo;
        this.walletTransactionRepo = walletTransactionRepo;
        this.userRepo = userRepo;
    }

    public Wallet createWallet(User user) {
        return walletRepo.findByUser(user)
                .orElseGet(() -> walletRepo.save(new Wallet(user)));
    }

    public Wallet getWallet(User user) {
        return walletRepo.findByUser(user)
                .orElseGet(() -> walletRepo.save(new Wallet(user)));
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletForUser(String email) {
        User user = findUser(email);
        Wallet wallet = walletRepo.findByUser(user)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Wallet was not found for this user"
                ));
        List<WalletTransactionResponse> transactions = walletTransactionRepo
                .findAllByWallet_IdOrderByTimestampDescIdDesc(wallet.getId(), PageRequest.of(0, 20))
                .getContent()
                .stream()
                .map(WalletTransactionResponse::from)
                .toList();
        return WalletResponse.from(wallet, transactions);
    }

    @Transactional(readOnly = true)
    public WalletTransactionPageResponse getTransactionsForUser(String email, int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100");
        }
        User user = findUser(email);
        Wallet wallet = walletRepo.findByUser(user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet was not found for this user"));
        var transactions = walletTransactionRepo.findAllByWallet_IdOrderByTimestampDescIdDesc(
                wallet.getId(), PageRequest.of(page, size));
        return WalletTransactionPageResponse.from(transactions);
    }

    @Transactional
    @CacheEvict(cacheNames = "creditScores", allEntries = true)
    public WalletResponse deposit(String email, BigDecimal amount) {
        Wallet wallet = lockWallet(email);
        BigDecimal updatedBalance = wallet.getBalance().add(amount);
        wallet.setBalance(updatedBalance);
        walletRepo.save(wallet);
        walletTransactionRepo.save(new WalletTransaction(
                wallet,
                amount,
                WalletTransactionType.DEPOSIT,
                updatedBalance
        ));
        return getWalletForUser(email);
    }

    @Transactional
    @CacheEvict(cacheNames = "creditScores", allEntries = true)
    public WalletResponse withdraw(String email, BigDecimal amount) {
        Wallet wallet = lockWallet(email);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Insufficient wallet balance");
        }

        BigDecimal updatedBalance = wallet.getBalance().subtract(amount);
        wallet.setBalance(updatedBalance);
        walletRepo.save(wallet);
        walletTransactionRepo.save(new WalletTransaction(
                wallet,
                amount,
                WalletTransactionType.WITHDRAWAL,
                updatedBalance
        ));
        return getWalletForUser(email);
    }

    private Wallet lockWallet(String email) {
        User user = findUser(email);
        return walletRepo.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Wallet was not found for this user"
                ));
    }

    private User findUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user was not found"
                ));
    }
}
