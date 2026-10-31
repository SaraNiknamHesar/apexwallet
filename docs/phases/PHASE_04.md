# Phase 04 — Independent Chat Packet

**Topic:** PostgreSQL, SQL, transactions and locking

**State:** Read `PROGRESS.md` and `docs/handoffs/CURRENT_HANDOFF.md`; do not infer completion from this static phase file.

**How to use:** Upload this file and the current handoff into a new ChatGPT chat. If the entire project ZIP is uploaded, read `docs/TEACHING_CONTRACT.md` and `docs/CHAT_WORKFLOW.md` first. The compact rules are duplicated below, so this packet also functions on its own.

# Universal teaching contract (applies in EVERY fresh chat)

- Purpose: take one absolute beginner from Java fundamentals to an explainable, demonstrable financial backend project; simulate banking; do NOT present it as certified bank-grade production software.
- Roles: learner writes and understands business logic; assistant teaches and reviews. Assistant may supply small unrelated syntax examples, command explanations, setup/config scaffolding and tests where appropriate; never silently implement core financial algorithms for the learner.
- Explain ONE new idea at a time, then offer ONE practical small task; respond to questions directly before assigning more work. Use simple English by default; explain in Persian whenever requested. Keep code and tool names in English.
- Every NEW command is explained before asking the learner to run it. Never invent terminal results, claims, version numbers, completion or hours.
- Work chunks are generally 15–60 minutes; no forced 90-minute script, TIME BOX or DO NOT LOOK AHEAD headings. The learner has an irregular schedule, often approximately two hours in an evening.
- Prefer understanding over deadlines. Estimates are indicative, not phase gates. Split complex steps into small substeps, without silently dropping requirements.
- Review with ACCEPT / ACCEPT WITH NOTES / REVISE / REVERT AND REDO and constructive evidence-based feedback. Explicit completion claims can count as self-reported evidence for basic setup, not as proof of passing code, security or financial invariants.
- Use hints before full solutions. Never make unverified changes to code or deployment. Ask for visible evidence for consequential operations.
- At phase end: acceptance evidence + quiz >=80% with reasoning (or record gaps), concise phase review, progress update, portable handoff, next-phase start declared. Mark N as COMPLETE only after all gates satisfied; otherwise IN PROGRESS.
- Important phase-appropriate grading: assess a dimension only when taught; not-yet-taught features like concurrency/authorization must not block Phase 0 or Phase 1. Record them as NOT APPLICABLE, not failures. Later relevant gates follow the Handbook.
- Distinguish AI-generated instructions (can propose file text for the learner to save) from actual repository changes (only real saved files count). Do not claim a Git commit, a PR, CI run, or a successful command without evidence. If GitHub/CI is not yet configured, note it as deferred rather than fabricating its success.
- Keep security from the start: no credentials or personal tokens pasted in chat or committed.

## Cross-chat contract
A new chat has **no guaranteed access** to older conversations or their unstored context. The learner must attach the updated project ZIP or paste the new chat starter plus the current handoff and phase file; do not pretend you can see their device files merely because they have downloaded a ZIP. The uploaded snapshot is authoritative. If both are supplied, use the latest dated handoff for step state and PROGRESS.md for overall state; flag any discrepancy rather than silently picking one. Never rely on ChatGPT memory for a phase gate.

Order of authority for pedagogical process: (1) explicit current user request; (2) this contract and `docs/CHAT_WORKFLOW.md`; (3) current `PROGRESS.md` and `docs/handoffs/CURRENT_HANDOFF.md` for recorded progress; (4) `docs/phases/PHASE_NN.md` for phase content; (5) full Handbook for technical detail; (6) archival `docs/mainVersion/` references. For safety/integrity preserve immutable financial requirements in the Handbook.


## Entry/exit handshake
1. On entry, verify whether earlier phase is COMPLETE based on saved evidence. If unavailable, disclose the gap and start with verification instead of making up status.
2. Teach one concept per reply, ask one small concrete task, and review learner output.
3. At exit, document acceptance criteria, quiz answers, relevant quality scores, evidence, open gaps, summary of commits (only verified ones), exact next action; update `PROGRESS.md` and `CURRENT_HANDOFF.md` in the local project.
4. Once Phase 04 is complete, direct the learner to a NEW chat for Phase 05 with the updated files.


