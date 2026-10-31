package com.apexwallet.app.identity;

import com.apexwallet.app.audit.Audited;
import com.apexwallet.app.config.ApexProperties;
import com.apexwallet.app.port.UserDirectory;
import com.apexwallet.app.security.AccessTokenService;
import com.apexwallet.app.security.Denylist;
import com.apexwallet.app.security.RefreshTokenService;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserDirectory users;
    private final PasswordEncoder encoder;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final Denylist denylist;
    private final Clock clock;
    private final ApexProperties properties;
    private final String dummyHash;

    public AuthService(UserDirectory users, PasswordEncoder encoder, AccessTokenService accessTokens,
                        RefreshTokenService refreshTokens, Denylist denylist, Clock clock, ApexProperties properties) {
        this.users = users;
        this.encoder = encoder;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.denylist = denylist;
        this.clock = clock;
        this.properties = properties;
        this.dummyHash = encoder.encode("dummy-password-not-used");
    }

    public AppUser register(String username, String password) {
        if (users.findByUsername(username).isPresent()) {
            throw new DomainException(ErrorCode.DUPLICATE, "username already registered");
        }
        AppUser user = new AppUser(UUID.randomUUID(), username, encoder.encode(password), Role.CUSTOMER, 0, null);
        users.save(user);
        return user;
    }

    @Audited(action = "LOGIN")
    public TokenPair login(String username, String password) {
        AppUser user = users.findByUsername(username).orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash);
            throw new DomainException(ErrorCode.UNAUTHORIZED, "invalid credentials");
        }
        Instant now = clock.instant();
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
            throw new DomainException(ErrorCode.UNAUTHORIZED, "account locked");
        }
        if (!encoder.matches(password, user.passwordHash())) {
            int failures = user.failedAttempts() + 1;
            Instant lock = failures >= properties.getLoginMaxFailures() ? now.plus(properties.getLoginLockout()) : null;
            users.save(user.withFailures(failures, lock));
            throw new DomainException(ErrorCode.UNAUTHORIZED, "invalid credentials");
        }
        users.save(user.withFailures(0, null));
        AccessTokenService.IssuedAccess access = accessTokens.issue(user);
        return new TokenPair(access.token(), refreshTokens.issue(user.id()), access.expiresInSeconds());
    }

    public TokenPair refresh(String refreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokens.rotate(refreshToken);
        AppUser user = users.findById(rotated.userId())
                .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED, "refresh token rejected"));
        AccessTokenService.IssuedAccess access = accessTokens.issue(user);
        return new TokenPair(access.token(), rotated.refreshToken(), access.expiresInSeconds());
    }

    public void logout(String jti, Instant accessExpiresAt) {
        if (jti != null) {
            denylist.revoke(jti, accessExpiresAt);
        }
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
    }
}
