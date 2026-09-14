# Cursor Implementation Prompt

Copy the prompt below into Cursor after placing this specification folder at the root of an empty or newly initialized repository.

```text
You are implementing a production-minded Java/Spring Boot take-home assignment: a Wallet Ledger Backend Service.

First, read every Markdown file in `wallet-ledger-cursor-spec/`, beginning with `00_START_HERE.md`. Do not write code until you have read all files.

Instruction priority:
1. Source requirements in `01_SOURCE_REQUIREMENTS.md`.
2. Testable behavior in `02_ACCEPTANCE_CRITERIA.md`.
3. Recommended implementation decisions in the remaining documents.

Then:

1. Summarize your implementation plan and any detected contradiction in no more than 15 bullets.
2. Implement the project phase by phase using `09_IMPLEMENTATION_PLAN.md`.
3. Use Java 17, Spring Boot 3.x, Maven Wrapper, PostgreSQL, Flyway, Docker Compose, and Testcontainers PostgreSQL.
4. Treat `06_CONCURRENCY_AND_IDEMPOTENCY.md` as a critical correctness specification.
5. Do not use floating-point amounts, in-memory locks, or controller-level read-then-write logic for wallet correctness.
6. Add tests before considering optional features. The concurrent debit, concurrent duplicate submission, and rollback tests are required.
7. Run the full test suite and fix failures. Do not claim success based only on compilation.
8. Build the repository README from `10_PROJECT_README_TEMPLATE.md`, using actual commands and implemented behavior.
9. Review every item in `11_DEFINITION_OF_DONE.md` and report any unchecked item honestly.
10. Do not implement optional product features until all mandatory criteria pass.

When a minor detail is unspecified, choose the simplest production-defensible option and document it. Ask for input only when a choice would materially change the public API, data integrity model, or required behavior.

At completion, provide:
- A concise architecture summary.
- Exact run and test commands.
- Full test results.
- Any assumptions or remaining limitations.
- A list of files changed.
```

## Expected Cursor behavior

Cursor should not merely scaffold endpoints. It should finish the core, run tests against PostgreSQL, and use the definition-of-done checklist as its final audit.

