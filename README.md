# Wallet Ledger Backend Service

A production-minded Spring Boot wallet service that credits and debits player balances under retries, concurrency, and failures. Every committed money movement writes exactly one immutable ledger row in the same database transaction as the balance update.

## Features

- Credit and debit
- Insufficient-funds protection
- Current balance
- Paginated immutable ledger history
- Idempotent mutations
- Concurrency-safe wallet updates
- OpenAPI / Swagger UI
- Actuator health endpoint

## Technology

- Java 17 (build target; JDK 17+ can compile and run)
- Spring Boot 3.5.6
- Maven Wrapper
- PostgreSQL 16
- Flyway
- Spring Data JPA
- springdoc-openapi
- Spring Boot Actuator
- JUnit 5, AssertJ, Testcontainers PostgreSQL
- Docker Compose

## Prerequisites

- JDK 17 or newer
- Docker Desktop (or another Docker engine) for local PostgreSQL and for Testcontainers
- Ports `8080` (app) and `5432` (local Compose Postgres) available

## How to run

1. Start PostgreSQL:

```powershell
docker compose up -d
```

Wait until the container is healthy (`docker compose ps` should show `healthy`).

2. Start the application:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS or Linux:

```bash
./mvnw spring-boot:run
```

3. Open Swagger UI at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html). The health endpoint is [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health).

4. Stop local services:

```powershell
# Ctrl+C the application, then:
docker compose down
```

Default local credentials are Compose-only (`wallet` / `wallet`) and are overridden with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.

## How to run tests

From a clean checkout, with Docker running:

```powershell
.\mvnw.cmd test
```

On macOS or Linux:

```bash
./mvnw test
```

This single command runs domain tests, MockMvc API tests, and PostgreSQL Testcontainers integration tests, including concurrency and rollback cases. Testcontainers 1.21.4 is required for Docker Engine 29+, which rejects older Docker API clients.

## API examples

Replace the player ID if you create a different wallet.

1. Create a wallet:

```powershell
curl.exe -s -X POST http://localhost:8080/api/v1/wallets `
  -H "Content-Type: application/json" `
  -d "{\"playerId\":\"9429d823-82f0-4f45-83bf-e55575a8fcec\"}"
```

2. Credit it:

```powershell
curl.exe -s -X POST http://localhost:8080/api/v1/wallets/9429d823-82f0-4f45-83bf-e55575a8fcec/credits `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: reward-8f876f19" `
  -d "{\"amount\":250,\"reason\":\"Completed mission 42\",\"referenceType\":\"MISSION\",\"referenceId\":\"mission-42\"}"
```

3. Debit it:

```powershell
curl.exe -s -X POST http://localhost:8080/api/v1/wallets/9429d823-82f0-4f45-83bf-e55575a8fcec/debits `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: purchase-2391" `
  -d "{\"amount\":75,\"reason\":\"Purchased item sword-7\",\"referenceType\":\"PURCHASE\",\"referenceId\":\"purchase-2391\"}"
```

4. Read the balance:

```powershell
curl.exe -s http://localhost:8080/api/v1/wallets/9429d823-82f0-4f45-83bf-e55575a8fcec/balance
```

5. Read transaction history:

```powershell
curl.exe -s "http://localhost:8080/api/v1/wallets/9429d823-82f0-4f45-83bf-e55575a8fcec/transactions?page=0&size=20"
```

6. Replay the credit (returns `200` with `Idempotency-Replayed: true` and does not move money again):

```powershell
curl.exe -s -D - -X POST http://localhost:8080/api/v1/wallets/9429d823-82f0-4f45-83bf-e55575a8fcec/credits `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: reward-8f876f19" `
  -d "{\"amount\":250,\"reason\":\"Completed mission 42\",\"referenceType\":\"MISSION\",\"referenceId\":\"mission-42\"}"
