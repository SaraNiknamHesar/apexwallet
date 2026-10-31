# ADR 0003 — Module boundaries

## Status
Accepted.

## Context
A bank interview should be able to see that money rules do not depend on the web framework.

## Decision
`apexwallet-domain` is pure Java: money, accounts, ledger, concurrency. It does not depend on Spring or JPA. `apexwallet-app` is the Spring Boot edge: HTTP, security, idempotency, and the in-memory adapters. `apexwallet-iso20022` holds XML hardening. `apexwallet-ai` is a named placeholder until a later phase.

## Consequences
Domain tests run without a database or a Spring context. Changing the API must not change `Money` or `PostingRules`.
