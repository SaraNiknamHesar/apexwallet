# ADR 0009 — Fee rounding and day count

## Status
Accepted.

## Decision
Fees and interest round with HALF_EVEN, the same policy as `Money`. Day count for the study engine is ACT/360 by default in examples, with ACT/365 and 30/360 implemented and tested. 30/360 caps each day-of-month at 30.

## Consequences
Changing the convention changes customer interest. The formula lives in `Interest` and is covered by hand-calculated tests, including 29 February 2024.
