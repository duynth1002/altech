package com.example.walletledger.wallet.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.walletledger.shared.error.ApiExceptionHandler;
import com.example.walletledger.shared.time.TimeConfig;
import com.example.walletledger.wallet.api.mapper.WalletApiMapper;
import com.example.walletledger.wallet.application.MutationResult;
import com.example.walletledger.wallet.application.WalletBalanceView;
import com.example.walletledger.wallet.application.WalletCommandService;
import com.example.walletledger.wallet.application.WalletQueryService;
import com.example.walletledger.wallet.domain.TransactionType;
import com.example.walletledger.wallet.domain.Wallet;
import com.example.walletledger.wallet.domain.WalletTransaction;
import com.example.walletledger.wallet.domain.exception.BalanceLimitExceededException;
import com.example.walletledger.wallet.domain.exception.IdempotencyKeyReusedException;
import com.example.walletledger.wallet.domain.exception.InsufficientFundsException;
import com.example.walletledger.wallet.domain.exception.WalletAlreadyExistsException;
import com.example.walletledger.wallet.domain.exception.WalletNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WalletController.class)
@Import({WalletApiMapper.class, ApiExceptionHandler.class, TimeConfig.class})
class WalletControllerTest {

    private static final UUID PLAYER = UUID.fromString("9429d823-82f0-4f45-83bf-e55575a8fcec");
    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletCommandService walletCommandService;

    @MockitoBean
    private WalletQueryService walletQueryService;

    @Test
    void createWalletReturnsCreatedShape() throws Exception {
        Wallet wallet = Wallet.open(PLAYER, NOW);
        when(walletCommandService.createWallet(PLAYER)).thenReturn(wallet);

        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId":"9429d823-82f0-4f45-83bf-e55575a8fcec"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.walletId").value(wallet.getId().toString()))
                .andExpect(jsonPath("$.playerId").value(PLAYER.toString()))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.createdAt").value("2026-09-12T10:00:00Z"));
    }

    @Test
    void creditReturnsCreatedTransactionShape() throws Exception {
        WalletTransaction tx = WalletTransaction.append(
                UUID.randomUUID(),
                TransactionType.CREDIT,
                250,
                250,
                "Completed mission 42",
                "MISSION",
                "mission-42",
                "reward-1",
                "a".repeat(64),
                NOW);
        when(walletCommandService.credit(eq(PLAYER), eq("reward-1"), any()))
                .thenReturn(new MutationResult(tx, false));

        mockMvc.perform(post("/api/v1/wallets/{playerId}/credits", PLAYER)
                        .header("Idempotency-Key", "reward-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 250,
                                  "reason": "Completed mission 42",
                                  "referenceType": "MISSION",
                                  "referenceId": "mission-42"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value(tx.getId().toString()))
                .andExpect(jsonPath("$.playerId").value(PLAYER.toString()))
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.amount").value(250))
                .andExpect(jsonPath("$.balanceAfter").value(250));
    }

    @Test
    void replayAddsIdempotencyHeader() throws Exception {
        WalletTransaction tx = WalletTransaction.append(
                UUID.randomUUID(),
                TransactionType.CREDIT,
                250,
                250,
                "Completed mission 42",
                "MISSION",
                "mission-42",
                "reward-1",
                "a".repeat(64),
                NOW);
        when(walletCommandService.credit(eq(PLAYER), eq("reward-1"), any()))
                .thenReturn(new MutationResult(tx, true));

        mockMvc.perform(post("/api/v1/wallets/{playerId}/credits", PLAYER)
                        .header("Idempotency-Key", "reward-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 250,
                                  "reason": "Completed mission 42",
                                  "referenceType": "MISSION",
                                  "referenceId": "mission-42"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.transactionId").value(tx.getId().toString()));
    }

    @Test
    void missingIdempotencyKeyIsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/wallets/{playerId}/credits", PLAYER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10, "reason": "Grant"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void invalidUuidIsMalformedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/not-a-uuid/balance"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void zeroAmountIsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/wallets/{playerId}/debits", PLAYER)
                        .header("Idempotency-Key", "debit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 0, "reason": "Bad debit"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    void mapsWalletNotFound() throws Exception {
        when(walletQueryService.getBalance(PLAYER)).thenThrow(new WalletNotFoundException(PLAYER));

        mockMvc.perform(get("/api/v1/wallets/{playerId}/balance", PLAYER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    void mapsWalletAlreadyExists() throws Exception {
        when(walletCommandService.createWallet(PLAYER)).thenThrow(new WalletAlreadyExistsException(PLAYER));

        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId":"9429d823-82f0-4f45-83bf-e55575a8fcec"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_ALREADY_EXISTS"));
    }

    @Test
    void mapsInsufficientFunds() throws Exception {
        when(walletCommandService.debit(eq(PLAYER), eq("debit-1"), any()))
                .thenThrow(new InsufficientFundsException());

        mockMvc.perform(post("/api/v1/wallets/{playerId}/debits", PLAYER)
                        .header("Idempotency-Key", "debit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 80, "reason": "Purchase"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void mapsIdempotencyKeyReused() throws Exception {
        when(walletCommandService.credit(eq(PLAYER), eq("reward-1"), any()))
                .thenThrow(new IdempotencyKeyReusedException());

        mockMvc.perform(post("/api/v1/wallets/{playerId}/credits", PLAYER)
                        .header("Idempotency-Key", "reward-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10, "reason": "Other"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void mapsBalanceLimitExceeded() throws Exception {
        when(walletCommandService.credit(eq(PLAYER), eq("reward-1"), any()))
                .thenThrow(new BalanceLimitExceededException());

        mockMvc.perform(post("/api/v1/wallets/{playerId}/credits", PLAYER)
                        .header("Idempotency-Key", "reward-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1, "reason": "Overflow"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BALANCE_LIMIT_EXCEEDED"));
    }

    @Test
    void paginationBoundsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/{playerId}/transactions", PLAYER)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/wallets/{playerId}/transactions", PLAYER)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void historyUsesQueryServicePage() throws Exception {
        WalletTransaction tx = WalletTransaction.append(
                UUID.randomUUID(),
                TransactionType.DEBIT,
                75,
                175,
                "Purchased item",
                "PURCHASE",
                "purchase-1",
                "debit-1",
                "b".repeat(64),
                NOW);
        when(walletQueryService.getHistory(PLAYER, 0, 20))
                .thenReturn(new PageImpl<>(List.of(tx), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/wallets/{playerId}/transactions", PLAYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].transactionId").value(tx.getId().toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void balanceUsesQueryView() throws Exception {
        when(walletQueryService.getBalance(PLAYER)).thenReturn(new WalletBalanceView(PLAYER, 175, NOW));

        mockMvc.perform(get("/api/v1/wallets/{playerId}/balance", PLAYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(PLAYER.toString()))
                .andExpect(jsonPath("$.balance").value(175))
                .andExpect(jsonPath("$.asOf").value("2026-09-12T10:00:00Z"));
    }
}
