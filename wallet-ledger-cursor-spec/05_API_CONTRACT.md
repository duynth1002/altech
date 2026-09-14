# API Contract

Base path: `/api/v1`

Use JSON for request and response bodies. Use UTC ISO-8601 timestamps. Never expose JPA entities directly.

## Create wallet

`POST /api/v1/wallets`

Request:

```json
{
  "playerId": "9429d823-82f0-4f45-83bf-e55575a8fcec"
}
```

Response: `201 Created`

```json
{
  "walletId": "b1094b48-e066-4553-94a6-c9b07c673f86",
  "playerId": "9429d823-82f0-4f45-83bf-e55575a8fcec",
  "balance": 0,
  "createdAt": "2026-09-12T10:00:00Z"
}
```

## Credit wallet

`POST /api/v1/wallets/{playerId}/credits`

Required header:

```text
Idempotency-Key: reward-8f876f19
```

Request:

```json
{
  "amount": 250,
  "reason": "Completed mission 42",
  "referenceType": "MISSION",
  "referenceId": "mission-42"
}
```

First response: `201 Created`

```json
{
  "transactionId": "48544c88-8d75-47ae-bfc0-30c111e5bace",
  "playerId": "9429d823-82f0-4f45-83bf-e55575a8fcec",
  "type": "CREDIT",
  "amount": 250,
  "balanceAfter": 250,
  "reason": "Completed mission 42",
  "referenceType": "MISSION",
  "referenceId": "mission-42",
  "createdAt": "2026-09-12T10:01:00Z"
}
```

Exact replay response: `200 OK` with the original transaction body and header:

```text
Idempotency-Replayed: true
```

## Debit wallet

`POST /api/v1/wallets/{playerId}/debits`

The header and request fields match the credit endpoint. A successful first submission returns `201 Created`; an exact replay returns `200 OK` with the original transaction and `Idempotency-Replayed: true`.

Example request:

```json
{
  "amount": 75,
  "reason": "Purchased item sword-7",
  "referenceType": "PURCHASE",
  "referenceId": "purchase-2391"
}
```

## Get balance

`GET /api/v1/wallets/{playerId}/balance`

Response: `200 OK`

```json
{
  "playerId": "9429d823-82f0-4f45-83bf-e55575a8fcec",
  "balance": 175,
  "asOf": "2026-09-12T10:02:00Z"
}
```

`asOf` is the time the response was generated, not a ledger transaction time.

## Get transaction history

`GET /api/v1/wallets/{playerId}/transactions?page=0&size=20`

Response: `200 OK`

```json
{
  "items": [
    {
      "transactionId": "b1b3124b-6c74-49c5-8637-dad01784773f",
      "type": "DEBIT",
      "amount": 75,
      "balanceAfter": 175,
      "reason": "Purchased item sword-7",
      "referenceType": "PURCHASE",
      "referenceId": "purchase-2391",
      "createdAt": "2026-09-12T10:02:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

Sort newest first by `createdAt DESC, id DESC`. The server owns this sort; ignore or reject arbitrary sort parameters.

## HTTP behavior summary

| Scenario | Status | Error code |
| --- | ---: | --- |
| Wallet created | 201 | - |
| New credit or debit committed | 201 | - |
| Exact idempotent replay | 200 | - |
| Invalid input or missing idempotency key | 400 | `VALIDATION_ERROR` |
| Wallet missing | 404 | `WALLET_NOT_FOUND` |
| Insufficient funds | 409 | `INSUFFICIENT_FUNDS` |
| Idempotency key reused for another request | 409 | `IDEMPOTENCY_KEY_REUSED` |
| Wallet already exists | 409 | `WALLET_ALREADY_EXISTS` |
| Arithmetic overflow | 422 | `BALANCE_LIMIT_EXCEEDED` |
| Unexpected server error | 500 | `INTERNAL_ERROR` |

## OpenAPI

Document all endpoints, headers, validation constraints, success responses, and problem responses. Ensure the required `Idempotency-Key` header is visible in Swagger UI for credit and debit operations.

