# ADR 0004 — ORM versus hand-written SQL

## Status
Accepted.

## Context
Phase 4 wants a JDBC ledger and database-enforced constraints. Phase 5 wants the same invariants through JPA, with the domain kept free of annotations.

## Decision
Flyway owns the schema. Hibernate uses `ddl-auto=validate` and does not create tables. `PostingRules` is the only posting validator. `JdbcLedgerRepository` posts with one JDBC transaction and `SELECT … FOR UPDATE ORDER BY id`. `JpaBank` is the JPA version of that path: entities are mapped in the app module, and the lock is still a native query so the lock order cannot depend on the persistence context. The append-only trigger and `account_reconciliation` stay raw SQL.

## Consequences
Profile `local` still uses the in-memory `Ledger`, so the interview demo runs without Docker. Profile `postgres` uses `JpaBank` and JDBC stores for users, refresh tokens, the denylist, approvals, audit, idempotency, and the outbox. Those stores stay in memory on the local profile. H2 is not used. `open-in-view` is false.
