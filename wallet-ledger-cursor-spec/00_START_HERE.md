# Wallet Ledger Backend - Cursor Specification Pack

This folder translates the AI Engineer take-home assignment into an implementation-ready specification for Cursor.

## Objective

Build a production-minded wallet ledger backend using Java 17+, Spring Boot 3.x, and PostgreSQL. The implementation must prioritize correctness of money movement under retries, concurrency, and failures.

## Instruction priority

If documents appear to conflict, use this order:

1. `01_SOURCE_REQUIREMENTS.md` - requirements taken from the assignment.
2. `02_ACCEPTANCE_CRITERIA.md` - testable interpretation of those requirements.
3. The remaining files - recommended implementation decisions.

Do not weaken a source requirement to follow a recommendation. If a recommendation cannot be implemented cleanly, preserve the required behavior and document the deviation in the project README.

## Read in this order

1. `01_SOURCE_REQUIREMENTS.md`
2. `02_ACCEPTANCE_CRITERIA.md`
3. `03_RECOMMENDED_ARCHITECTURE.md`
4. `04_DOMAIN_AND_DATA_MODEL.md`
5. `05_API_CONTRACT.md`
6. `06_CONCURRENCY_AND_IDEMPOTENCY.md`
7. `07_VALIDATION_AND_ERRORS.md`
8. `08_TEST_PLAN.md`
9. `09_IMPLEMENTATION_PLAN.md`
10. `10_PROJECT_README_TEMPLATE.md`
11. `11_DEFINITION_OF_DONE.md`

After reading them, use `CURSOR_IMPLEMENTATION_PROMPT.md` as the execution prompt.

## Recommended scope

Implement the mandatory core completely before adding optional features.

Recommended additions:

- PostgreSQL and Flyway migrations
- Docker Compose
- OpenAPI / Swagger
- Testcontainers using PostgreSQL
- Spring Boot Actuator health endpoint

Do not initially implement Redis, WebSockets, transfers, refunds, reservations, bulk rewards, daily streaks, or promotional campaigns. They add risk without improving the score if the wallet core is incomplete.

## Core invariants

- A wallet balance is never negative.
- An amount is always a positive integer quantity.
- Every committed balance change has exactly one immutable ledger entry.
- A failed mutation changes neither the wallet balance nor its ledger.
- Replaying the same idempotency key with the same request does not move money twice.
- Reusing the same idempotency key for a different request is rejected.
- Concurrent mutations on one wallet produce a valid serial outcome.
- The stored balance equals the opening balance plus all committed ledger deltas.

