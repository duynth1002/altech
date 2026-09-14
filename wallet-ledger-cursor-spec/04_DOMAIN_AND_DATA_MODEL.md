# Domain and Data Model

## Domain assumptions

- `playerId` is a UUID supplied by another player system.
- Each player has exactly one wallet.
- The service supports one whole-unit in-game currency.
- A balance starts at zero.
- Ledger entries are never edited or deleted through the application.
- Timestamps are stored as UTC instants.

## Wallet

Fields:

| Field | Type | Rules |
| --- | --- | --- |
| `id` | UUID | Primary key |
| `playerId` | UUID | Required and unique |
| `balance` | long / BIGINT | Required and at least zero |
| `createdAt` | Instant / timestamptz | Required |
| `updatedAt` | Instant / timestamptz | Required |

Optional: include a JPA `@Version` column for diagnostic value, but do not rely on it instead of the prescribed row lock unless the concurrency design is deliberately changed and documented.

## Wallet transaction

Fields:

| Field | Type | Rules |
| --- | --- | --- |
| `id` | UUID | Primary key |
| `walletId` | UUID | Required foreign key |
| `type` | enum | `CREDIT` or `DEBIT` |
| `amount` | long / BIGINT | Required and greater than zero |
| `balanceAfter` | long / BIGINT | Required and at least zero |
| `reason` | string | Required, human-readable, max 255 |
| `referenceType` | string | Optional, max 50; examples: `MISSION`, `PURCHASE`, `ADMIN` |
| `referenceId` | string | Optional, max 100 |
| `idempotencyKey` | string | Required, max 100 |
| `requestHash` | string | Required SHA-256 hex digest |
| `createdAt` | Instant / timestamptz | Required |

The effective signed ledger delta is:

- `+amount` for `CREDIT`
- `-amount` for `DEBIT`

## Suggested PostgreSQL schema

```sql
CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL UNIQUE,
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    type VARCHAR(10) NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    amount BIGINT NOT NULL CHECK (amount > 0),
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    reason VARCHAR(255) NOT NULL,
    reference_type VARCHAR(50),
    reference_id VARCHAR(100),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_wallet_idempotency UNIQUE (wallet_id, idempotency_key)
);

CREATE INDEX idx_wallet_transactions_history
    ON wallet_transactions (wallet_id, created_at DESC, id DESC);
```

Add a migration for each schema change. Do not enable Hibernate schema generation outside isolated tests; production and local schemas should be managed by Flyway.

## Safe arithmetic

- Debit: verify `balance >= amount`, then subtract.
- Credit: use `Math.addExact(balance, amount)` or an equivalent explicit overflow check.
- Never accept negative request amounts as a way to reverse direction.
- Never use `float` or `double` for wallet amounts.

## Request hash

Build a canonical string from immutable effective request fields:

```text
operation|playerId|amount|reason|referenceType-or-empty|referenceId-or-empty
```

Hash its UTF-8 bytes with SHA-256. Normalize only in documented ways. Do not silently trim or case-fold semantic values after validation, because it makes idempotency comparisons surprising.

## Balance reconciliation

For a wallet whose opening balance is zero:

```text
stored balance = SUM(CREDIT amounts) - SUM(DEBIT amounts)
```

Provide at least an integration test for this invariant. A production-only admin reconciliation endpoint is optional.

