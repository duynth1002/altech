package com.example.walletledger.wallet.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateWalletRequest(@NotNull UUID playerId) {
}