```

## Design decisions

**Stored balance plus append-only ledger.** Balance reads are a single row lookup. The ledger is the audit trail and the source for reconciliation (`stored balance = SUM(CREDIT) - SUM(DEBIT)` from a zero opening balance). The cost is that both rows must change atomically; a failed ledger write rolls the balance back.

**Whole integer currency units.** Amounts are PostgreSQL `BIGINT` / Java `long`. There is no floating-point type on the money path. Credits use `Math.addExact` so overflow is rejected instead of wrapping.

**Transaction boundaries.** Credit and debit run in one `@Transactional` application-service method. The controller does not read-then-write a balance. Success responses are built only after the transaction returns.

**PostgreSQL.** Row-level `SELECT ... FOR UPDATE`, `CHECK` constraints, and a unique `(wallet_id, idempotency_key)` constraint give database-enforced correctness. H2 was allowed by the assignment but is not used because its locking behavior is not a substitute for the concurrency tests.

**Pessimistic row locking.** Writes to the same wallet serialize at the database. This is easier to reason about than optimistic retries for a correctness-focused take-home. The trade-off is higher latency on a hot wallet. Different wallets are not globally locked. Isolation is PostgreSQL `READ COMMITTED` plus the explicit row lock.

## Concurrency and idempotency

Mutation algorithm:

1. Validate path, `Idempotency-Key`, and body.
2. Hash the effective request: `operation|playerId|amount|reason|referenceType-or-empty|referenceId-or-empty` (SHA-256 hex).
3. Begin a database transaction.
4. Load the wallet with `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`).
5. Look up `(walletId, idempotencyKey)`.
6. Same hash: return the original row as a replay. Different hash: `409 IDEMPOTENCY_KEY_REUSED`.
7. Credit or debit with safe arithmetic (insufficient funds is decided after the lock).
8. Flush the wallet row, then insert one ledger row.
9. Commit, then log.

The wallet lock is taken before the idempotency lookup so two identical concurrent submissions queue: the first commits, the second observes the ledger row and replays. The unique constraint is defense in depth. Idempotency is per wallet; the same key may be used on different players. Keys are retained permanently because the ledger is permanent.

## Testing approach

- **Domain tests** cover credit, debit, insufficient funds, non-positive amounts, overflow, and request hashing.
- **Controller tests** cover request/response shape, missing idempotency header, invalid UUID, validation error format, exception-to-status mapping, and pagination bounds.
- **PostgreSQL Testcontainers tests** apply Flyway migrations and assert database state after API calls.
- **Guard tests** cover invalid amounts, credit overflow, debit replay, missing idempotency keys, incomplete references, default history pagination, and the append-only ledger trigger.
- **Concurrent debit:** start at `100`, two debits of `80`; exactly one success, one `INSUFFICIENT_FUNDS`, final balance `20`, one debit ledger row.
- **Concurrent duplicate submission:** many identical credits share one key; balance is `50`, one ledger row, one transaction id.
- **Concurrent credits:** distinct keys; no lost updates.
- **Rollback:** a spy on `LedgerAppender` throws after the wallet row is flushed; balance and ledger remain unchanged. There is no production failure flag.

Concurrency tests use `CountDownLatch` start barriers and bounded futures, not `Thread.sleep`.

## Database model

`wallets`: `id`, unique `player_id`, `balance >= 0`, UTC timestamps.

`wallet_transactions`: append-only ledger with `type` in (`CREDIT`, `DEBIT`), `amount > 0`, `balance_after >= 0`, reason, optional references, `idempotency_key`, `request_hash CHAR(64)`, and unique `(wallet_id, idempotency_key)`. History is indexed on `(wallet_id, created_at DESC, id DESC)`.

A trigger rejects `UPDATE` or `DELETE` on `wallet_transactions`. Flyway owns the schema; Hibernate `ddl-auto` is `validate`.

## Assumptions and limitations

- One wallet per player, identified by a UUID from another player system.
- One whole-unit in-game currency.
- `referenceType` and `referenceId` must both be present or both omitted.
- No authentication or authorization in this take-home.
- Idempotency records are retained indefinitely.
- Pessimistic locking serializes mutations to the same wallet.
- No distributed events and no external player verification.
- `@Version` is omitted so optimistic locking is not mistaken for the concurrency mechanism.
- Optional product features (transfers, refunds, reservations, streaks, campaigns) are not implemented.

## Future improvements

- Authentication and authorization
- Multi-currency support
- Outbox-backed domain events
- Ledger reconciliation job and alerting
- Metrics and tracing
- Archival/retention policy for very high volume
- Refunds, transfers, and reservations

## AI tooling notes

Implemented with Cursor (Grok 4.6). The generated code was reviewed against the source requirements, acceptance criteria, and concurrency algorithm, then verified by running `.\mvnw.cmd test` against Testcontainers PostgreSQL. Optional product features were left out on purpose.

