# Validation and Error Handling

## Request validation

### Player ID

- Required in the relevant path or body.
- Must parse as a UUID.

### Idempotency key

- Required for credit and debit.
- Length: 1-100 characters after rejecting blank-only values.
- Treat the submitted nonblank value as case-sensitive.
- Do not log the entire key at info level; a shortened or hashed representation is sufficient.

### Amount

- Required.
- Integer.
- Greater than zero.
- Must fit in Java `long` and PostgreSQL `BIGINT`.

### Reason

- Required.
- Nonblank.
- Maximum 255 characters.

### References

- `referenceType` is optional, maximum 50 characters.
- `referenceId` is optional, maximum 100 characters.
- Either allow both independently or require both together; this package recommends requiring both together for a clearer audit trail.

### Pagination

- `page` defaults to `0` and cannot be negative.
- `size` defaults to `20`, minimum `1`, maximum `100`.

## Error format

Use Spring's `ProblemDetail` and an `application/problem+json` response compatible with RFC 9457.

Example:

```json
{
  "type": "https://example.com/problems/insufficient-funds",
  "title": "Insufficient funds",
  "status": 409,
  "detail": "The wallet balance is lower than the requested debit.",
  "instance": "/api/v1/wallets/9429d823-82f0-4f45-83bf-e55575a8fcec/debits",
  "code": "INSUFFICIENT_FUNDS",
  "timestamp": "2026-09-12T10:03:00Z",
  "traceId": "c4896d29c3484ff6"
}
```

Validation errors should additionally include field-level details:

```json
{
  "type": "https://example.com/problems/validation-error",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more request values are invalid.",
  "code": "VALIDATION_ERROR",
  "errors": [
    {
      "field": "amount",
      "message": "must be greater than 0"
    }
  ]
}
```

Do not expose stack traces, SQL, table names, Java exception names, or internal implementation details.

## Exception mapping

| Domain/application exception | Status | Code |
| --- | ---: | --- |
| Wallet not found | 404 | `WALLET_NOT_FOUND` |
| Wallet already exists | 409 | `WALLET_ALREADY_EXISTS` |
| Insufficient funds | 409 | `INSUFFICIENT_FUNDS` |
| Idempotency mismatch | 409 | `IDEMPOTENCY_KEY_REUSED` |
| Balance overflow | 422 | `BALANCE_LIMIT_EXCEEDED` |
| Bean validation failure | 400 | `VALIDATION_ERROR` |
| Malformed JSON or UUID | 400 | `MALFORMED_REQUEST` |

Use one `@RestControllerAdvice` for consistent translation.

## Logging

- Log successful mutations once after commit at an appropriate level.
- Include transaction ID, player ID, type, amount, and outcome.
- Log rejected operations with the domain error code.
- Use a request/trace ID.
- Never log database credentials or full exception responses containing secrets.

