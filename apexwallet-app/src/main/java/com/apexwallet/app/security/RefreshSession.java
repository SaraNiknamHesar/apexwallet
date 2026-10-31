package com.apexwallet.app.security;

import java.time.Instant;
import java.util.UUID;

public record RefreshSession(String tokenHash, UUID familyId, UUID userId, boolean revoked, Instant expiresAt) {

    public RefreshSession revoke() {
        return new RefreshSession(tokenHash, familyId, userId, true, expiresAt);
    }
}
