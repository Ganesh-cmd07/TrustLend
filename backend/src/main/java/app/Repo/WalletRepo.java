package app.Repo;

import app.models.User;
import app.models.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface WalletRepo extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUser(User user);

    Optional<Wallet> findByUser_Id(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from Wallet wallet where wallet.user.id = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") Long userId);
}
