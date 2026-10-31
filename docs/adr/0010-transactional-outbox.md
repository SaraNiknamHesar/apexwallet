# ADR 0010 — Outbox instead of a second commit

## Status
Accepted.

## Decision
A payment and its "posted" event are written in one database transaction. A relay publishes afterwards and retries. Consumers dedupe by event id. After the attempt limit, the event goes to a dead-letter list and the rest of the batch continues. There is no two-phase commit and no synchronous call from the ledger to Kafka.

## Consequences
Readers of the topic are eventually consistent. Per-account order is the order of unpublished rows for that key. Order across accounts is not guaranteed. Kafka in compose is the broker for a later wiring; the tests prove the outbox semantics in process and the row rollback in Postgres.
