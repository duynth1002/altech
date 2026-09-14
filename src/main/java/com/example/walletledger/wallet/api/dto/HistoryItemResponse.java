package com.example.walletledger.wallet.api.dto;

import com.example.walletledger.wallet.domain.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record HistoryItemResponse(
        UUID transactionId,
        TransactionType type,
        long amount,
        long balanceAfter,
        String reason,
        String referenceType,
        String referenceId,
        Instant createdAt
) {
}
