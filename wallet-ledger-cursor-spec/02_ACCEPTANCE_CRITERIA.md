# Acceptance Criteria

These criteria convert the assignment into externally observable behavior.

## Wallet creation support

Wallet creation is a supporting capability so reviewers can exercise the required operations.

- Creating a wallet for a new `playerId` returns `201 Created`.
- Creating a second wallet for the same `playerId` returns `409 Conflict`.
- A new wallet starts with balance `0` and no ledger transactions.
- A syntactically invalid player identifier returns `400 Bad Request`.

## Credit

- A positive credit increases the balance by exactly the requested amount.
- A successful credit creates exactly one `CREDIT` ledger entry.
- The entry records the amount, resulting balance, reason, reference data, idempotency key, and timestamp.
- A credit of zero or a negative amount returns `400 Bad Request` and changes nothing.
- A credit to a missing wallet returns `404 Not Found` and changes nothing.
- A credit that would overflow the supported numeric range is rejected and changes nothing.

## Debit

- A positive debit decreases the balance by exactly the requested amount.
- A successful debit creates exactly one `DEBIT` ledger entry.
- A debit larger than the available balance returns `409 Conflict` with code `INSUFFICIENT_FUNDS`.
- A rejected debit changes neither the balance nor the ledger.
- A debit of zero or a negative amount returns `400 Bad Request`.
- A debit from a missing wallet returns `404 Not Found`.

## Balance

- The balance endpoint returns the currently committed balance.
- It returns `404 Not Found` for a missing wallet.
- The balance is never exposed as negative.

## Transaction history

- History is paginated and returns page metadata.
- Default page size is `20`.
- Maximum page size is `100`.
- Results use deterministic newest-first ordering by `createdAt`, then transaction `id`.
- Each item exposes transaction identity, type, amount, balance after the transaction, reason, references, and timestamp.
- History for a missing wallet returns `404 Not Found`.

## Idempotency

- Every credit and debit request requires an `Idempotency-Key` header.
- The first valid request applies the mutation once.
- Repeating the same key with the identical effective request returns the original transaction without another balance change or ledger entry.
- Reusing the key for a different operation, amount, reason, reference, or wallet returns `409 Conflict` with code `IDEMPOTENCY_KEY_REUSED`.
- Concurrent identical submissions still produce exactly one balance change and one ledger entry.
- Idempotency is enforced by both service logic and a database uniqueness constraint.

## Concurrency

- Concurrent credits are not lost.
- Concurrent debits cannot collectively spend more than the committed balance.
- Starting from balance `100`, two concurrent debits of `80` result in exactly one success, one insufficient-funds response, and final balance `20`.
- Requests against different wallets are not serialized by an application-wide lock.

## Atomicity

- Wallet update and ledger insertion commit in one database transaction.
- An exception after the balance update but before ledger persistence rolls back both effects.
- No API response reports success before the database transaction commits.

## Documentation and delivery

- The project starts using documented commands.
- Database migrations run automatically.
- All tests run using one documented command.
- The README explains the design, trade-offs, concurrency, idempotency, testing, assumptions, and limitations.
- The Git repository contains no secrets or generated build output.

