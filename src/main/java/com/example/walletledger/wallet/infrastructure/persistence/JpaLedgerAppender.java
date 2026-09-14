package com.example.walletledger.wallet.infrastructure.persistence;

import com.example.walletledger.wallet.application.LedgerAppender;
import com.example.walletledger.wallet.domain.WalletTransaction;
import org.springframework.stereotype.Component;

@Component
public class JpaLedgerAppender implements LedgerAppender {

    private final WalletTransactionRepository walletTransactionRepository;

    public JpaLedgerAppender(WalletTransactionRepository walletTransactionRepository) {
        this.walletTransactionRepository = walletTransactionRepository;
    }

    @Override
    public WalletTransaction append(WalletTransaction transaction) {
        return walletTransactionRepository.saveAndFlush(transaction);
    }
}
