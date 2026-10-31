package com.apexwallet.app.security;

import com.apexwallet.app.config.ApexProperties;
import com.apexwallet.app.identity.AppUser;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final ApexProperties properties;

    public AccessTokenService(JwtEncoder encoder, Clock clock, ApexProperties properties) {
        this.encoder = encoder;
        this.clock = clock;
        this.properties = properties;
    }

    public IssuedAccess issue(AppUser user) {
        Instant now = clock.instant();
        Instant expires = now.plus(properties.getAccessTokenTtl());
        String jti = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("apexwallet")
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(expires)
                .id(jti)
                .claim("username", user.username())
                .claim("roles", List.of(user.role().name()))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new IssuedAccess(token, jti, expires, properties.getAccessTokenTtl().toSeconds());
    }

    public record IssuedAccess(String token, String jti, Instant expiresAt, long expiresInSeconds) {
    }
}
