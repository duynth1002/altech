# Project README Template

Use the following structure for the actual repository `README.md`. Replace every placeholder with real project-specific information.

```markdown
# Wallet Ledger Backend Service

One-paragraph summary of the service and its correctness goals.

## Features

- Credit and debit
- Insufficient-funds protection
- Current balance
- Paginated immutable ledger history
- Idempotent mutations
- Concurrency-safe wallet updates

## Technology

List Java, Spring Boot, PostgreSQL, Flyway, Testcontainers, Docker Compose, and other included tools.

## Prerequisites

List required JDK, Docker, and any platform notes.

## How to run

Provide exact commands for:

1. Starting PostgreSQL.
2. Starting the application.
3. Opening Swagger UI.
4. Stopping local services.

## How to run tests

Provide one exact clean command. Explain that PostgreSQL Testcontainers requires Docker.

## API examples

Show copy-paste curl examples for:

1. Creating a wallet.
2. Crediting it with an idempotency key.
3. Debiting it.
4. Reading its balance.
5. Reading transaction history.
6. Replaying a request.

## Design decisions

Explain:

- Stored balance plus append-only ledger.
- Whole integer currency units.
- Transaction boundaries.
- Why PostgreSQL was selected.
- Why pessimistic row locking was selected.

## Concurrency and idempotency

Describe the exact mutation algorithm, row lock, unique constraint, request hash, replay behavior, and key-reuse conflict.

## Testing approach

Describe unit, API, PostgreSQL integration, rollback, concurrent debit, concurrent credit, and repeated-submission tests.

## Database model

Summarize `wallets` and `wallet_transactions`, their constraints, and important indexes.

## Assumptions and limitations

State at least:

- One wallet per player.
- One whole-unit currency.
- No authentication/authorization in take-home scope.
- Idempotency records are retained indefinitely.
- Pessimistic locking serializes mutations to the same wallet.
- No distributed events or external player verification.

## Future improvements

Potential items:

- Authentication and authorization.
- Multi-currency support.
- Outbox-backed domain events.
- Ledger reconciliation job and alerting.
- Metrics and tracing.
- Archival/retention policy.
- Refunds, transfers, and reservations.

## AI tooling notes

Briefly and honestly state which AI tools were used and how outputs were reviewed and tested.
```

## README quality bar

- Commands must be executable, not placeholders.
- Do not claim features that are not implemented.
- Explain why choices were made, not only what libraries were used.
- Mention meaningful trade-offs and limitations.
- Keep the document concise enough for a reviewer to scan.

