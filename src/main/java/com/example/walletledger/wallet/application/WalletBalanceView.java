package com.example.walletledger.wallet.application;

import java.time.Instant;
import java.util.UUID;

public record WalletBalanceView(UUID playerId, long balance, Instant asOf) {
}
