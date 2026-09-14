# Definition of Done

Mark an item complete only after verifying it.

## Required behavior

- [ ] A wallet can be created for a player.
- [ ] A wallet can be credited.
- [ ] A wallet can be debited.
- [ ] Insufficient funds are rejected.
- [ ] Current balance can be retrieved.
- [ ] Transaction history is paginated.
- [ ] Every committed mutation has one permanent ledger record including a reason.
- [ ] Invalid amounts and missing wallets have clear errors.

## Money safety

- [ ] Amounts use integer or other exact arithmetic, never floating point.
- [ ] Database constraints prevent negative balances and nonpositive transaction amounts.
- [ ] Credit overflow is handled.
- [ ] Wallet update and ledger insertion share one transaction.
- [ ] A forced ledger-write failure rolls back the balance update.
- [ ] Balance reconciliation passes.

## Idempotency

- [ ] Credit and debit require an idempotency key.
- [ ] Exact replay does not move money twice.
- [ ] Exact replay returns the original transaction.
- [ ] Key reuse with a different request is rejected.
- [ ] Database uniqueness backs the application logic.
- [ ] Concurrent duplicate requests create one ledger entry.

## Concurrency

- [ ] Wallet mutations lock the relevant database row.
- [ ] Insufficient-funds validation occurs after the lock is acquired.
- [ ] Concurrent credits have no lost updates.
- [ ] Two debits of 80 against 100 produce one success, one rejection, and balance 20.
- [ ] No in-memory or application-global lock is used as the correctness mechanism.

## API quality

- [ ] DTOs are separate from persistence entities.
- [ ] Bean validation covers all inputs.
- [ ] Error responses are consistent and do not expose internals.
- [ ] Pagination has safe defaults and a maximum size.
- [ ] History ordering is deterministic.
- [ ] OpenAPI documents endpoints and the idempotency header.

## Tests

- [ ] Domain tests pass.
- [ ] Controller tests pass.
- [ ] PostgreSQL Testcontainers integration tests pass.
- [ ] Concurrency tests use barriers and timeouts rather than sleeps.
- [ ] Tests assert final balance and ledger state.
- [ ] Tests pass from a clean checkout using the documented command.

## Operations and documentation

- [ ] Flyway owns database migrations.
- [ ] Docker Compose starts required local infrastructure.
- [ ] Health endpoint works.
- [ ] No secrets are committed.
- [ ] README includes run and test instructions.
- [ ] README explains design decisions and trade-offs.
- [ ] README explains concurrency and idempotency.
- [ ] README explains the testing approach.
- [ ] README lists assumptions and limitations.
- [ ] AI tooling use is disclosed if applicable.

## Scope control

- [ ] All mandatory items are complete before optional product features.
- [ ] The implementation is readable and reviewable within take-home scope.
- [ ] Any deviation from this pack is documented with a reason.

