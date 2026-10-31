# ADR 0006 — RS256 access tokens and short lifetimes

## Status
Accepted for the local study profile.

## Context
A symmetric secret in the repository would be a leaked signing key. A long-lived access token cannot be revoked cheaply.

## Decision
Access tokens are RS256 JWTs. The RSA key is generated in memory at startup and is not committed. Issuer is `apexwallet`. Access TTL is 15 minutes. Refresh tokens are opaque, rotate on use, and last 7 days. Reuse of a refresh token revokes the family and writes audit action `REFRESH_REUSE`. Logout puts the access `jti` on a denylist.

## Consequences
Restarting the local process invalidates outstanding access tokens. Production would load the key from a KMS and persist refresh sessions.
