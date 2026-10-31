package com.apexwallet.app.security;

import com.apexwallet.app.audit.AuditSink;
import com.apexwallet.app.config.ApexProperties;
import com.apexwallet.app.crypto.CanonicalHash;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenStore store;
    private final AuditSink audit;
    private final Clock clock;
    private final ApexProperties properties;

    public RefreshTokenService(RefreshTokenStore store, AuditSink audit, Clock clock, ApexProperties properties) {
        this.store = store;
        this.audit = audit;
        this.clock = clock;
        this.properties = properties;
    }

    public String issue(UUID userId) {
        return issue(userId, UUID.randomUUID());
    }

    public String issue(UUID userId, UUID familyId) {
        String raw = UUID.randomUUID() + "." + UUID.randomUUID();
        store.save(new RefreshSession(
                CanonicalHash.sha256(raw),
                familyId,
                userId,
                false,
                clock.instant().plus(properties.getRefreshTokenTtl())));
        return raw;
    }

    public Rotated rotate(String raw) {
        RefreshSession session = store.findByHash(CanonicalHash.sha256(raw))
                .orElseThrow(this::unauthorized);
        if (session.revoked()) {
            store.revokeFamily(session.familyId());
            audit.append("REFRESH_REUSE", session.userId().toString(), clock.instant(), org.slf4j.MDC.get("correlation_id"));
            throw unauthorized();
        }
        if (!session.expiresAt().isAfter(clock.instant())) {
            throw unauthorized();
        }
        store.revoke(session.tokenHash());
        return new Rotated(session.userId(), issue(session.userId(), session.familyId()));
    }

    private DomainException unauthorized() {
        return new DomainException(ErrorCode.UNAUTHORIZED, "refresh token rejected");
    }

    public record Rotated(UUID userId, String refreshToken) {
    }
}
