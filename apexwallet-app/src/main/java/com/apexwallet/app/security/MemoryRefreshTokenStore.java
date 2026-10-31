package com.apexwallet.app.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class MemoryRefreshTokenStore implements RefreshTokenStore {

    private final ConcurrentHashMap<String, RefreshSession> byHash = new ConcurrentHashMap<>();

    @Override
    public void save(RefreshSession session) {
        byHash.put(session.tokenHash(), session);
    }

    @Override
    public Optional<RefreshSession> findByHash(String tokenHash) {
        return Optional.ofNullable(byHash.get(tokenHash));
    }

    @Override
    public void revoke(String tokenHash) {
        byHash.computeIfPresent(tokenHash, (ignored, session) -> session.revoke());
    }

    @Override
    public void revokeFamily(UUID familyId) {
        byHash.replaceAll((ignored, session) -> session.familyId().equals(familyId) ? session.revoke() : session);
    }
}
