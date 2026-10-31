# ADR 0011 — The model is off the payment path

## Status
Accepted.

## Decision
Categorisation and risk hints call a `ModelPort`. Payments do not. If the port throws or returns a value outside the allowed set, the category is `OTHER`. Account numbers are masked before a prompt is built. A budget counter records tokens and cost. An agent query is logged with the principal and refused after a limit.

## Consequences
An LLM outage does not fail a transfer. Accuracy is checked against a golden set in `EvalHarness`. This build does not call a hosted model.
