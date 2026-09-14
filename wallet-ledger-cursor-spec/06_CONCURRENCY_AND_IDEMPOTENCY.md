# Concurrency and Idempotency

This is the most important implementation document. Correctness must be enforced in the database transaction, not by controller checks or an in-memory Java lock.

## Mutation transaction algorithm

For both credit and debit:

1. Validate the path, header, and request body.
2. Compute the canonical request hash.
3. Begin a database transaction.
4. Load the wallet by `playerId` using `SELECT ... FOR UPDATE` through a repository method using `PESSIMISTIC_WRITE`.
5. If no wallet exists, throw `WALLET_NOT_FOUND`.
6. Look up an existing ledger entry by `(walletId, idempotencyKey)`.
7. If an entry exists and `requestHash` matches, return it as a replay without changing the wallet.
8. If an entry exists and the hash differs, throw `IDEMPOTENCY_KEY_REUSED`.
9. Calculate the new balance using safe arithmetic.
10. For debit, reject when available balance is less than the amount.
11. Update the locked wallet balance.
12. Insert exactly one ledger entry containing `balanceAfter` and idempotency data.
13. Commit.
14. Build the success response only from committed results.

Use `@Transactional` on the public application-service method. Runtime exceptions must roll the transaction back.

## Why the wallet is locked before the idempotency lookup

Locking the wallet serializes all mutations for that wallet, including two concurrent requests using the same idempotency key. The second transaction waits, observes the first committed ledger entry, and becomes a replay.

The database unique constraint on `(wallet_id, idempotency_key)` remains mandatory defense in depth.

## Row-locking repository method

Equivalent Spring Data behavior:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select w from Wallet w where w.playerId = :playerId")
Optional<Wallet> findByPlayerIdForUpdate(UUID playerId);
```

Verify the generated PostgreSQL query locks the selected wallet row.

## Insufficient-funds correctness

Balance validation must occur after the wallet lock is acquired. Checking before acquiring the lock creates a time-of-check/time-of-use race where two debits can both observe the same old balance.

Example:

- Starting balance: `100`
- Debit A: `80`
- Debit B: `80`
- One request obtains the lock and commits balance `20`.
- The other obtains the lock afterward, sees `20`, and fails.
- Final balance: `20`.

## Idempotency semantics

The idempotency namespace is per wallet.

- Same wallet + same key + same hash: replay the original result.
- Same wallet + same key + different hash: conflict.
- Different wallets + same key: allowed.

The request hash includes the operation type so a credit and debit cannot share a key accidentally.

Keep idempotency records permanently for this assignment because the ledger itself is permanent. In a high-volume production system, retention and archival would require an explicit policy.

## Lock scope

- Do not use `synchronized`, a static lock map, or a global database lock.
- Do not call slow external services while holding the database lock.
- Keep the transaction short.
- Lock only the affected wallet.
- Reads do not require the mutation lock.

## Isolation

PostgreSQL `READ COMMITTED` plus explicit row locking is sufficient for the described single-wallet mutations. Document this choice.

If player transfers are later added, both wallets must be locked in a deterministic order, such as ascending wallet UUID, to reduce deadlock risk.

## Failure behavior

- Validation errors occur before mutation.
- Domain errors throw before a ledger insert and roll back the transaction.
- Persistence errors throw runtime exceptions and roll back wallet and ledger changes.
- Do not catch a database exception and then return success.
- Translate known exceptions to API problems outside the transactional service.

