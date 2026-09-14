# Recommended Architecture

## Technology choices

- Java 17
- Spring Boot 3.x
- Maven Wrapper
- Spring Web
- Spring Validation
- Spring Data JPA
- PostgreSQL
- Flyway
- springdoc-openapi
- Spring Boot Actuator
- JUnit 5, AssertJ, and Testcontainers PostgreSQL
- Docker Compose for local execution

Use versions compatible with the selected Spring Boot release. Do not choose preview dependencies.

## Architectural style

Use a feature-first package with clear API, application, domain, and persistence responsibilities. Keep the design understandable for a take-home; avoid creating a large framework of abstractions.

Suggested package tree:

```text
src/main/java/com/example/walletledger/
├── WalletLedgerApplication.java
├── shared/
│   ├── error/
│   └── time/
└── wallet/
    ├── api/
    │   ├── WalletController.java
    │   ├── dto/
    │   └── mapper/
    ├── application/
    │   ├── WalletCommandService.java
    │   └── WalletQueryService.java
    ├── domain/
    │   ├── Wallet.java
    │   ├── WalletTransaction.java
    │   ├── TransactionType.java
    │   └── exception/
    └── infrastructure/
        └── persistence/
            ├── WalletRepository.java
            └── WalletTransactionRepository.java
```

## Responsibility boundaries

### API layer

- Parse requests and headers.
- Apply bean validation.
- Call application services.
- Map application results to response DTOs.
- Do not contain balance arithmetic or transaction management.

### Application layer

- Own use-case orchestration.
- Define transaction boundaries.
- Lock wallets for mutations.
- Enforce idempotency behavior.
- Validate sufficient funds and overflow.
- Update the wallet and append the ledger entry atomically.

### Domain layer

- Express wallet invariants and safe arithmetic.
- Use explicit transaction types.
- Throw meaningful domain exceptions.
- Avoid dependencies on HTTP types.

### Persistence layer

- Map wallet and transaction entities.
- Provide row-locking lookup for mutations.
- Provide idempotency and paginated history queries.
- Rely on database constraints as final protection.

## Scope decisions

- Model one wallet per player.
- Model a single in-game currency measured in whole integer units.
- Use `BIGINT` in PostgreSQL and `long` in Java.
- Require positive request amounts; transaction direction is represented by the transaction type.
- Keep the ledger append-only through application behavior and database permissions/documentation where practical.
- Store the current balance for efficient reads while retaining the full ledger as the audit trail.

## Trade-offs to document

### Stored balance plus ledger

Pros:

- Fast balance reads.
- Simple conditional debit logic.
- Clear audit history.

Cons:

- Two representations must remain consistent.
- All writes must be atomic.
- A reconciliation check is valuable in production.

### Pessimistic row locking

Pros:

- Easy to reason about.
- Prevents lost updates and overspending.
- Fits a correctness-focused take-home.

Cons:

- Serializes writes to the same wallet.
- Hot wallets can have higher latency.
- Requires short transactions and careful lock ordering if transfers are added.

### Integer currency units

Pros:

- Exact arithmetic.
- No floating-point rounding.
- Natural for whole-unit game currency.

Cons:

- The model must be extended if fractional currency is required.

## Optional additions to include

- OpenAPI UI for convenient review.
- Health endpoint.
- Structured log containing transaction ID, player ID, operation, and outcome, but no sensitive request data.
- A reconciliation query or test proving stored balances match ledger sums.

