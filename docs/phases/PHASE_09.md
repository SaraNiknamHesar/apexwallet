# Phase 09 — Independent Chat Packet

**Topic:** Audit trail, enforced immutability and reconciliation

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
4. Once Phase 09 is complete, direct the learner to a NEW chat for Phase 10 with the updated files.


## Original technical phase contract (preserved verbatim)

# PHASE 9 — Audit trail, enforced immutability and reconciliation
**Effort: 3 weeks.** Closes: AOP, compliance thinking, observability of correctness.

**Goal.** Every financially meaningful action is traceable to an actor, and the ledger proves itself consistent every night.

**Concepts:**
1. **AOP for cross-cutting concerns** — advice types, pointcuts, ordering, and the honest rule: AOP is for *observation and policy*, never for business decisions that must be visible in the code.
2. **What an audit record contains** — who (principal), what (operation and parameters, redacted), when (UTC instant), where (channel/IP), result, correlation id, and the previous state where relevant. Written to an insert-only table.
3. **Immutability enforced in layers** — application rule, database grant or trigger, and an append-only verification query run both in tests and in production monitoring.
4. **Reconciliation** — the daily job comparing derived balances, cached balances, a checksum over the day's entries, and (later) the external `camt.053` statement. Output: a report with zero mismatches, plus a defined procedure for when it is not zero.
5. **Correlation ids end to end** — set from the request, propagated through threads (including async work), present in every log line and in the audit record. Revisit with virtual threads in Phase 14.
6. **Read models and reports** — why reporting queries must not hammer transactional tables; the first step toward a reporting view or materialised view.

| # | Task | Acceptance criteria | Evidence |
|---|---|---|---|
| 9.1 | `@Auditable` aspect writing to `audit_log` | Every money-moving endpoint produces one audit row with actor, operation, result, correlation id | Rows shown (redacted) |
| 9.2 | Prove no business code changed to produce the trail | `git diff` shows only the aspect and annotations | Diff |
| 9.3 | Append-only proof test | Attempted update/delete on `ledger_entries` and `audit_log` fails at the database level, with the error asserted | Test output |
| 9.4 | Daily reconciliation job | Zero mismatches after a randomised load test; a deliberately injected mismatch **is detected** | Two report outputs |
| 9.5 | Correlation id propagation across threads and async execution | One id traces a request from controller to database to async handler | Log excerpt |
| 9.6 | Reporting view or materialised view for monthly volume | Slow transactional query replaced; timings recorded | Timings |
| 9.7 | Recovery drill: restore the database from a backup and reconcile | Written procedure with the actual commands and a produced report | Procedure + report |

**Agent must not:** write the aspect. Require him first to write, in prose, what an auditor would need to reconstruct — then implement exactly that and nothing more.

**Mistakes to expect.** Logging in the aspect *and* in the service (duplication); a business decision inside an aspect; an audit row written outside the transaction so a rolled-back operation leaves a claim of success (subtle — make him discover and decide the semantics); PII in audit parameters; aspect ordering interacting badly with `@Transactional`.

**Gate quiz.** (1) Why is an audit trail useless if it can be edited? (2) What semantics did you choose for an audit row of a rolled-back operation, and why? (3) What does reconciliation detect that tests cannot? (4) Why must the correlation id survive thread boundaries? (5) When is AOP the wrong tool?

---

## Source of full curriculum
For deep background, consult `docs/ApexWallet_Agent_Handbook.md` Parts 1–3 and 5–7. Its rigid session format/timebox and blanket early-phase rubric requirements are superseded by the current teaching contract.
