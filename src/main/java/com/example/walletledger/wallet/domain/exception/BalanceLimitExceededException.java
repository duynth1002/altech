package com.example.walletledger.wallet.domain.exception;

public class BalanceLimitExceededException extends RuntimeException {

    public BalanceLimitExceededException() {
        super("The credit would exceed the supported balance range.");
    }
}
