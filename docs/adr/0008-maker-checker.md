# ADR 0008 — Maker-checker above a threshold

## Status
Accepted.

## Context
A single user should not both create and approve a large payment.

## Decision
A transfer at or above `apex.approval-threshold-minor` (default 100000 minor units) is stored as `PENDING_APPROVAL` and is not posted. Approval is `SUPERVISOR` only. The maker and the checker must be different people, even if the maker's token is given the supervisor role. Rejection is terminal. The HTTP story test sets the threshold to 5000 so the path is easy to show.

## Consequences
Approval does not re-check the idempotency record of the original post. Insufficient funds are checked when the posting finally runs.
