package com.example.walletledger.wallet.domain;

import com.example.walletledger.wallet.domain.exception.BalanceLimitExceededException;
import com.example.walletledger.wallet.domain.exception.InsufficientFundsException;
import com.example.walletledger.wallet.domain.exception.InvalidAmountException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    private UUID id;

    @Column(name = "player_id", nullable = false, unique = true)
    private UUID playerId;

    @Column(nullable = false)
    private long balance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
    }

    public static Wallet open(UUID playerId, Instant now) {
        Wallet wallet = new Wallet();
        wallet.id = UUID.randomUUID();
        wallet.playerId = Objects.requireNonNull(playerId, "playerId");
        wallet.balance = 0L;
        wallet.createdAt = Objects.requireNonNull(now, "now");
        wallet.updatedAt = now;
        return wallet;
    }

    public long credit(long amount, Instant now) {
        requirePositiveAmount(amount);
        try {
            this.balance = Math.addExact(this.balance, amount);
        } catch (ArithmeticException ex) {
            throw new BalanceLimitExceededException();
        }
        this.updatedAt = Objects.requireNonNull(now, "now");
        return this.balance;
    }

    public long debit(long amount, Instant now) {
        requirePositiveAmount(amount);
        if (this.balance < amount) {
            throw new InsufficientFundsException();
        }
        this.balance -= amount;
        this.updatedAt = Objects.requireNonNull(now, "now");
        return this.balance;
    }

    private static void requirePositiveAmount(long amount) {
        if (amount <= 0) {
            throw new InvalidAmountException();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public long getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
