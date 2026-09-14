package com.example.walletledger.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.walletledger.support.AbstractPostgresIntegrationTest;
import com.example.walletledger.wallet.api.dto.BalanceResponse;
import com.example.walletledger.wallet.api.dto.CreateWalletResponse;
import com.example.walletledger.wallet.api.dto.HistoryItemResponse;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class WalletLedgerIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Test
    void createWalletStartsAtZeroWithEmptyLedger() {
        UUID playerId = UUID.randomUUID();

        ResponseEntity<CreateWalletResponse> response = createWallet(playerId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().balance()).isZero();
        assertThat(walletTransactionRepository.countByWalletId(response.getBody().walletId())).isZero();
    }

    @Test
    void duplicateWalletCreationIsRejected() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);

        ResponseEntity<String> duplicate = restTemplate.postForEntity(
                "/api/v1/wallets",
                Map.of("playerId", playerId.toString()),
                String.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).contains("WALLET_ALREADY_EXISTS");
    }

    @Test
    void creditCreatesOneLedgerEntryAndUpdatesBalance() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);

        ResponseEntity<TransactionResponse> credit = credit(playerId, "credit-1", 250, "Completed mission 42", "MISSION", "mission-42");

        assertThat(credit.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(credit.getBody()).isNotNull();
        assertThat(credit.getBody().type()).isEqualTo(TransactionType.CREDIT);
        assertThat(credit.getBody().amount()).isEqualTo(250);
        assertThat(credit.getBody().balanceAfter()).isEqualTo(250);

        UUID walletId = walletId(playerId);
        assertThat(walletRepository.findById(walletId).orElseThrow().getBalance()).isEqualTo(250);
        assertThat(walletTransactionRepository.countByWalletId(walletId)).isEqualTo(1);
        assertThat(walletTransactionRepository.sumSignedDeltas(walletId)).isEqualTo(250);
    }

    @Test
    void debitCreatesOneLedgerEntryAndUpdatesBalance() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        credit(playerId, "credit-1", 250, "Grant", "ADMIN", "admin-1");

        ResponseEntity<TransactionResponse> debit = debit(playerId, "debit-1", 75, "Purchased item", "PURCHASE", "purchase-1");

        assertThat(debit.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(debit.getBody()).isNotNull();
        assertThat(debit.getBody().balanceAfter()).isEqualTo(175);

        UUID walletId = walletId(playerId);
        assertThat(walletRepository.findById(walletId).orElseThrow().getBalance()).isEqualTo(175);
        assertThat(walletTransactionRepository.countByWalletIdAndType(walletId, TransactionType.DEBIT)).isEqualTo(1);
    }

    @Test
    void insufficientDebitChangesNeitherWalletNorLedger() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        credit(playerId, "credit-1", 50, "Grant", "ADMIN", "admin-1");

        ResponseEntity<String> rejected = exchange(
                "/api/v1/wallets/" + playerId + "/debits",
                "debit-too-large",
                """
                        {"amount": 80, "reason": "Too expensive", "referenceType": "PURCHASE", "referenceId": "p-1"}
                        """,
                String.class);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejected.getBody()).contains("INSUFFICIENT_FUNDS");

        UUID walletId = walletId(playerId);
        assertThat(walletRepository.findById(walletId).orElseThrow().getBalance()).isEqualTo(50);
        assertThat(walletTransactionRepository.countByWalletIdAndType(walletId, TransactionType.DEBIT)).isZero();
        assertThat(walletTransactionRepository.sumSignedDeltas(walletId)).isEqualTo(50);
    }

    @Test
    void missingWalletReturnsNotFoundForMutationsAndQueries() {
        UUID missing = UUID.randomUUID();

        assertThat(exchange(
                "/api/v1/wallets/" + missing + "/credits",
                "credit-1",
                body(10, "Grant", null, null),
                String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(
                "/api/v1/wallets/" + missing + "/debits",
                "debit-1",
                body(10, "Spend", null, null),
                String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity("/api/v1/wallets/" + missing + "/balance", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity("/api/v1/wallets/" + missing + "/transactions", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void exactSequentialReplayReturnsOriginalTransaction() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);

        ResponseEntity<TransactionResponse> first = credit(playerId, "same-key", 50, "Grant", "ADMIN", "admin-1");
        ResponseEntity<TransactionResponse> replay = credit(playerId, "same-key", 50, "Grant", "ADMIN", "admin-1");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
        assertThat(replay.getBody()).isNotNull();
        assertThat(first.getBody()).isNotNull();
        assertThat(replay.getBody().transactionId()).isEqualTo(first.getBody().transactionId());

        UUID walletId = walletId(playerId);
        assertThat(walletRepository.findById(walletId).orElseThrow().getBalance()).isEqualTo(50);
        assertThat(walletTransactionRepository.countByWalletId(walletId)).isEqualTo(1);
    }

    @Test
    void reusedKeyWithChangedAmountIsRejected() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        credit(playerId, "same-key", 50, "Grant", "ADMIN", "admin-1");

        ResponseEntity<String> conflict = exchange(
                "/api/v1/wallets/" + playerId + "/credits",
                "same-key",
                """
                        {"amount": 80, "reason": "Grant", "referenceType": "ADMIN", "referenceId": "admin-1"}
                        """,
                String.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(walletRepository.findByPlayerId(playerId).orElseThrow().getBalance()).isEqualTo(50);
        assertThat(walletTransactionRepository.countByWalletId(walletId(playerId))).isEqualTo(1);
    }

    @Test
    void reusedKeyAcrossCreditAndDebitIsRejected() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        credit(playerId, "shared-key", 50, "Grant", "ADMIN", "admin-1");

        ResponseEntity<String> conflict = exchange(
                "/api/v1/wallets/" + playerId + "/debits",
                "shared-key",
                """
                        {"amount": 50, "reason": "Grant", "referenceType": "ADMIN", "referenceId": "admin-1"}
                        """,
                String.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(walletTransactionRepository.countByWalletIdAndType(walletId(playerId), TransactionType.DEBIT)).isZero();
    }

    @Test
    void sameKeyIsAllowedForDifferentWallets() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        createWallet(playerA);
        createWallet(playerB);

        ResponseEntity<TransactionResponse> first = credit(playerA, "shared-key", 25, "Grant", "ADMIN", "a");
        ResponseEntity<TransactionResponse> second = credit(playerB, "shared-key", 25, "Grant", "ADMIN", "a");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().transactionId()).isNotEqualTo(second.getBody().transactionId());
        assertThat(walletRepository.findByPlayerId(playerA).orElseThrow().getBalance()).isEqualTo(25);
        assertThat(walletRepository.findByPlayerId(playerB).orElseThrow().getBalance()).isEqualTo(25);
    }

    @Test
    void historyIsNewestFirstAndPaginated() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        credit(playerId, "c1", 10, "First", "ADMIN", "1");
        credit(playerId, "c2", 20, "Second", "ADMIN", "2");
        credit(playerId, "c3", 30, "Third", "ADMIN", "3");

        ResponseEntity<TransactionHistoryResponse> page0 = restTemplate.getForEntity(
                "/api/v1/wallets/" + playerId + "/transactions?page=0&size=2",
                TransactionHistoryResponse.class);
        ResponseEntity<TransactionHistoryResponse> page1 = restTemplate.getForEntity(
                "/api/v1/wallets/" + playerId + "/transactions?page=1&size=2",
                TransactionHistoryResponse.class);

        assertThat(page0.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page0.getBody()).isNotNull();
        assertThat(page0.getBody().totalElements()).isEqualTo(3);
        assertThat(page0.getBody().totalPages()).isEqualTo(2);
        assertThat(page0.getBody().items()).extracting(HistoryItemResponse::reason)
                .containsExactly("Third", "Second");
        assertThat(page1.getBody()).isNotNull();
        assertThat(page1.getBody().items()).extracting(HistoryItemResponse::reason)
                .containsExactly("First");
        assertThat(page1.getBody().last()).isTrue();
    }

    @Test
    void storedBalanceEqualsLedgerSum() {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        credit(playerId, "c1", 100, "Grant", "ADMIN", "1");
        credit(playerId, "c2", 40, "Bonus", "MISSION", "2");
        debit(playerId, "d1", 30, "Spend", "PURCHASE", "3");

        UUID walletId = walletId(playerId);
        long stored = walletRepository.findById(walletId).orElseThrow().getBalance();
        long reconciled = walletTransactionRepository.sumSignedDeltas(walletId);

        assertThat(stored).isEqualTo(110);
        assertThat(stored).isEqualTo(reconciled);

        BalanceResponse balance = restTemplate.getForEntity(
                "/api/v1/wallets/" + playerId + "/balance",
                BalanceResponse.class).getBody();
        assertThat(balance).isNotNull();
        assertThat(balance.balance()).isEqualTo(110);
        assertThat(balance.balance()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void healthEndpointIsUp() {
        ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");
    }

    private ResponseEntity<CreateWalletResponse> createWallet(UUID playerId) {
        return restTemplate.postForEntity(
                "/api/v1/wallets",
                Map.of("playerId", playerId.toString()),
                CreateWalletResponse.class);
    }

    private ResponseEntity<TransactionResponse> credit(
            UUID playerId,
            String key,
            long amount,
            String reason,
            String referenceType,
            String referenceId) {
        return exchange("/api/v1/wallets/" + playerId + "/credits", key, body(amount, reason, referenceType, referenceId), TransactionResponse.class);
    }

    private ResponseEntity<TransactionResponse> debit(
            UUID playerId,
            String key,
            long amount,
            String reason,
            String referenceType,
            String referenceId) {
        return exchange("/api/v1/wallets/" + playerId + "/debits", key, body(amount, reason, referenceType, referenceId), TransactionResponse.class);
    }

    private <T> ResponseEntity<T> exchange(String path, String idempotencyKey, String json, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers), type);
    }

    private static String body(long amount, String reason, String referenceType, String referenceId) {
        if (referenceType == null && referenceId == null) {
            return """
                    {"amount": %d, "reason": "%s"}
                    """.formatted(amount, reason);
        }
        return """
                {"amount": %d, "reason": "%s", "referenceType": "%s", "referenceId": "%s"}
                """.formatted(amount, reason, referenceType, referenceId);
    }

    private UUID walletId(UUID playerId) {
        return walletRepository.findByPlayerId(playerId).orElseThrow().getId();
    }
}
