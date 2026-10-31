package com.apexwallet.app.identity;

import java.time.Instant;
import java.util.UUID;

public record AppUser(
        UUID id,
        String username,
        String passwordHash,
        Role role,
        int failedAttempts,
        Instant lockedUntil
) {
    public AppUser withFailures(int failures, Instant lockedUntil) {
        return new AppUser(id, username, passwordHash, role, failures, lockedUntil);
    }
}
