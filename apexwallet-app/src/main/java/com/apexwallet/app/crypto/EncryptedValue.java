package com.apexwallet.app.crypto;

public record EncryptedValue(String ciphertext, String nonce, String keyId) {
}
