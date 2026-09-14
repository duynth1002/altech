package com.example.walletledger.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.example.walletledger.support.AbstractPostgresIntegrationTest;
import com.example.walletledger.wallet.application.LedgerAppender;
import com.example.walletledger.wallet.application.MoneyMutationCommand;
import com.example.walletledger.wallet.application.WalletCommandService;
import com.example.walletledger.wallet.domain.WalletTransaction;
import com.example.walletledger.wallet.infrastructure.persistence.WalletRepository;
import com.example.walletledger.wallet.infrastructure.persistence.WalletTransactionRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class LedgerRollbackIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private WalletCommandService walletCommandService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @MockitoSpyBean
    private LedgerAppender ledgerAppender;

    @Test
    void ledgerWriteFailureRollsBackBalanceAndLedger() {
        UUID playerId = UUID.randomUUID();
        walletCommandService.createWallet(playerId);

        doThrow(new RuntimeException("forced ledger failure"))
                .when(ledgerAppender)
                .append(any(WalletTransaction.class));

        assertThatThrownBy(() -> walletCommandService.credit(
                        playerId,
                        "rollback-key",
                        new MoneyMutationCommand(40, "Should roll back", "ADMIN", "rollback-1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("forced ledger failure");

        var wallet = walletRepository.findByPlayerId(playerId).orElseThrow();
        assertThat(wallet.getBalance()).isZero();
        assertThat(walletTransactionRepository.countByWalletId(wallet.getId())).isZero();
    }
}
