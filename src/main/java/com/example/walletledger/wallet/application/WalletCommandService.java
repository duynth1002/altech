package com.example.walletledger.wallet.application;

import com.example.walletledger.wallet.domain.TransactionType;
import com.example.walletledger.wallet.domain.Wallet;
import com.example.walletledger.wallet.domain.WalletTransaction;
import com.example.walletledger.wallet.domain.exception.IdempotencyKeyReusedException;
import com.example.walletledger.wallet.domain.exception.WalletAlreadyExistsException;
import com.example.walletledger.wallet.domain.exception.WalletNotFoundException;
import com.example.walletledger.wallet.infrastructure.persistence.WalletRepository;
import com.example.walletledger.wallet.infrastructure.persistence.WalletTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class WalletCommandService {

    private static final Logger log = LoggerFactory.getLogger(WalletCommandService.class);

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final LedgerAppender ledgerAppender;
    private final Clock clock;

    public WalletCommandService(
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            LedgerAppender ledgerAppender,
            Clock clock) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.ledgerAppender = ledgerAppender;
        this.clock = clock;
    }

    @Transactional
    public Wallet createWallet(UUID playerId) {
        if (walletRepository.existsByPlayerId(playerId)) {
            throw new WalletAlreadyExistsException(playerId);
        }
        try {
            Wallet wallet = walletRepository.saveAndFlush(Wallet.open(playerId, Instant.now(clock)));
            log.info("wallet created playerId={} walletId={} outcome=CREATED", playerId, wallet.getId());
            return wallet;
        } catch (DataIntegrityViolationException ex) {
            throw new WalletAlreadyExistsException(playerId);
        }
    }

    @Transactional
    public MutationResult credit(UUID playerId, String idempotencyKey, MoneyMutationCommand command) {
        return mutate(playerId, idempotencyKey, TransactionType.CREDIT, command);
    }

    @Transactional
    public MutationResult debit(UUID playerId, String idempotencyKey, MoneyMutationCommand command) {
        return mutate(playerId, idempotencyKey, TransactionType.DEBIT, command);
    }

    private MutationResult mutate(
            UUID playerId,
            String idempotencyKey,
            TransactionType type,
            MoneyMutationCommand command) {
        Wallet wallet = walletRepository.findByPlayerIdForUpdate(playerId)
                .orElseThrow(() -> new WalletNotFoundException(playerId));

        String requestHash = RequestHashCalculator.hash(
                type.name(),
                playerId,
                command.amount(),
                command.reason(),
                command.referenceType(),
                command.referenceId());

        var existing = walletTransactionRepository.findByWalletIdAndIdempotencyKey(wallet.getId(), idempotencyKey);
        if (existing.isPresent()) {
            WalletTransaction found = existing.get();
            if (found.getRequestHash().equals(requestHash)) {
                log.info(
                        "wallet mutation replayed transactionId={} playerId={} type={} amount={} outcome=REPLAYED idempotencyKeyPreview={}",
                        found.getId(),
                        playerId,
                        found.getType(),
                        found.getAmount(),
                        preview(idempotencyKey));
                return new MutationResult(found, true);
            }
            log.info(
                    "wallet mutation rejected playerId={} type={} amount={} outcome=IDEMPOTENCY_KEY_REUSED idempotencyKeyPreview={}",
                    playerId,
                    type,
                    command.amount(),
                    preview(idempotencyKey));
            throw new IdempotencyKeyReusedException();
        }

        Instant now = Instant.now(clock);
        long balanceAfter = type == TransactionType.CREDIT
                ? wallet.credit(command.amount(), now)
                : wallet.debit(command.amount(), now);
        walletRepository.saveAndFlush(wallet);

        WalletTransaction ledgerEntry = WalletTransaction.append(
                wallet.getId(),
                type,
                command.amount(),
                balanceAfter,
                command.reason(),
                command.referenceType(),
                command.referenceId(),
                idempotencyKey,
                requestHash,
                now);
        WalletTransaction saved = ledgerAppender.append(ledgerEntry);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info(
                        "wallet mutation committed transactionId={} playerId={} type={} amount={} outcome=COMMITTED idempotencyKeyPreview={}",
                        saved.getId(),
                        playerId,
                        saved.getType(),
                        saved.getAmount(),
                        preview(idempotencyKey));
            }
        });

        return new MutationResult(saved, false);
    }

    private static String preview(String idempotencyKey) {
        return idempotencyKey.substring(0, Math.min(8, idempotencyKey.length()));
    }
}
