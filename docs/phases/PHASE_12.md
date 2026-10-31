# Phase 12 — Independent Chat Packet

**Topic:** Event backbone: Kafka and the transactional outbox

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
4. Once Phase 12 is complete, direct the learner to a NEW chat for Phase 13 with the updated files.


## Original technical phase contract (preserved verbatim)

# PHASE 12 — Event backbone: Kafka and the transactional outbox
**Effort: 3 weeks.** Closes: messaging, exactly-once thinking, distributed failure modes.

**Goal.** Ledger events leave the system reliably, without a distributed transaction, and no event is lost or double-applied.

**Concepts:**
1. **Why an event backbone** — notifications, analytics, fraud scoring, downstream systems. A bank does not call downstream systems synchronously inside a payment.
2. **The dual-write problem** — commit then publish: the process can die between the two. Publish then commit: a phantom payment. Draw both failure timelines explicitly; this diagram is worth more than the code.
3. **The transactional outbox** — write the event row in the *same* database transaction as the post; a relay publishes and marks delivered; retries with backoff; at-least-once delivery is the only honest guarantee.
4. **Idempotent consumers** — dedupe on a message key persisted in the consumer's own database, in the same transaction as the effect. Reuse Phase 7's machinery consciously.
5. **Kafka fundamentals worth mastering** — topics, partitions, keys and per-key ordering (all events for one account must land in one partition), consumer groups, offsets, rebalancing, retention, replay, and why a compacted topic is a different tool.
6. **Poison messages and the dead-letter queue** — a message that can never be processed must not block its partition forever; DLQ plus an operational procedure.
7. **Backpressure and lag** — outbox growth, relay throughput, consumer lag as a first-class metric, and what "the system is behind" means operationally.

| # | Task | Acceptance criteria | Evidence |
|---|---|---|---|
| 12.1 | Dual-write failure analysis with two timelines | Both failure modes named with the exact crash point | Document |
| 12.2 | Outbox table and transactional write | The event row commits atomically with the post; a test proves rollback removes both | Test |
| 12.3 | Relay with retry and backoff | Kill the relay mid-batch → every event is eventually published exactly once in *effect* | Chaos test output |
| 12.4 | Idempotent consumer with its own dedupe store | Redelivering the same event twice produces one effect | Test |
| 12.5 | Key by account id, prove per-account ordering | A test asserts ordering per key; the report documents what is *not* ordered | Test |
| 12.6 | DLQ after N attempts with an alerting metric | A poison event lands in the DLQ and the partition keeps moving | Test + metric |
| 12.7 | Lag and outbox-backlog metrics with a dashboard | A deliberate consumer stop visibly increases lag | Screenshot |
| 12.8 | ADR: why not synchronous calls, why not two-phase commit | Names the costs: eventual consistency, complexity, operational burden | ADR |

**Agent must not:** write the relay. Ask instead: "Where exactly can this process die, and what does the database contain afterwards?"

**Mistakes to expect.** Publishing inside the transaction *and* after it; a relay that marks delivered before publishing; a consumer that dedupes in memory; a partition key that spreads one account's events across partitions; retry without backoff; no DLQ; unbounded outbox growth because the relay is a manual endpoint; swallowed deserialization failures.

**Gate quiz.** (1) Draw the dual-write problem from memory. (2) Why is exactly-once delivery a myth, and what does the project actually guarantee? (3) Why must one account's events share a partition key? (4) What is the operational procedure for a DLQ message? (5) Where does the consumer's dedupe store live, and why?

---

## Source of full curriculum
For deep background, consult `docs/ApexWallet_Agent_Handbook.md` Parts 1–3 and 5–7. Its rigid session format/timebox and blanket early-phase rubric requirements are superseded by the current teaching contract.
