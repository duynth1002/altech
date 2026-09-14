package com.example.walletledger.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.walletledger.wallet.domain.exception.BalanceLimitExceededException;
import com.example.walletledger.wallet.domain.exception.InsufficientFundsException;
import com.example.walletledger.wallet.domain.exception.InvalidAmountException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletTest {

    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

    @Test
    void openWalletStartsAtZero() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), NOW);

        assertThat(wallet.getBalance()).isZero();
        assertThat(wallet.getCreatedAt()).isEqualTo(NOW);
        assertThat(wallet.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void creditIncreasesBalanceByExactAmount() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), NOW);

        long after = wallet.credit(250, NOW.plusSeconds(1));

        assertThat(after).isEqualTo(250);
        assertThat(wallet.getBalance()).isEqualTo(250);
    }

    @Test
    void debitDecreasesBalanceByExactAmount() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), NOW);
        wallet.credit(250, NOW);

        long after = wallet.debit(75, NOW.plusSeconds(1));

        assertThat(after).isEqualTo(175);
        assertThat(wallet.getBalance()).isEqualTo(175);
    }

    @Test
    void debitRejectsInsufficientFundsWithoutChangingBalance() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), NOW);
        wallet.credit(50, NOW);

        assertThatThrownBy(() -> wallet.debit(51, NOW.plusSeconds(1)))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(wallet.getBalance()).isEqualTo(50);
    }

    @Test
    void zeroAndNegativeAmountsAreRejected() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> wallet.credit(0, NOW)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> wallet.credit(-1, NOW)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> wallet.debit(0, NOW)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> wallet.debit(-5, NOW)).isInstanceOf(InvalidAmountException.class);
        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    void creditOverflowIsRejectedWithoutChangingBalance() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), NOW);
        wallet.credit(10, NOW);

        assertThatThrownBy(() -> wallet.credit(Long.MAX_VALUE, NOW.plusSeconds(1)))
                .isInstanceOf(BalanceLimitExceededException.class);
        assertThat(wallet.getBalance()).isEqualTo(10);
    }
}
