package com.example.walletledger.wallet.api;

import com.example.walletledger.wallet.api.dto.BalanceResponse;
import com.example.walletledger.wallet.api.dto.CreateWalletRequest;
import com.example.walletledger.wallet.api.dto.CreateWalletResponse;
import com.example.walletledger.wallet.api.dto.MoneyMutationRequest;
import com.example.walletledger.wallet.api.dto.TransactionHistoryResponse;
import com.example.walletledger.wallet.api.dto.TransactionResponse;
import com.example.walletledger.wallet.api.mapper.WalletApiMapper;
import com.example.walletledger.wallet.application.MutationResult;
import com.example.walletledger.wallet.application.WalletCommandService;
import com.example.walletledger.wallet.application.WalletQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallets", description = "Wallet creation, balance, ledger history, credit, and debit")
public class WalletController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final WalletCommandService walletCommandService;
    private final WalletQueryService walletQueryService;
    private final WalletApiMapper mapper;

    public WalletController(
            WalletCommandService walletCommandService,
            WalletQueryService walletQueryService,
            WalletApiMapper mapper) {
        this.walletCommandService = walletCommandService;
        this.walletQueryService = walletQueryService;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Create a wallet for a player")
    @ApiResponse(responseCode = "201", description = "Wallet created")
    @ApiResponse(responseCode = "400", description = "Invalid player identifier", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Wallet already exists", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<CreateWalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toCreateResponse(walletCommandService.createWallet(request.playerId())));
    }

    @PostMapping("/{playerId}/credits")
    @Operation(summary = "Credit a wallet")
    @ApiResponse(responseCode = "201", description = "Credit committed")
    @ApiResponse(
            responseCode = "200",
            description = "Exact idempotent replay",
            headers = @Header(name = IDEMPOTENCY_REPLAYED, description = "true when the original transaction is replayed"))
    public ResponseEntity<TransactionResponse> credit(
            @PathVariable UUID playerId,
            @Parameter(name = IDEMPOTENCY_KEY, in = ParameterIn.HEADER, required = true, description = "Caller-supplied idempotency key, 1-100 characters")
            @RequestHeader(IDEMPOTENCY_KEY)
            @NotBlank
            @Size(min = 1, max = 100)
            String idempotencyKey,
            @Valid @RequestBody MoneyMutationRequest request) {
        return toMutationResponse(playerId, walletCommandService.credit(playerId, idempotencyKey, mapper.toCommand(request)));
    }

    @PostMapping("/{playerId}/debits")
    @Operation(summary = "Debit a wallet")
    @ApiResponse(responseCode = "201", description = "Debit committed")
    @ApiResponse(
            responseCode = "200",
            description = "Exact idempotent replay",
            headers = @Header(name = IDEMPOTENCY_REPLAYED, description = "true when the original transaction is replayed"))
    public ResponseEntity<TransactionResponse> debit(
            @PathVariable UUID playerId,
            @Parameter(name = IDEMPOTENCY_KEY, in = ParameterIn.HEADER, required = true, description = "Caller-supplied idempotency key, 1-100 characters")
            @RequestHeader(IDEMPOTENCY_KEY)
            @NotBlank
            @Size(min = 1, max = 100)
            String idempotencyKey,
            @Valid @RequestBody MoneyMutationRequest request) {
        return toMutationResponse(playerId, walletCommandService.debit(playerId, idempotencyKey, mapper.toCommand(request)));
    }

    @GetMapping("/{playerId}/balance")
    @Operation(summary = "Get the committed wallet balance")
    public BalanceResponse getBalance(@PathVariable UUID playerId) {
        return mapper.toBalanceResponse(walletQueryService.getBalance(playerId));
    }

    @GetMapping("/{playerId}/transactions")
    @Operation(summary = "Get paginated ledger history, newest first")
    public TransactionHistoryResponse getHistory(
            @PathVariable UUID playerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return mapper.toHistoryResponse(walletQueryService.getHistory(playerId, page, size));
    }

    private ResponseEntity<TransactionResponse> toMutationResponse(UUID playerId, MutationResult result) {
        HttpHeaders headers = new HttpHeaders();
        if (result.replayed()) {
            headers.add(IDEMPOTENCY_REPLAYED, "true");
        }
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return new ResponseEntity<>(mapper.toTransactionResponse(result.transaction(), playerId), headers, status);
    }
}
