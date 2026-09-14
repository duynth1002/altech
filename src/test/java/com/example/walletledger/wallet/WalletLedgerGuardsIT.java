package com.example.walletledger.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.walletledger.support.AbstractPostgresIntegrationTest;
import com.example.walletledger.wallet.api.dto.CreateWalletResponse;
import com.example.walletledger.wallet.api.dto.TransactionHistoryResponse;
import com.example.walletledger.wallet.api.dto.TransactionResponse;
import com.example.walletledger.wallet.domain.TransactionType;
import com.example.walletledger.wallet.infrastructure.persistence.WalletRepository;
import com.example.walletledger.wallet.infrastructure.persistence.WalletTransactionRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class WalletLedgerGuardsIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void zeroAndNegativeAmountsAreRejectedAndChangeNothing() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        credit(playerId, "seed", 40, "Seed", "ADMIN", "seed");

        assertValidationRejected(playerId, "/credits", "zero-credit", """
                {"amount": 0, "reason": "Zero credit"}
                """);
        assertValidationRejected(playerId, "/credits", "negative-credit", """
                {"amount": -5, "reason": "Negative credit"}
                """);
        assertValidationRejected(playerId, "/debits", "zero-debit", """
                {"amount": 0, "reason": "Zero debit"}
                """);
        assertValidationRejected(playerId, "/debits", "negative-debit", """
                {"amount": -8, "reason": "Negative debit"}
                """);

        UUID walletId = walletId(playerId);
        assertThat(walletRepository.findById(walletId).orElseThrow().getBalance()).isEqualTo(40);
        assertThat(walletTransactionRepository.countByWalletId(walletId)).isEqualTo(1);
    }

    @Test
    void creditOverflowIsRejectedAndChangesNothing() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        credit(playerId, "seed", 10, "Seed", "ADMIN", "seed");

        ResponseEntity<String> overflow = exchange(
                "/api/v1/wallets/" + playerId + "/credits",
                "overflow",
                """
                        {"amount": %d, "reason": "Overflow"}
                        """.formatted(Long.MAX_VALUE),
                String.class);

        assertThat(overflow.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(overflow.getBody()).contains("BALANCE_LIMIT_EXCEEDED");

        UUID walletId = walletId(playerId);
        assertThat(walletRepository.findById(walletId).orElseThrow().getBalance()).isEqualTo(10);
        assertThat(walletTransactionRepository.countByWalletId(walletId)).isEqualTo(1);
        assertThat(walletTransactionRepository.sumSignedDeltas(walletId)).isEqualTo(10);
    }

    @Test
    void exactDebitReplayReturnsOriginalTransaction() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        credit(playerId, "seed", 80, "Seed", "ADMIN", "seed");

        ResponseEntity<TransactionResponse> first = debit(playerId, "same-debit", 25, "Purchase", "PURCHASE", "p-1");
        ResponseEntity<TransactionResponse> replay = debit(playerId, "same-debit", 25, "Purchase", "PURCHASE", "p-1");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
        assertThat(first.getBody()).isNotNull();
        assertThat(replay.getBody()).isNotNull();
        assertThat(replay.getBody().transactionId()).isEqualTo(first.getBody().transactionId());
        assertThat(replay.getBody().balanceAfter()).isEqualTo(55);

        UUID walletId = walletId(playerId);
        assertThat(walletRepository.findById(walletId).orElseThrow().getBalance()).isEqualTo(55);
        assertThat(walletTransactionRepository.countByWalletIdAndType(walletId, TransactionType.DEBIT)).isEqualTo(1);
        assertThat(walletTransactionRepository.sumSignedDeltas(walletId)).isEqualTo(55);
    }

    @Test
    void reusedKeyWithChangedReasonIsRejected() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        credit(playerId, "same-key", 20, "Original reason", "ADMIN", "admin-1");

        ResponseEntity<String> conflict = exchange(
                "/api/v1/wallets/" + playerId + "/credits",
                "same-key",
                """
                        {"amount": 20, "reason": "Different reason", "referenceType": "ADMIN", "referenceId": "admin-1"}
                        """,
                String.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(walletRepository.findByPlayerId(playerId).orElseThrow().getBalance()).isEqualTo(20);
        assertThat(walletTransactionRepository.countByWalletId(walletId(playerId))).isEqualTo(1);
    }

    @Test
    void missingIdempotencyKeyIsRejectedAndChangesNothing() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/wallets/" + playerId + "/credits",
                HttpMethod.POST,
                new HttpEntity<>("""
                        {"amount": 15, "reason": "Missing key"}
                        """, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_ERROR");
        assertThat(walletRepository.findByPlayerId(playerId).orElseThrow().getBalance()).isZero();
        assertThat(walletTransactionRepository.countByWalletId(walletId(playerId))).isZero();
    }

    @Test
    void incompleteReferencePairIsRejectedAndChangesNothing() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);

        assertValidationRejected(playerId, "/credits", "partial-ref", """
                {"amount": 15, "reason": "Partial reference", "referenceType": "MISSION"}
                """);

        UUID walletId = walletId(playerId);
        assertThat(walletRepository.findById(walletId).orElseThrow().getBalance()).isZero();
        assertThat(walletTransactionRepository.countByWalletId(walletId)).isZero();
    }

    @Test
    void historyDefaultsToPageSizeTwenty() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);

        ResponseEntity<TransactionHistoryResponse> response = restTemplate.getForEntity(
                "/api/v1/wallets/" + playerId + "/transactions",
                TransactionHistoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().page()).isZero();
        assertThat(response.getBody().size()).isEqualTo(20);
        assertThat(response.getBody().items()).isEmpty();
        assertThat(response.getBody().first()).isTrue();
        assertThat(response.getBody().last()).isTrue();
    }

    @Test
    void ledgerRowsCannotBeUpdatedOrDeleted() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        TransactionResponse credit = credit(playerId, "ledger-1", 12, "Protected", "ADMIN", "admin-1").getBody();
        assertThat(credit).isNotNull();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "update wallet_transactions set reason = ? where id = ?",
                        "tampered",
                        credit.transactionId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "delete from wallet_transactions where id = ?",
                        credit.transactionId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        UUID walletId = walletId(playerId);
        assertThat(walletTransactionRepository.countByWalletId(walletId)).isEqualTo(1);
        assertThat(walletTransactionRepository.findById(credit.transactionId()).orElseThrow().getReason())
                .isEqualTo("Protected");
    }

    private void assertValidationRejected(UUID playerId, String operationPath, String key, String json) {
        ResponseEntity<String> response = exchange(
                "/api/v1/wallets/" + playerId + operationPath,
                key,
                json,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_ERROR");
    }

    private ResponseEntity<CreateWalletResponse> createWallet(UUID playerId) {
        ResponseEntity<CreateWalletResponse> response = restTemplate.postForEntity(
                "/api/v1/wallets",
                Map.of("playerId", playerId.toString()),
                CreateWalletResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response;
    }

    private ResponseEntity<TransactionResponse> credit(
            UUID playerId,
            String key,
            long amount,
            String reason,
            String referenceType,
            String referenceId) {
        ResponseEntity<TransactionResponse> response = exchange(
                "/api/v1/wallets/" + playerId + "/credits",
                key,
                body(amount, reason, referenceType, referenceId),
                TransactionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response;
    }

    private ResponseEntity<TransactionResponse> debit(
            UUID playerId,
            String key,
            long amount,
            String reason,
            String referenceType,
            String referenceId) {
        return exchange(
                "/api/v1/wallets/" + playerId + "/debits",
                key,
                body(amount, reason, referenceType, referenceId),
                TransactionResponse.class);
    }

    private <T> ResponseEntity<T> exchange(String path, String idempotencyKey, String json, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers), type);
    }

    private static String body(long amount, String reason, String referenceType, String referenceId) {
        return """
                {"amount": %d, "reason": "%s", "referenceType": "%s", "referenceId": "%s"}
                """.formatted(amount, reason, referenceType, referenceId);
    }

    private UUID walletId(UUID playerId) {
        return walletRepository.findByPlayerId(playerId).orElseThrow().getId();
    }
}
