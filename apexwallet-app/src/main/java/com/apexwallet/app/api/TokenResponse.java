package com.apexwallet.app.api;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
}
