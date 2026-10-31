package com.apexwallet.app.security;

import java.util.Optional;

public interface RefreshTokenStore {

    void save(RefreshSession session);

    Optional<RefreshSession> findByHash(String tokenHash);

    void revoke(String tokenHash);

    void revokeFamily(java.util.UUID familyId);
}
