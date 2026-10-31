# Phase 14 — Independent Chat Packet

**Topic:** Observability and performance under load

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
4. Once Phase 14 is complete, direct the learner to a NEW chat for Phase 15 with the updated files.


## Original technical phase contract (preserved verbatim)

# PHASE 14 — Observability and performance under load
**Effort: 3 weeks.** Closes: metrics, tracing, load testing, virtual threads, JVM performance basics.

**Goal.** The system tells you what it is doing and how fast, with numbers instead of opinions.

**Concepts:**
1. **The four golden signals** for a payment API — latency (p50/p95/p99), traffic, errors, saturation — plus the banking north star, `ledger_imbalance_total == 0`.
2. **Metrics design** — counters, gauges, histograms, and cardinality discipline (never label with `accountId` or `customerId`; explain the cardinality explosion), naming, and business metrics (posted transfers per minute, average transfer value).
3. **Structured logs and correlation ids** — JSON, correct levels, no PII, log volume as a cost.
4. **Tracing** — spans from controller to service to database to Kafka, and how to read a waterfall.
5. **Health and readiness** for containers, graceful shutdown, and why an in-flight payment must not be killed mid-transaction.
6. **Load testing that answers a question** — define the SLO first ("p99 posting latency under 500 concurrent users below 300 ms"), build the scenario with k6 or Gatling, run it, *then* interpret. A load test without a question produces a screenshot nobody can use.
7. **Finding the bottleneck** — pool size vs thread count vs database CPU; the queueing intuition that throughput saturates and latency explodes; measure before optimising.
8. **Virtual threads** — compare a platform-thread server under 300 blocked database calls with a virtual-thread server, measure, then explain that the bottleneck merely moved to the database. That is the actual lesson.
9. **JVM and JMH basics** — warm-up, measurement, allocation, and why single-run timings lie.

| # | Task | Acceptance criteria | Evidence |
|---|---|---|---|
| 14.1 | Instrument with Micrometer including the imbalance gauge | A Grafana dashboard shows all golden signals | Screenshot |
| 14.2 | Enable tracing and show one request's span tree | Waterfall captured | Screenshot |
| 14.3 | Write the SLO and the load scenario | SLO written before any run; scenario reproducible from a script in the repo | SLO + script |
| 14.4 | Run the load test and produce a latency report | p50/p95/p99 under load; saturation point identified | Graph + numbers |
| 14.5 | Optimise the top bottleneck with evidence | Before/after numbers, plus what got worse or why nothing did | Two runs |
| 14.6 | Switch to virtual threads and re-measure | A comparative table with a written interpretation | Table |
| 14.7 | JMH benchmark for fee calculation | Warm-up discipline; results with confidence intervals | JMH output |
| 14.8 | Alert rules for imbalance, error rate, DLQ and lag | Each alert has a documented first response action | Config + runbook page |

**Agent must not:** accept "it feels faster". Every claim is a measurement, and he states what he measured and how.

**Mistakes to expect.** Unbounded label cardinality; logging every request body (PII); comparing runs with different data volumes; a load generator competing for resources with the system under test; confusing throughput with latency; trusting one run; optimising before measuring; claiming virtual threads "fixed" a database-bound path.

**Gate quiz.** (1) Why is p99 more interesting than the mean in payments? (2) What is your measured saturation point, and what saturates first? (3) Why did virtual threads not magically increase throughput, and what did you learn? (4) Which log line would you remove before production, and why? (5) What is your alerting response to a single DLQ message?

---

## Source of full curriculum
For deep background, consult `docs/ApexWallet_Agent_Handbook.md` Parts 1–3 and 5–7. Its rigid session format/timebox and blanket early-phase rubric requirements are superseded by the current teaching contract.
