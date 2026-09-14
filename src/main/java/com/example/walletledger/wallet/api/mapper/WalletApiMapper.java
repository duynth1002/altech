package com.example.walletledger.wallet.api.mapper;

import com.example.walletledger.wallet.api.dto.BalanceResponse;
import com.example.walletledger.wallet.api.dto.CreateWalletResponse;
import com.example.walletledger.wallet.api.dto.HistoryItemResponse;
import com.example.walletledger.wallet.api.dto.MoneyMutationRequest;
import com.example.walletledger.wallet.api.dto.TransactionHistoryResponse;
import com.example.walletledger.wallet.api.dto.TransactionResponse;
import com.example.walletledger.wallet.application.MoneyMutationCommand;
import com.example.walletledger.wallet.application.WalletBalanceView;
import com.example.walletledger.wallet.domain.Wallet;
import com.example.walletledger.wallet.domain.WalletTransaction;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class WalletApiMapper {

    public CreateWalletResponse toCreateResponse(Wallet wallet) {
        return new CreateWalletResponse(
                wallet.getId(),
                wallet.getPlayerId(),
                wallet.getBalance(),
                wallet.getCreatedAt());
    }

    public MoneyMutationCommand toCommand(MoneyMutationRequest request) {
        return new MoneyMutationCommand(
                request.amount(),
                request.reason(),
                blankToNull(request.referenceType()),
                blankToNull(request.referenceId()));
    }

    public TransactionResponse toTransactionResponse(WalletTransaction transaction, UUID playerId) {
        return new TransactionResponse(
                transaction.getId(),
                playerId,
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getReason(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getCreatedAt());
    }

    public BalanceResponse toBalanceResponse(WalletBalanceView view) {
        return new BalanceResponse(view.playerId(), view.balance(), view.asOf());
    }

    public TransactionHistoryResponse toHistoryResponse(Page<WalletTransaction> page) {
        return new TransactionHistoryResponse(
                page.getContent().stream().map(this::toHistoryItem).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private HistoryItemResponse toHistoryItem(WalletTransaction transaction) {
        return new HistoryItemResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getReason(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
