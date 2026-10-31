# Phase 06 — Independent Chat Packet

**Topic:** Spring Boot: the container and the API

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
4. Once Phase 06 is complete, direct the learner to a NEW chat for Phase 07 with the updated files.


## Original technical phase contract (preserved verbatim)

# PHASE 6 — Spring Boot: the container and the API
**Effort: 4 weeks.** Closes: IoC/DI, bean lifecycle, configuration, Web MVC, validation, error handling, OpenAPI.

**Goal.** The ledger runs inside Spring Boot behind a well-designed REST API, with the domain untouched by the framework.

**Concepts:**
1. **The problem IoC solves** — he has been constructing his own object graph; now a container does it. Make the before/after contrast concrete in his own code.
2. **Constructor injection only** — final fields, no field injection, and why: immutability, testability, failure at startup instead of an NPE at runtime.
3. **Bean lifecycle and proxies** — `@PostConstruct`, scopes, and the proxy trap: a `@Transactional`/`@Async` call inside the same bean bypasses the proxy. He reproduces this trap once and never forgets it.
4. **Configuration without hard-coding** — `@ConfigurationProperties` records, profiles (`dev`, `test`, `prod`), environment variables, and the rule that no secret is ever in a git-tracked file.
5. **Web layer discipline** — DTOs in and out (never entities), request/response separation, HTTP semantics (201 for creation, 409 for conflict, 422 vs 400, idempotent PUT vs POST), pagination, a versioning strategy.
6. **Validation** — `@Valid` on the DTO, Jakarta constraints, one custom constraint (IBAN) to prove extension points; validation failures return structured errors, never stack traces.
7. **One error format for the API** — RFC 7807 problem details via `@ControllerAdvice` with stable error codes (`INSUFFICIENT_FUNDS`, `CURRENCY_MISMATCH`, `IDEMPOTENCY_CONFLICT`, …). Error codes are part of the contract and are documented.
8. **OpenAPI as a contract** — generated spec, reviewed like code, because integrators build against it.
9. **Layering** — controller → application service (use case) → domain → repository. The application service owns the transaction boundary; the domain owns the invariants. Explain why an anemic domain model is a trap here.

| # | Task | Acceptance criteria | Evidence |
|---|---|---|---|
| 6.1 | Refactor onto Spring Boot with constructor injection everywhere | No `new` for services; startup logs the wired graph | Startup log |
| 6.2 | Implement `TransferController`, `AccountController`, `StatementController` with DTOs | No entity type in any controller signature (grep/test) | Grep output |
| 6.3 | Implement validation with at least one custom constraint | Invalid requests return 400/422 with field-level detail | Response bodies |
| 6.4 | Global exception handler with problem details and error codes | A failed transfer returns a typed body with a code and no stack trace | Response body |
| 6.5 | Publish OpenAPI documentation and review it as a contract | Spec matches the running API; every endpoint documents its error codes | Spec + screenshot |
| 6.6 | Reproduce the self-invocation proxy trap and document it | A test proves the trap; a note explains it | Test + note |
| 6.7 | Paginated statement endpoint | Stable ordering; `page`/`size` bounded to prevent abuse | Response |
| 6.8 | `@WebMvcTest` slice tests for controllers | Controllers tested without a database, in seconds | Test run |

**Agent must not:** write the controllers, DTOs, mappers or exception handler. Require him to propose the endpoint list first (method, path, request, responses) and critique it against HTTP semantics.

**Mistakes to expect.** Entities on the wire; 200 with `{"success": false}` for a failed transfer; validation in the service instead of at the boundary; catching an exception only to rethrow a `RuntimeException` with no code; leaking `SQLException` messages; `@Autowired` fields; business logic in the controller.

**Gate quiz.** (1) Why does constructor injection catch wiring errors earlier? (2) Which two things does the proxy trap break, and how do you detect it? (3) When is 422 better than 400? (4) Why must an error code be stable and documented? (5) Where exactly is the transaction boundary in your code, and who owns it?

---

## Source of full curriculum
For deep background, consult `docs/ApexWallet_Agent_Handbook.md` Parts 1–3 and 5–7. Its rigid session format/timebox and blanket early-phase rubric requirements are superseded by the current teaching contract.
