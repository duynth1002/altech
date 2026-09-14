package com.example.walletledger.wallet.infrastructure.persistence;

import com.example.walletledger.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByPlayerId(UUID playerId);

    boolean existsByPlayerId(UUID playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.playerId = :playerId")
    Optional<Wallet> findByPlayerIdForUpdate(@Param("playerId") UUID playerId);
}
