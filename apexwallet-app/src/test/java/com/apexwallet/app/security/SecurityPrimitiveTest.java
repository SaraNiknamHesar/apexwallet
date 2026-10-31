package com.apexwallet.app.security;

import com.apexwallet.app.audit.MemoryAuditSink;
import com.apexwallet.app.config.ApexProperties;
import com.apexwallet.domain.error.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityPrimitiveTest {

    @Test
    void identicalPasswordsDoNotProduceIdenticalHashes() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String first = encoder.encode("same-password-1");
        String second = encoder.encode("same-password-1");
        assertNotEquals(first, second);
        assertTrue(encoder.matches("same-password-1", first));
        assertTrue(encoder.matches("same-password-1", second));
    }

    @Test
    void reusedRefreshTokenRevokesTheFamily() {
        ApexProperties properties = new ApexProperties();
        properties.setRefreshTokenTtl(Duration.ofDays(1));
        MemoryRefreshTokenStore store = new MemoryRefreshTokenStore();
        MemoryAuditSink audit = new MemoryAuditSink();
        RefreshTokenService service = new RefreshTokenService(store, audit, Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC), properties);
        UUID user = UUID.randomUUID();
        String first = service.issue(user);
        String second = service.rotate(first).refreshToken();
        assertThrows(DomainException.class, () -> service.rotate(first));
        assertThrows(DomainException.class, () -> service.rotate(second));
        assertEquals("REFRESH_REUSE", audit.events().get(0).action());
    }

    @Test
    void limiterReturnsFalseInsideTheWindow() {
        WindowRateLimiter limiter = new WindowRateLimiter();
        Instant now = Instant.parse("2026-10-23T12:00:00Z");
        assertTrue(limiter.allow("alice", 2, Duration.ofMinutes(1), now));
        assertTrue(limiter.allow("alice", 2, Duration.ofMinutes(1), now.plusSeconds(1)));
        assertTrue(!limiter.allow("alice", 2, Duration.ofMinutes(1), now.plusSeconds(2)));
        assertTrue(limiter.allow("alice", 2, Duration.ofMinutes(1), now.plusSeconds(61)));
    }
}
