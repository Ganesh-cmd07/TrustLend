package app.Repo;

import app.models.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface WalletTransactionRepo extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findAllByWallet_IdOrderByTimestampDescIdDesc(Long walletId);

    Page<WalletTransaction> findAllByWallet_IdOrderByTimestampDescIdDesc(Long walletId, Pageable pageable);

    Page<WalletTransaction> findAllByWallet_Id(Long walletId, Pageable pageable);
}
