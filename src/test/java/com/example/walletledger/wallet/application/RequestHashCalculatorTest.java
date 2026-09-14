package com.example.walletledger.wallet.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestHashCalculatorTest {

    private static final UUID PLAYER = UUID.fromString("9429d823-82f0-4f45-83bf-e55575a8fcec");

    @Test
    void hashIsDeterministicForTheSameEffectiveRequest() {
        String first = RequestHashCalculator.hash("CREDIT", PLAYER, 250, "Completed mission 42", "MISSION", "mission-42");
        String second = RequestHashCalculator.hash("CREDIT", PLAYER, 250, "Completed mission 42", "MISSION", "mission-42");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
        assertThat(first).matches("[0-9a-f]{64}");
    }

    @Test
    void differentEffectiveRequestsProduceDifferentHashes() {
        String baseline = RequestHashCalculator.hash("CREDIT", PLAYER, 250, "Completed mission 42", "MISSION", "mission-42");

        assertThat(RequestHashCalculator.hash("DEBIT", PLAYER, 250, "Completed mission 42", "MISSION", "mission-42"))
                .isNotEqualTo(baseline);
        assertThat(RequestHashCalculator.hash("CREDIT", PLAYER, 251, "Completed mission 42", "MISSION", "mission-42"))
                .isNotEqualTo(baseline);
        assertThat(RequestHashCalculator.hash("CREDIT", PLAYER, 250, "Different reason", "MISSION", "mission-42"))
                .isNotEqualTo(baseline);
        assertThat(RequestHashCalculator.hash("CREDIT", PLAYER, 250, "Completed mission 42", "ADMIN", "mission-42"))
                .isNotEqualTo(baseline);
        assertThat(RequestHashCalculator.hash("CREDIT", PLAYER, 250, "Completed mission 42", "MISSION", "mission-43"))
                .isNotEqualTo(baseline);
        assertThat(RequestHashCalculator.hash(
                        "CREDIT",
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        250,
                        "Completed mission 42",
                        "MISSION",
                        "mission-42"))
                .isNotEqualTo(baseline);
    }

    @Test
    void nullReferencesHashAsEmptySegments() {
        String withNulls = RequestHashCalculator.hash("CREDIT", PLAYER, 10, "Admin grant", null, null);
        String withEmpties = RequestHashCalculator.hash("CREDIT", PLAYER, 10, "Admin grant", "", "");

        assertThat(withNulls).isEqualTo(withEmpties);
    }
}
