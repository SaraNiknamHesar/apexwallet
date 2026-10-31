# ApexWallet — Progress

## Current position
- Curriculum release: v3.0
- Reference code: phases 0–17 implemented on 2026-10-23 by the assistant, at the learner's explicit request, as a study sample
- Learner mastery: NOT ASSESSED. No phase is learner-complete
- Interview study guide: `docs/study/PHASES_00_08_FA.md`
- Last verification: `PostgresLedgerIT` 8 tests green on PostgreSQL 16 (JDBC, JPA, kill-the-JVM, 500 transfers, deadlock, index, pool, N+1, optimistic lock)

## What the reference actually contains
- Domain: minor-unit money, double-entry ledger, ordered locks, property tests
- App profile `local`: REST, validation, ProblemDetail, OpenAPI, idempotency, RS256 JWT, RBAC, IDOR as 404, maker-checker, AES-GCM, rate limit, refresh reuse, audit aspect
- Flyway `V1`–`V3`, append-only triggers, reconciliation view, unique idempotency index
- `JdbcLedgerRepository` and `JpaBank`, proved by `PostgresLedgerIT`
- Phases 9–17 study code: reconciliation, fees, ISO subset, outbox relay, ArchUnit, SLO notes, offline AI, Dockerfile, order book
- ADRs 0001–0012

## What it does not contain
- The `local` HTTP demo does not write the ledger to Postgres. Restarting that demo rebuilds memory data
- Users, refresh tokens, approvals, and the HTTP idempotency store are still memory-only
- A running Kafka consumer, a deployed Grafana dashboard, a PIT mutation score, and a recorded demo video

## Next session
Study with the learner from phase 1–2 concepts (why not `double`, the five invariants). Do not resume as if phase 0 step 0.1 still has no code, and do not mark a phase complete because the reference tests passed.

## Learner context
- Absolute Java beginner relative to this curriculum. About two hours a night.
- Persian explanations are welcome. Code and tool names stay in English.
- The learner will later redo the project step by step. Until then, interview answers must stay honest about authorship and about the gaps above.

## Hours log
No study hours reported for this learning pass.
