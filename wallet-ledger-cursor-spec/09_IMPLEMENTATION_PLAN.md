# Implementation Plan

Implement in phases and keep the build green after each phase.

## Phase 1 - Bootstrap

- Create a Java 17 Spring Boot 3 Maven project.
- Add Web, Validation, Data JPA, PostgreSQL, Flyway, Actuator, OpenAPI, and test dependencies.
- Add Maven Wrapper.
- Configure local, test, and production-friendly settings without committing secrets.
- Add Docker Compose for PostgreSQL.

Exit condition: application starts and health endpoint reports healthy with PostgreSQL running.

## Phase 2 - Schema and persistence

- Add the initial Flyway migration.
- Implement wallet and transaction mappings.
- Add database constraints and indexes.
- Implement wallet lookup and `PESSIMISTIC_WRITE` lookup.
- Implement transaction history and idempotency lookup repositories.

Exit condition: repository integration tests pass against Testcontainers PostgreSQL.

## Phase 3 - Domain and application services

- Implement safe credit and debit arithmetic.
- Implement domain exceptions.
- Implement canonical request hashing.
- Implement the transactional mutation algorithm.
- Implement wallet creation and query services.

Exit condition: unit and service integration tests prove core operations and rollback.

## Phase 4 - REST API

- Add request and response DTOs.
- Add wallet, credit, debit, balance, and history endpoints.
- Add validation.
- Add `ProblemDetail` exception handling.
- Add deterministic pagination.

Exit condition: API tests cover success and error contracts.

## Phase 5 - Concurrency and idempotency proof

- Add concurrent debit test.
- Add concurrent duplicate-submission test.
- Add concurrent credits test.
- Inspect database state and ledger counts in every test.
- Fix all races at the persistence/transaction layer.

Exit condition: concurrency tests pass repeatedly against PostgreSQL.

## Phase 6 - Reviewer experience

- Add OpenAPI annotations where generated documentation is unclear.
- Complete the repository README using `10_PROJECT_README_TEMPLATE.md`.
- Include example curl commands.
- Ensure Docker Compose and test commands work from a clean checkout.
- Add AI tooling notes if AI tools were used.

Exit condition: another developer can run, understand, and evaluate the project using only the README.

## Phase 7 - Final verification

- Run formatting/static analysis if configured.
- Run all tests from the command documented in the README.
- Check no secrets or generated artifacts are committed.
- Review `11_DEFINITION_OF_DONE.md` line by line.
- Keep optional product features out unless every mandatory item is complete.

