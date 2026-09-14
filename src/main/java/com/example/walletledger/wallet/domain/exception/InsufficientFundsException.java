package com.example.walletledger.wallet.domain.exception;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException() {
        super("The wallet balance is lower than the requested debit.");
    }
}
