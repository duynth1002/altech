package com.example.walletledger.wallet.domain.exception;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException {

    private final UUID playerId;

    public WalletNotFoundException(UUID playerId) {
        super("No wallet exists for the given player.");
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }
}
