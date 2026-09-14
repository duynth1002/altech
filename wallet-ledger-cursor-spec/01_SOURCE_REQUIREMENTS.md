# Source Requirements

This document faithfully summarizes the supplied four-page take-home assignment. It separates mandatory requirements from optional ideas.

## Assignment metadata

- Project: Wallet Ledger Backend Service
- Role: AI Engineer
- Expected effort: 4-8 hours
- Suggested submission window: 3-5 days
- Submission: GitHub repository link, public or private
- Required stack: Java 17+, Spring Boot 3.x, relational database
- Preferred database: PostgreSQL
- Acceptable alternative database: H2

## Project goal

Build a production-minded Java and Spring Boot backend that safely handles wallet money movement and keeps a clear record of every change.

The exact architecture, package structure, API design, and optional features are deliberately open-ended.

## Mandatory wallet operations

The service must:

- Credit a player's wallet.
- Debit a player's wallet.
- Reject a debit when the wallet has insufficient balance.
- Return a player's current balance.
- Return a player's transaction history with pagination.

Every balance change must leave a clear, permanent record of what happened and why. Example causes include a mission reward, purchase, or administrator action.

## Mandatory safety and correctness

- The same request must not be applied more than once.
- Concurrent requests must not leave the wallet in an incorrect state.
- A failed operation must not leave partial updates.
- Invalid input, including negative amounts and missing players, must be handled clearly.

## Mandatory tests

Automated tests must provide confidence under normal use and under pressure. The assignment specifically highlights concurrent requests and repeated submission of the same request.

The important money paths must be protected by tests.

## Mandatory README content

The repository README must explain:

- How to set up and run the application.
- How to run the tests.
- The selected ledger design and its trade-offs.
- How concurrency is handled.
- How idempotency is handled.
- The testing approach, especially concurrent debit behavior.
- Assumptions, known limitations, and possible future improvements.

## Optional product features

Optional examples from the assignment:

- Daily login streak rewards
- Limited promotional rewards
- Player-to-player currency transfers
- Transaction refunds
- Reservation, finalization, and release of funds
- Bulk reward distribution
- Server-validated reward claims

## Optional supporting practices

- Domain events
- Docker Compose
- OpenAPI / Swagger
- Balance consistency checks
- Notes about AI coding tools used
- Redis caching for balance reads
- Structured logging and metrics

Optional work is considered only after the mandatory core is correct.

## Evaluation criteria

The reviewer will assess:

1. Money-moving correctness under normal and concurrent use.
2. Clean service design, readable code, and sensible layering.
3. Safe behavior under overlapping requests and failures.
4. Tests protecting important money paths.
5. Clear README documentation and design reasoning.
6. Optional additions only after the required behavior is solid.

## Submission requirements

- Push the complete project to a GitHub repository.
- If the repository is private, grant the reviewer access.
- Submit only the repository link.
- Make the README sufficient for the reviewer to understand and run the project without additional explanation.

