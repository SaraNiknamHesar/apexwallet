# Phase 10 — Independent Chat Packet

**Topic:** Fees, interest, limits and the rules engine

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
4. Once Phase 10 is complete, direct the learner to a NEW chat for Phase 11 with the updated files.


## Original technical phase contract (preserved verbatim)

# PHASE 10 — Fees, interest, limits and the rules engine
**Effort: 3 weeks.** Closes: switch expressions, sealed types, pattern matching, strategy and state patterns, financial calculations.

**Goal.** Real banking calculations — tariffs, interest accrual, limits — computable, testable and explainable.

**Concepts:**
1. **Fees as data, not branches** — a tariff table (product, transaction type, tier), minimum/maximum caps, percentage vs flat, currency handling, and an explicit rounding policy per tariff.
2. **Modelling with sealed types and `switch` expressions** — `sealed interface FeeRule permits FlatFee, PercentageFee, TieredFee, CappedFee`, with an exhaustive `switch` so that adding a rule type breaks compilation instead of silently doing nothing. This is his clearest demonstration of modern Java beyond syntax.
3. **Interest, done properly** — simple vs compound; **day-count conventions** (ACT/365F, ACT/360, 30/360) and why the choice changes the number; daily accrual with a monthly posting transaction; the correct place to round (accrue at higher precision, round the *posted* amount, and document it); leap years; value date vs booking date; zero and negative rates.
4. **Limits as concurrency problems** — per-transaction, daily and monthly cumulative, and velocity (N transactions per hour). Daily counters must survive concurrency: a locked counter row, an atomic conditional update, or a derived sum over the day's entries (slow but simple). He compares the three, chooses, and proves his choice under load.
5. **Explainability** — every fee, interest posting and limit rejection carries a machine-readable reason and the rule version that produced it. An unexplainable charge is a customer complaint a bank cannot answer.
6. **Test strategy for calculations** — table-driven tests with hand-computed expected values; boundary cases (zero, one minor unit, exactly at a cap, exactly at a limit, leap day, month-end); a property test that fees never exceed their cap.

| # | Task | Acceptance criteria | Evidence |
|---|---|---|---|
| 10.1 | Tariff table and fee rule types | Four rule types; exhaustive switch; adding a type breaks compilation if a branch is missing | Code + the build-error demonstration |
| 10.2 | Fee calculation with caps and correct rounding | Table-driven tests with hand-calculated values, including a rounding boundary | Test run |
| 10.3 | Interest accrual engine with three day-count conventions | Each convention tested against hand-computed values over a month including a leap day | Test run + worked examples in the PR |
| 10.4 | Monthly interest posting transaction | Posts a balanced transaction; an unposted accrual never appears in the ledger | Test |
| 10.5 | Limit checks with concurrency-safe daily counters | 100 concurrent transfers: the daily limit is never exceeded by one minor unit | Test with the aggregate printed |
| 10.6 | Explainability payload for every charge and rejection | Each carries rule id, rule version and reason code | Response body |
| 10.7 | ADR: rounding policy and day-count convention | States the policy, the rejected alternative, and who is affected by a change | ADR |

**Agent must not:** supply any formula. Give the requirement ("charge 0.5% of the amount, minimum 5 euro cents, maximum 2 EUR, rounded half-even") and make him derive and test it. For interest, give the day-count definition in words and require the arithmetic to be his.

**Mistakes to expect.** Rounding at every step instead of at the posting boundary (accumulating bias); `double` reappearing in an interest formula; using `LocalDate` arithmetic where `Instant` is required; a daily limit counter that resets on deployment; forgetting that a reversed transaction must release its limit usage; hard-coded currency assumptions in the fee engine.

**Gate quiz.** (1) Why does the day-count convention change the interest paid? (2) Where do you round, and why exactly there? (3) Which of your three limit designs fails at 100 concurrent requests, and why? (4) How would you prove a charge is correct to an auditor? (5) What happens to a limit counter when a transaction is reversed?

---

## Source of full curriculum
For deep background, consult `docs/ApexWallet_Agent_Handbook.md` Parts 1–3 and 5–7. Its rigid session format/timebox and blanket early-phase rubric requirements are superseded by the current teaching contract.
