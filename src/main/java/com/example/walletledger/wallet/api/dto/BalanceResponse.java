package com.example.walletledger.wallet.api.dto;

import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(UUID playerId, long balance, Instant asOf) {
}
