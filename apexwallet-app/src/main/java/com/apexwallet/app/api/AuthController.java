package com.apexwallet.app.api;

import com.apexwallet.app.identity.AuthService;
import com.apexwallet.app.identity.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a customer. Requested roles other than CUSTOMER are ignored.")
    public void register(@Valid @RequestBody RegisterRequest request) {
        auth.register(request.username(), request.password());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.TokenPair pair = auth.login(request.username(), request.password());
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), "Bearer", pair.expiresInSeconds());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        AuthService.TokenPair pair = auth.refresh(request.refreshToken());
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), "Bearer", pair.expiresInSeconds());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            auth.logout(jwt.getId(), jwt.getExpiresAt());
        }
        CurrentUser.require();
    }
}
