package com.example.walletledger.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WalletTransaction() {
    }

    public static WalletTransaction append(
            UUID walletId,
            TransactionType type,
            long amount,
            long balanceAfter,
            String reason,
            String referenceType,
            String referenceId,
            String idempotencyKey,
            String requestHash,
            Instant createdAt) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.id = UUID.randomUUID();
        transaction.walletId = Objects.requireNonNull(walletId, "walletId");
        transaction.type = Objects.requireNonNull(type, "type");
        transaction.amount = amount;
        transaction.balanceAfter = balanceAfter;
        transaction.reason = Objects.requireNonNull(reason, "reason");
        transaction.referenceType = referenceType;
        transaction.referenceId = referenceId;
        transaction.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        transaction.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        transaction.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return transaction;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public TransactionType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public String getReason() {
        return reason;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
