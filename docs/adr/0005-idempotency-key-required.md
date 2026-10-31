# ADR 0005 — Missing idempotency key is rejected

## Status
Accepted.

## Context
A server-generated key cannot protect a client retry: the client would not know which key to send again.

## Decision
`POST /api/v1/transfers` requires `X-Idempotency-Key`. A missing key is HTTP 400 `IDEMPOTENCY_KEY_REQUIRED`. The stored hash is canonical `from|to|amount|currency|description` with SHA-256, not the raw JSON bytes. Same key and same hash replay the stored body with `Idempotency-Replayed: true`. A different payload is 409 `IDEMPOTENCY_CONFLICT`. An expired key may be used again. A `FAILED` key is not silently retried.

## Consequences
The uniqueness that should live on `(owner_ref, idempotency_key)` is in `V2__identity_idempotency_audit.sql`. The running local store is `MemoryIdempotencyStore`.
