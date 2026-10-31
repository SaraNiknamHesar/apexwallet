# ADR 0002 — The ledger is the source of truth

## Status
Accepted.

## Context
A mutable balance column cannot explain why a number is what it is, and it races under concurrent transfers.

## Decision
Financial truth is an append-only double-entry journal. A customer balance is credits minus debits. `BANK_CASH` is debit-normal. Corrections are reversing transactions, not updates. `cached_balance_minor` is a projection and must match the fold in `account_reconciliation`. Postgres rejects `UPDATE` and `DELETE` on `ledger_entries` with trigger `ledger_entries_append_only`.

## Consequences
The local profile keeps the same rules in `Ledger` and `PostingRules`. The HTTP demo does not yet post through JDBC.
