package com.apexwallet.app.identity;

import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static ApexPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new DomainException(ErrorCode.UNAUTHORIZED, "authentication required");
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            throw new DomainException(ErrorCode.UNAUTHORIZED, "token has no role");
        }
        return new ApexPrincipal(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("username"),
                Role.valueOf(roles.get(0)));
    }
}
