package com.example.walletledger.wallet.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MoneyMutationRequest(
        @NotNull @Positive Long amount,
        @NotBlank @Size(max = 255) String reason,
        @Size(max = 50) String referenceType,
        @Size(max = 100) String referenceId
) {
    @AssertTrue(message = "referenceType and referenceId must both be present or both omitted")
    public boolean isReferenceConsistent() {
        return hasText(referenceType) == hasText(referenceId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
