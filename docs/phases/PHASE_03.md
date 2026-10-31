# Phase 03 — Independent Chat Packet

**Topic:** Concurrency and the Java Memory Model (the money-vanishing lab)

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
4. Once Phase 03 is complete, direct the learner to a NEW chat for Phase 04 with the updated files.


## Original technical phase contract (preserved verbatim)

# PHASE 3 — Concurrency and the Java Memory Model (the money-vanishing lab)
**Effort: 3 weeks.** Closes: threads, pools, locks, JMM, atomics, deadlock.

**Goal.** Reproduce real money loss under concurrency, then eliminate it with proof.

**Concepts:**
1. **The lost update** — read, modify, write. Two threads, one balance, money created or destroyed. He sees it happen *before* learning the fix.
2. **`ExecutorService` and pools** — sizing, queueing, shutdown, and why `Executors.newFixedThreadPool` hides an unbounded queue.
3. **`synchronized` and `ReentrantLock`** — what a monitor guarantees, reentrancy, fairness, `tryLock` with timeout as a deadlock escape hatch.
4. **Deadlock** — the four conditions, lock ordering, reproduction, then the fix. He must produce a deadlock, observe it, and fix it by ordering locks by account id.
5. **The Java Memory Model** — happens-before, visibility, reordering, why `volatile` fixes visibility but not atomicity, and why an assertion can fail only under load.
6. **CAS and atomics** — `AtomicLong`, `LongAdder`, ABA as awareness. Where lock-free belongs (metrics, counters) and where it does not (multi-account invariants).
7. **Immutability as the cheapest concurrency strategy** — tie back to Phase 1: shared immutable `Money` needs no lock.

| # | Task | Acceptance criteria | Evidence |
|---|---|---|---|
| 3.1 | Failing concurrency test: 100 threads transfer between two accounts, unsynchronised | The sum of balances changes (money created or destroyed) | Failing output with the differing numbers |
| 3.2 | Fix with a `ReentrantLock` strategy | Sum invariant over 1000 runs | Test output |
| 3.3 | Introduce a second lock and reproduce a deadlock | Thread dump shows the cycle | Thread dump |
| 3.4 | Fix with global lock ordering by `AccountId` | No deadlock in 10 000 randomised transfers; throughput recorded for both versions | Numbers |
| 3.5 | Demonstrate a visibility bug with a shared non-volatile flag, then fix it | A short note explaining happens-before in his own words | The note |
| 3.6 | Concurrent metrics counter with `LongAdder` | Under load the counter equals posted transactions exactly | Test |
| 3.7 | Escalate to 1000 concurrent transfers | Invariants hold; median and p99 posting time recorded | Timing table |

**Agent must not:** hand over the fix. Let him find lock ordering, guided by "what happens if A locks account 1 then 2, and B locks 2 then 1?"

**Mistakes to expect.** `synchronized` on one path while another path bypasses it; `volatile` for a counter; swallowing `InterruptedException`; `Thread.sleep`-based "fixes"; believing a `ConcurrentHashMap` makes a multi-step operation atomic; measuring performance with one run on a cold JIT.

**Gate quiz.** (1) What does `volatile` guarantee, and what not? (2) Name the four deadlock conditions. (3) Why does ordered locking prevent deadlock but not starvation? (4) Why is your fix useless once the ledger runs in two JVMs? (5) Which mechanism moves correctness into the database, and what does it cost?

---

## Source of full curriculum
For deep background, consult `docs/ApexWallet_Agent_Handbook.md` Parts 1–3 and 5–7. Its rigid session format/timebox and blanket early-phase rubric requirements are superseded by the current teaching contract.
