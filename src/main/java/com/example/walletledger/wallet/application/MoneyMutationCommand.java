package com.example.walletledger.wallet.application;

public record MoneyMutationCommand(
        long amount,
        String reason,
        String referenceType,
        String referenceId
) {
}
