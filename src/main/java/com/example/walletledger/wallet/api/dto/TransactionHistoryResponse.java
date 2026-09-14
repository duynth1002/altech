package com.example.walletledger.wallet.api.dto;

import java.util.List;

public record TransactionHistoryResponse(
        List<HistoryItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
