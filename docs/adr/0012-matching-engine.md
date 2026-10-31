# ADR 0012 — A single-threaded matching book

## Status
Accepted.

## Decision
The study matching engine is one thread per book, price-time priority, partial fills, and deterministic arrival order. A market order that cannot fill is cancelled. A limit order rests. The book must not stay crossed.

## Consequences
Throughput is limited by one thread. That is the point: replay of the same orders produces the same trades. A trade's notional is the debit and the credit of a ledger posting. This is not a production trading system.
