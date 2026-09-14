package com.example.walletledger.wallet.domain.exception;

import java.util.UUID;

public class WalletAlreadyExistsException extends RuntimeException {

    private final UUID playerId;

    public WalletAlreadyExistsException(UUID playerId) {
        super("A wallet already exists for the given player.");
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }
}
