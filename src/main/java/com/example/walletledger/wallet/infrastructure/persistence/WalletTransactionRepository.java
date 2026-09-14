package com.example.walletledger.wallet.infrastructure.persistence;

import com.example.walletledger.wallet.domain.TransactionType;
import com.example.walletledger.wallet.domain.WalletTransaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    Optional<WalletTransaction> findByWalletIdAndIdempotencyKey(UUID walletId, String idempotencyKey);

    Page<WalletTransaction> findByWalletId(UUID walletId, Pageable pageable);

    long countByWalletId(UUID walletId);

    long countByWalletIdAndType(UUID walletId, TransactionType type);

    @Query("""
            select coalesce(sum(case when t.type = com.example.walletledger.wallet.domain.TransactionType.CREDIT
                then t.amount else -t.amount end), 0)
            from WalletTransaction t
            where t.walletId = :walletId
            """)
    long sumSignedDeltas(@Param("walletId") UUID walletId);
}
