package com.example.walletledger.wallet.application;

import com.example.walletledger.wallet.domain.WalletTransaction;

public interface LedgerAppender {

    WalletTransaction append(WalletTransaction transaction);
}
