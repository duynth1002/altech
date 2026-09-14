package com.example.walletledger.wallet.domain.exception;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("The idempotency key was already used for a different request.");
    }
}