## Original technical phase contract (preserved verbatim)

# PHASE 4 — PostgreSQL, SQL, transactions and locking
**Effort: 4 weeks.** Closes: relational design, SQL, indexes, ACID, isolation, JDBC, pooling, Flyway.

**Goal.** The ledger survives a restart, in a real database, with real transactional guarantees and database-enforced immutability.

**Concepts:**
1. **Relational modelling** — accounts, transactions, entries; every constraint the database can enforce *is* enforced by the database: `NOT NULL`, `FOREIGN KEY`, `CHECK (amount_minor > 0)`, `UNIQUE (idempotency_key)`.
2. **Why minor units are `BIGINT`** — continuation of ADR 0001: exact, indexable, sum-safe; contrast with `NUMERIC` and its cost.
3. **Transactions and ACID** — a transfer is two inserts plus state changes: all or nothing. Demonstrate a crash between the two inserts by killing the JVM inside a transaction.
4. **Isolation levels and the four anomalies** — the level table, and why the project runs `READ_COMMITTED` plus explicit row locks where needed.
5. **Row locking** — `SELECT … FOR UPDATE` inside the transaction; ordered locking as the database version of Phase 3's lesson; `NOWAIT`/timeouts as the escape hatch.
6. **Deadlocks in the database** — engine detection, retry with bounded backoff and jitter, and why a retry must be idempotent.
7. **Indexes** — the query pattern first ("today's entries for this account in order"), then the composite index that serves it; prove with `EXPLAIN ANALYZE` before and after.
8. **JDBC by hand before any ORM** — `PreparedStatement`, batching, generated keys, try-with-resources, HikariCP pooling, pool-sizing mathematics, and the fact that a leaked connection is a production outage.
9. **Migrations with Flyway** — versioned SQL, never editing an applied migration, forward-only philosophy.

| # | Task | Acceptance criteria | Evidence |
|---|---|---|---|
| 4.1 | Write the schema as Flyway migrations | Constraints present in `\d+` output | `psql` output |
| 4.2 | Revoke `UPDATE`/`DELETE` on `ledger_entries` (or add a raising trigger) | An attempted `UPDATE` fails; a test captures the error | Test output |
| 4.3 | Implement `JdbcLedgerRepository` with a real transaction per posting | Kill-the-JVM test: no partial transaction survives; balances rebuild identically after restart | Test + restart log |
| 4.4 | Implement ordered pessimistic locking for transfers | 500 concurrent transfers: invariant holds, no lock timeouts, throughput recorded | Numbers |
| 4.5 | Force and handle a database deadlock | Bounded retry with backoff; test asserts eventual success | Test output |
| 4.6 | Add and justify indexes with before/after plans | Query uses the index; timings recorded | Two `EXPLAIN ANALYZE` plans |
| 4.7 | Reconciliation query: cached balance vs fold of entries, per account per currency | Zero mismatches after the concurrency test | Query output |
| 4.8 | Tune the pool and demonstrate behaviour at exhaustion | Documented size, timeout, observed failure mode | Log excerpt |

**Agent must not:** design the schema for him. Give the access patterns, require a proposal, then critique it against them.

**Mistakes to expect.** Balance check and insert in two separate transactions (the race the database would have prevented); auto-commit sneaking in; `SELECT *`; `timestamp without time zone`; money as `DECIMAL` in one table and `BIGINT` in another; long transactions holding locks during HTTP calls; no retry on serialization or deadlock failures; forgetting that a unique violation is the *correct* way to resolve a duplicate-key race.

**Gate quiz.** (1) Which anomaly does `READ_COMMITTED` still allow? (2) Why must lock order be deterministic? (3) Why can a unique constraint beat an application-level check? (4) What exactly happens to an open transaction when the JVM is killed? (5) Why is the cached balance allowed to exist at all?

---

## Source of full curriculum
For deep background, consult `docs/ApexWallet_Agent_Handbook.md` Parts 1–3 and 5–7. Its rigid session format/timebox and blanket early-phase rubric requirements are superseded by the current teaching contract.
