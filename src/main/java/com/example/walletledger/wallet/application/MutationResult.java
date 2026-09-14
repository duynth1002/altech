package com.example.walletledger.wallet.application;

import com.example.walletledger.wallet.domain.WalletTransaction;

public record MutationResult(WalletTransaction transaction, boolean replayed) {
}
