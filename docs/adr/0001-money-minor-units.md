# ADR 0001 — Store money as minor-unit integers

## Status
Accepted for the phase 0–8 reference build.

## Context
Bank balances must add, compare and allocate without rounding drift. `double` and `float` cannot represent 0.10 exactly.

## Decision
`Money` stores a `long` minor-unit amount plus an ISO 4217 currency. Scale is 2 for EUR/USD/GBP/CHF, 0 for JPY/KRW, and 3 for KWD/BHD/OMR/TND. Multiplication uses `HALF_EVEN`. Addition uses `Math.addExact`. Negative amounts are rejected on `Money`. There is no shared `Money.ZERO`.

## Consequences
Database columns are `BIGINT`. Display formatting happens at the edge. Cross-currency arithmetic is refused inside one posting.
