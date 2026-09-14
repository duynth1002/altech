package com.example.walletledger.wallet.application;

import com.example.walletledger.wallet.domain.Wallet;
import com.example.walletledger.wallet.domain.WalletTransaction;
import com.example.walletledger.wallet.domain.exception.WalletNotFoundException;
import com.example.walletledger.wallet.infrastructure.persistence.WalletRepository;
import com.example.walletledger.wallet.infrastructure.persistence.WalletTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletQueryService {

    private static final Sort HISTORY_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id"));

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final Clock clock;

    public WalletQueryService(
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            Clock clock) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WalletBalanceView getBalance(UUID playerId) {
        Wallet wallet = requireWallet(playerId);
        return new WalletBalanceView(wallet.getPlayerId(), wallet.getBalance(), Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public Page<WalletTransaction> getHistory(UUID playerId, int page, int size) {
        Wallet wallet = requireWallet(playerId);
        return walletTransactionRepository.findByWalletId(wallet.getId(), PageRequest.of(page, size, HISTORY_SORT));
    }

    private Wallet requireWallet(UUID playerId) {
        return walletRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new WalletNotFoundException(playerId));
    }
}
