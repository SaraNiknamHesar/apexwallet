# ADR 0007 — Field encryption plus a lookup hash

## Status
Accepted.

## Context
An IBAN must not sit in plaintext, but equality search on ciphertext is useless because AES-GCM uses a fresh nonce.

## Decision
External account numbers are encrypted with AES-GCM, a random 12-byte nonce, a 128-bit tag, and a key id so keys can rotate. A separate HMAC-SHA256 of the normalized value is the lookup key. Local keys are ephemeral when `apex.data-keys` and the HMAC key are empty.

## Consequences
Ciphertext changes every write of the same IBAN. The HMAC stays stable until the HMAC key rotates. The demo reveals the plaintext only through the application, after an ownership check.
