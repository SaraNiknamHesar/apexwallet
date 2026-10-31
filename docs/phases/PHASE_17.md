# Phase 17 — Independent Chat Packet

**Topic:** STRETCH: capital markets and trading systems awareness

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
4. Once Phase 17 is complete, declare the optional programme finished; no Phase 18 exists with the updated files.


## Original technical phase contract (preserved verbatim)

# PHASE 17 — STRETCH: capital markets and trading systems awareness
**Effort: optional, 4–6 weeks, only after Phase 16 (or as reading interleaved with earlier phases).**

A banking career often forks toward capital markets. This phase exists so the learner speaks the language of a trading floor and can build a second, smaller demonstrator. It is **awareness plus one small build**, not a rewrite of the curriculum.

**Concepts:**
1. **Order lifecycle** — `NEW → PARTIALLY_FILLED → FILLED / CANCELLED / REJECTED`; order types (market, limit, stop); the difference between an *order* and a *trade*; idempotency in order entry (the same problem as Phase 7, different vocabulary).
2. **Matching engine basics** — price-time priority, the limit order book, partial fills, and why a matching engine is single-threaded per instrument by design (determinism beats parallelism).
3. **Market data** — quotes vs trades, level 1/2 depth, conflation, sequencing, and why market data is a firehose with lossy guarantees.
4. **Positions and P&L** — position keeping, average cost, realised vs unrealised P&L, mark-to-market, and the accounting link back to the ledger he already built (a trade *is* a double-entry event).
5. **Risk limits** — pre-trade checks (notional, position, credit), margin, circuit breakers; connected to Phase 10's limit machinery.
6. **Clearing and settlement** — central counterparty, novation, T+1 cycles, and the difference between *executed* and *settled* he already met in §3.9.
7. **FIX protocol awareness** — session layer, logon/heartbeat, `NewOrderSingle` / `ExecutionReport`, tags and repeating groups, sequence-number gaps and resend requests. **Never guess tag numbers: read the specification.**
8. **Latency, throughput and determinism** — why low-latency systems avoid allocation, GC pauses and hot-path logging; lock-free queues; and the trade-off against maintainability.
9. **Regulatory vocabulary** — MiFID II, best execution, transaction reporting, market abuse surveillance, order-to-trade ratio. Understand the obligations, not the paperwork.

**Suggested small build (choose one, cap at 4 weeks):**

- **A. Limit order book and matching engine** — pure Java, price-time priority, partial fills, deterministic replay from an event log, property tests asserting "no crossed book" and "quantity is conserved". Reuses Phase 2 and Phase 3 lessons exactly.
- **B. A FIX session client** — logon, heartbeat, sequence numbers, gap detection, a `NewOrderSingle`/`ExecutionReport` round trip against a simulator, strict message validation. Reuses Phase 11's spec-driven discipline.
- **C. Mark-to-market P&L engine** — positions from trades, prices from a feed, realised and unrealised P&L, posting the accounting entries into the Phase 2 ledger. Ties the programme together.

**Acceptance criteria (any option).** The artefact runs, has an invariant test suite, has an ADR explaining design choices and their costs, and the learner can answer: *why is determinism more important than throughput in a matching engine?* and *what is the accounting entry behind a trade?*

**Agent must not:** let this phase delay Phase 16, and must not teach trading as the same discipline. Say it plainly: trading systems optimise for latency and determinism; core banking optimises for correctness and auditability. Both are honest careers, and the project he has already built is the stronger beginner portfolio.

## Source of full curriculum
For deep background, consult `docs/ApexWallet_Agent_Handbook.md` Parts 1–3 and 5–7. Its rigid session format/timebox and blanket early-phase rubric requirements are superseded by the current teaching contract.
