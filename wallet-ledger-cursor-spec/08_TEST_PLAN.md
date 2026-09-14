# Test Plan

The test suite must prove invariants, not only controller status codes.

## Test layers

### Domain/unit tests

Keep these fast and focused:

- Credit increases balance correctly.
- Debit decreases balance correctly.
- Debit rejects insufficient funds.
- Zero and negative amounts are rejected.
- Credit overflow is rejected.
- Canonical request hashing is deterministic.
- Different effective requests produce different hashes.

### Controller tests

Use MockMvc or an equivalent Spring test slice:

- Valid request and response shape.
- Missing idempotency header.
- Invalid UUID.
- Validation error format.
- Correct mapping of each domain exception to HTTP status and error code.
- Pagination bounds.

### PostgreSQL integration tests

Use Testcontainers rather than H2 for locking and concurrency tests. Apply real Flyway migrations.

Required integration cases:

1. Create wallet with zero balance.
2. Reject duplicate wallet creation.
3. Credit creates one ledger entry and updates balance.
4. Debit creates one ledger entry and updates balance.
5. Insufficient debit changes neither wallet nor ledger.
6. Missing wallet behavior.
7. Exact sequential idempotent replay returns the original transaction.
8. Reused key with a changed amount is rejected.
9. Reused key across credit and debit is rejected.
10. Same key is allowed for different wallets.
11. Transaction history is newest-first and paginated.
12. Stored balance equals ledger sum.

## Required concurrency tests

Use an `ExecutorService`, a start barrier such as `CountDownLatch`, separate request threads, and bounded timeouts. Avoid `Thread.sleep` as the synchronization mechanism.

### Concurrent debit protection

Setup:

- Create wallet.
- Credit `100`.
- Start two concurrent debits of `80` using different idempotency keys.

Assertions:

- Exactly one debit succeeds.
- Exactly one debit fails with insufficient funds.
- Final balance is `20`.
- Exactly one debit ledger entry exists.
- Reconciliation succeeds.

### Concurrent duplicate submission

Setup:

- Create wallet.
- Start multiple identical credits of `50` using the same idempotency key.

Assertions:

- Calls resolve as one creation plus replays.
- Final balance is `50`, not `50 * requestCount`.
- Exactly one matching ledger entry exists.
- All successful responses identify the same transaction.

### Concurrent credits

Setup:

- Create wallet.
- Run several credits with distinct idempotency keys.

Assertions:

- Final balance equals the sum of all committed credits.
- No updates are lost.
- Ledger count matches successful requests.

## Atomic rollback test

Use a test override or mockable ledger-writing collaborator that throws a runtime exception after the wallet entity has been changed but before the ledger write completes.

Assert after the transaction fails:

- The database wallet balance is unchanged.
- No ledger entry was inserted.

Do not add a production endpoint or production failure flag solely to support this test.

## Test quality rules

- Assert database state after requests.
- Give concurrency tests timeouts so failures do not hang the build.
- Do not depend on test execution order.
- Use unique players and idempotency keys per test.
- Keep randomized tests reproducible with a fixed seed when randomness is used.
- Make the test command work from a clean checkout.

