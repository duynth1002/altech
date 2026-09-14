package com.example.walletledger.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.walletledger.support.AbstractPostgresIntegrationTest;
import com.example.walletledger.wallet.api.dto.CreateWalletResponse;
import com.example.walletledger.wallet.api.dto.TransactionResponse;
import com.example.walletledger.wallet.domain.TransactionType;
import com.example.walletledger.wallet.infrastructure.persistence.WalletRepository;
import com.example.walletledger.wallet.infrastructure.persistence.WalletTransactionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class ConcurrentWalletIT extends AbstractPostgresIntegrationTest {

    private static final int TIMEOUT_SECONDS = 30;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Test
    void concurrentDebitsCannotOverspend() throws Exception {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        credit(playerId, "seed", 100, "seed");

        List<ResponseEntity<String>> responses = runConcurrently(2, index ->
                exchangeRaw("/api/v1/wallets/" + playerId + "/debits", "debit-" + index, debitBody(80)));

        long successes = responses.stream().filter(response -> response.getStatusCode() == HttpStatus.CREATED).count();
        long insufficient = responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.CONFLICT)
                .filter(response -> response.getBody() != null && response.getBody().contains("INSUFFICIENT_FUNDS"))
                .count();

        UUID walletId = walletId(playerId);
        assertThat(successes).isEqualTo(1);
        assertThat(insufficient).isEqualTo(1);
        assertThat(walletRepository.findById(walletId).orElseThrow().getBalance()).isEqualTo(20);
        assertThat(walletTransactionRepository.countByWalletIdAndType(walletId, TransactionType.DEBIT)).isEqualTo(1);
        assertThat(walletTransactionRepository.sumSignedDeltas(walletId))
                .isEqualTo(walletRepository.findById(walletId).orElseThrow().getBalance());
    }

    @Test
    void concurrentDuplicateSubmissionsCreateOneLedgerEntry() throws Exception {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        int requestCount = 8;

        List<ResponseEntity<TransactionResponse>> responses = runConcurrently(requestCount, index ->
                exchange("/api/v1/wallets/" + playerId + "/credits", "same-key", creditBody(50)));

        long created = responses.stream().filter(response -> response.getStatusCode() == HttpStatus.CREATED).count();
        long replayed = responses.stream().filter(response -> response.getStatusCode() == HttpStatus.OK).count();
        Set<UUID> transactionIds = responses.stream()
                .map(ResponseEntity::getBody)
                .map(TransactionResponse::transactionId)
                .collect(java.util.stream.Collectors.toSet());

        UUID walletId = walletId(playerId);
        assertThat(created).isEqualTo(1);
        assertThat(replayed).isEqualTo(requestCount - 1);
        assertThat(transactionIds).hasSize(1);
        assertThat(walletRepository.findById(walletId).orElseThrow().getBalance()).isEqualTo(50);
        assertThat(walletTransactionRepository.countByWalletId(walletId)).isEqualTo(1);
    }

    @Test
    void concurrentCreditsAreNotLost() throws Exception {
        UUID playerId = UUID.randomUUID();
        createWallet(playerId);
        int requestCount = 6;
        long amount = 15;

        List<ResponseEntity<TransactionResponse>> responses = runConcurrently(requestCount, index ->
                exchange("/api/v1/wallets/" + playerId + "/credits", "credit-" + index, creditBody(amount)));

        long created = responses.stream().filter(response -> response.getStatusCode() == HttpStatus.CREATED).count();
        UUID walletId = walletId(playerId);
        long expected = amount * requestCount;

        assertThat(created).isEqualTo(requestCount);
        assertThat(walletRepository.findById(walletId).orElseThrow().getBalance()).isEqualTo(expected);
        assertThat(walletTransactionRepository.countByWalletId(walletId)).isEqualTo(requestCount);
        assertThat(walletTransactionRepository.sumSignedDeltas(walletId)).isEqualTo(expected);
    }

    private <T> List<T> runConcurrently(int threads, IndexedTask<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<T>> callables = IntStream.range(0, threads)
                    .mapToObj(index -> (Callable<T>) () -> {
                        ready.countDown();
                        if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("timed out waiting to start");
                        }
                        return task.run(index);
                    })
                    .toList();
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> callable : callables) {
                futures.add(pool.submit(callable));
            }
            if (!ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("workers did not become ready");
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("executor did not shut down");
            }
        }
    }

    private void createWallet(UUID playerId) {
        ResponseEntity<CreateWalletResponse> response = restTemplate.postForEntity(
                "/api/v1/wallets",
                Map.of("playerId", playerId.toString()),
                CreateWalletResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void credit(UUID playerId, String key, long amount, String reason) {
        ResponseEntity<TransactionResponse> response = exchange(
                "/api/v1/wallets/" + playerId + "/credits",
                key,
                """
                        {"amount": %d, "reason": "%s"}
                        """.formatted(amount, reason));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<TransactionResponse> exchange(String path, String idempotencyKey, String json) {
        return restTemplate.exchange(path, HttpMethod.POST, entity(idempotencyKey, json), TransactionResponse.class);
    }

    private ResponseEntity<String> exchangeRaw(String path, String idempotencyKey, String json) {
        return restTemplate.exchange(path, HttpMethod.POST, entity(idempotencyKey, json), String.class);
    }

    private static HttpEntity<String> entity(String idempotencyKey, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return new HttpEntity<>(json, headers);
    }

    private static String creditBody(long amount) {
        return """
                {"amount": %d, "reason": "Concurrent credit"}
                """.formatted(amount);
    }

    private static String debitBody(long amount) {
        return """
                {"amount": %d, "reason": "Concurrent debit"}
                """.formatted(amount);
    }

    private UUID walletId(UUID playerId) {
        return walletRepository.findByPlayerId(playerId).orElseThrow().getId();
    }

    @FunctionalInterface
    private interface IndexedTask<T> {
        T run(int index);
    }
}
