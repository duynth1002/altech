package com.example.walletledger.wallet.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateWalletResponse(
        UUID walletId,
        UUID playerId,
        long balance,
        Instant createdAt
) {
}
