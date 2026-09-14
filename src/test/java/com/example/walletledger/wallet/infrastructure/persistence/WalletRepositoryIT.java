package com.example.walletledger.wallet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.walletledger.support.AbstractPostgresIntegrationTest;
import com.example.walletledger.wallet.domain.Wallet;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class WalletRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void findByPlayerIdForUpdateReturnsPersistedWallet() {
        UUID playerId = UUID.randomUUID();
        Wallet saved = walletRepository.saveAndFlush(Wallet.open(playerId, Instant.parse("2026-09-12T10:00:00Z")));

        Wallet locked = new TransactionTemplate(transactionManager).execute(status ->
                walletRepository.findByPlayerIdForUpdate(playerId).orElseThrow());

        assertThat(locked.getId()).isEqualTo(saved.getId());
        assertThat(locked.getBalance()).isZero();
        assertThat(walletRepository.findByPlayerId(playerId)).isPresent();
    }
}
